/*
 * Copyright (c) 2023, Kaooot
 */
package org.jukeboxmc.extractor.util

import com.google.gson.annotations.SerializedName
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.*
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.*
import org.cloudburstmc.protocol.bedrock.packet.CraftingDataPacket
import org.jukeboxmc.extractor.DataExtractor
import java.io.File
import java.util.*

/**
 * @author Kaooot
 * @version 1.0
 */
class RecipeUtil {

    companion object {
        private val shapeChars = arrayOf('A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J')

        fun writeRecipes(packet: CraftingDataPacket, dataExtractor: DataExtractor) {
            val craftingData = ArrayList<CraftingDataEntry>()
            val potionMixes = ArrayList<PotionMixDataEntry>()
            val containerMixes = ArrayList<ContainerMixDataEntry>()

            for (recipe in packet.craftingData) {
                val type = recipe.type.ordinal

                var block: String? = null
                var uuid: UUID? = null

                if (recipe is TaggedCraftingData) {
                    block = recipe.tag
                } else if (recipe is UniqueCraftingData) {
                    uuid = recipe.uuid
                }

                var id: String? = null
                var priority: Int? = null
                var output: Any? = null

                if (recipe is CraftingRecipeData) {
                    id = recipe.id
                    priority = recipe.priority
                    output = this.writeRecipeItems(recipe.results.toTypedArray())
                }

                var entryShape: Array<String>? = null
                var input: Any? = null

                if (recipe is ShapedRecipeData) {
                    var charCounter = 0

                    val inputs = recipe.ingredients
                    val charItemMap = HashMap<RecipeItemDescriptor, Char>()
                    val shape: Array<CharArray> = Array(recipe.height) { CharArray(recipe.width) }

                    for (height in 0..<recipe.height) {
                        Arrays.fill(shape[height], ' ')

                        val index = height * recipe.width

                        for (width in 0..<recipe.width) {
                            val slot = index + width
                            val descriptor = this.fromNetwork(inputs[slot], dataExtractor)

                            if (descriptor.type == ItemDescriptorType.INVALID.name.lowercase()) {
                                continue
                            }

                            var shapeChar = charItemMap[descriptor]

                            if (shapeChar == null) {
                                shapeChar = this.shapeChars[charCounter++]

                                charItemMap[descriptor] = shapeChar
                            }

                            shape[height][width] = shapeChar
                        }
                    }

                    val shapes = ArrayList<String>()

                    for (i in shape.indices) {
                        shapes.add(String(shape[i]))
                    }

                    entryShape = shapes.toTypedArray()

                    val charItemMapReversed = HashMap<Char, RecipeItemDescriptor>()

                    charItemMap.entries.forEach { charItemMapReversed[it.value] = it.key }

                    input = charItemMapReversed
                }

                if (recipe is ShapelessRecipeData) {
                    input = this.writeRecipeItemDescriptors(recipe.ingredients, dataExtractor)
                }

                if (recipe is FurnaceRecipeData) {
                    var damage: Int? = recipe.inputData

                    if (damage == 0x7fff) {
                        damage = -1
                    } else if (damage == 0) {
                        damage = null
                    }

                    input = RecipeItem(dataExtractor.legacyItemIds()[recipe.inputId]!!, null, damage, null)
                    output = this.itemFromNetwork(recipe.result)
                }

                craftingData.add(CraftingDataEntry(id, type, input, output, entryShape, block, uuid, priority))
            }

            for (potionMix in packet.potionMixData) {
                potionMixes.add(
                    PotionMixDataEntry(
                        dataExtractor.legacyItemIds()[potionMix.inputId]!!,
                        potionMix.inputMeta,
                        dataExtractor.legacyItemIds()[potionMix.reagentId]!!,
                        potionMix.reagentMeta,
                        dataExtractor.legacyItemIds()[potionMix.outputId]!!,
                        potionMix.outputMeta
                    )
                )
            }

            for (containerMix in packet.containerMixData) {
                containerMixes.add(ContainerMixDataEntry(dataExtractor.legacyItemIds()[containerMix.inputId]!!, dataExtractor.legacyItemIds()[containerMix.reagentId]!!, dataExtractor.legacyItemIds()[containerMix.outputId]!!))
            }

            ExtractionUtil.writeJson(
                dataExtractor.gson().toJson(Recipes(dataExtractor.codec().protocolVersion, craftingData, potionMixes, containerMixes)),
                File("src/main/resources/extracted/recipes/recipes.${dataExtractor.codec().minecraftVersion.replace(".", "_")}.json")
            )
        }

        private fun writeRecipeItems(inputs: Array<ItemData>): List<RecipeItem> {
            val outputs = ArrayList<RecipeItem>()

            for (input in inputs) {
                val item = this.itemFromNetwork(input)

                if (item.id != "minecraft:air") {
                    outputs.add(item)
                }
            }

            return outputs
        }

        private fun writeRecipeItemDescriptors(inputs: List<ItemDescriptorWithCount>, dataExtractor: DataExtractor): List<RecipeItemDescriptor> {
            val outputs = ArrayList<RecipeItemDescriptor>()

            for (input in inputs) {
                val descriptor = this.fromNetwork(input, dataExtractor)

                if (descriptor.type != ItemDescriptorType.INVALID.name.lowercase()) {
                    outputs.add(descriptor)
                }
            }

            return outputs
        }

        private fun itemFromNetwork(itemData: ItemData): RecipeItem {
            val id = itemData.definition.identifier
            val count = itemData.count
            var damage: Int? = itemData.damage
            var tag: String? = null

            if (itemData.tag != null) {
                tag = ExtractionUtil.nbtToBase64(itemData.tag!!)
            }

            if (damage == 0 || damage == -1) {
                damage = null
            }

            return RecipeItem(id, count, damage, tag)
        }

        private fun fromNetwork(descriptorWithCount: ItemDescriptorWithCount, dataExtractor: DataExtractor): RecipeItemDescriptor {
            val itemDescriptor = descriptorWithCount.descriptor

            var name: String? = null
            var itemId: Int? = null
            var auxValue: Int? = null
            var fullName: String? = null
            var itemTag: String? = null
            var tagExpression: String? = null
            var molangVersion: Int? = null

            when (itemDescriptor) {
                is ComplexAliasDescriptor -> {
                    name = itemDescriptor.name
                }

                is DefaultDescriptor -> {
                    itemId = itemDescriptor.itemId.runtimeId
                    auxValue = itemDescriptor.auxValue
                }

                is DeferredDescriptor -> {
                    fullName = itemDescriptor.fullName
                }

                is ItemTagDescriptor -> {
                    itemTag = itemDescriptor.itemTag
                }

                is MolangDescriptor -> {
                    tagExpression = itemDescriptor.tagExpression
                    molangVersion = itemDescriptor.molangVersion
                }
            }

            return RecipeItemDescriptor(descriptorWithCount.descriptor.type.name.lowercase(), descriptorWithCount.count, name, dataExtractor.legacyItemIds()[itemId], auxValue, fullName, itemTag, tagExpression, molangVersion)
        }
    }

    data class Recipes(val version: Int, val recipes: List<CraftingDataEntry>, val potionMixes: List<PotionMixDataEntry>, val containerMixes: List<ContainerMixDataEntry>)

    data class CraftingDataEntry(val id: String?, val type: Int, val input: Any?, val output: Any?, val shape: Array<String>?, val block: String?, val uuid: UUID?, val priority: Int?)

    data class PotionMixDataEntry(val inputId: String, val inputMeta: Int, val reagentId: String, val reagentMeta: Int, val outputId: String, val outputMeta: Int)

    data class ContainerMixDataEntry(val inputId: String, val reagentId: String, val outputId: String)

    data class RecipeItem(val id: String, val count: Int?, val damage: Int?, @SerializedName("nbt_b64") val nbtBase64: String?)

    data class RecipeItemDescriptor(val type: String, val count: Int, val name: String?, val itemId: String?, val auxValue: Int?, val fullName: String?, val itemTag: String?, val tagExpression: String?, val molangVersion: Int?)
}