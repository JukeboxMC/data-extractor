/*
 * Copyright (c) 2023, Kaooot
 */
package org.jukeboxmc.extractor.session

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.nimbusds.jwt.SignedJWT
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition
import org.cloudburstmc.protocol.bedrock.packet.*
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils
import org.cloudburstmc.protocol.common.PacketSignal
import org.cloudburstmc.protocol.common.SimpleDefinitionRegistry
import org.jukeboxmc.extractor.DataExtractor
import org.jukeboxmc.extractor.util.ExtractionUtil
import org.jukeboxmc.extractor.util.NbtBlockDefinitionRegistry
import org.jukeboxmc.extractor.util.RecipeUtil
import java.io.File
import java.net.URI
import java.util.*

/**
 * @author Kaooot
 * @version 1.0
 */
class DownstreamPacketHandler(private val dataExtractor: DataExtractor) : BedrockPacketHandler {

    override fun handle(packet: NetworkSettingsPacket): PacketSignal {
        this.dataExtractor.serverSession().sendPacketImmediately(packet)

        this.dataExtractor.clientSession().setCompression(packet.compressionAlgorithm)
        this.dataExtractor.serverSession().setCompression(packet.compressionAlgorithm)

        println("The compression was set to ${packet.compressionAlgorithm}")

        return PacketSignal.HANDLED
    }

    override fun handle(packet: ServerToClientHandshakePacket): PacketSignal {
        val jwt: SignedJWT = SignedJWT.parse(packet.jwt)
        val x5u: URI = jwt.header.x509CertURL
        val salt = Base64.getDecoder().decode(jwt.jwtClaimsSet.getStringClaim("salt"))
        val publicKey = EncryptionUtils.parseKey(x5u.toASCIIString())
        val secretKey = EncryptionUtils.getSecretKey(this.dataExtractor.keyPair().private, publicKey, salt)

        this.dataExtractor.clientSession().enableEncryption(secretKey)
        this.dataExtractor.clientSession().sendPacketImmediately(ClientToServerHandshakePacket())

        return PacketSignal.HANDLED
    }

    override fun handle(packet: DisconnectPacket): PacketSignal {
        println("disconnected: ${packet.reason} (${packet.kickMessage})")

        this.dataExtractor.clientSession().disconnect()

        return PacketSignal.UNHANDLED
    }

    override fun handle(packet: CompressedBiomeDefinitionListPacket): PacketSignal {
        ExtractionUtil.writeNBT(packet.definitions, File("src/main/resources/extracted/biome_definitions/biome_definitions.${this.dataExtractor.codec().minecraftVersion.replace(".", "_")}.dat"))

        println("extracted biome definitions")

        return PacketSignal.UNHANDLED
    }

    override fun handle(packet: AvailableEntityIdentifiersPacket): PacketSignal {
        ExtractionUtil.writeNBT(packet.identifiers, File("src/main/resources/extracted/entity_identifiers/entity_identifiers.${this.dataExtractor.codec().minecraftVersion.replace(".", "_")}.dat"))

        println("extracted entity identifiers")

        return PacketSignal.UNHANDLED
    }

    override fun handle(packet: StartGamePacket): PacketSignal {
        this.dataExtractor.clientSession().peer.codecHelper.itemDefinitions = SimpleDefinitionRegistry.builder<ItemDefinition>()
            .addAll(packet.itemDefinitions)
            .build()

        this.dataExtractor.clientSession().peer.codecHelper.blockDefinitions = if (packet.isBlockNetworkIdsHashed) this.dataExtractor.blockDefinitionHashedRegistry() else this.dataExtractor.blockDefinitionRegistry()

        println("blockNetworkIdsHashed: ${packet.isBlockNetworkIdsHashed}")

        val itemIds = HashMap<Int, String>()

        for (itemDefinition in packet.itemDefinitions) {
            itemIds[itemDefinition.runtimeId] = itemDefinition.identifier

            this.dataExtractor.legacyItemIds()[itemDefinition.runtimeId] = itemDefinition.identifier
        }

        val items = ArrayList<JsonObject>()
        val identifiers = ArrayList(itemIds.values)

        identifiers.sort()

        for (identifier in identifiers) {
            val o = JsonObject()
            o.addProperty("name", identifier)
            o.addProperty("id", itemIds.entries.find { entry -> entry.value.equals(identifier, true) }?.key ?: throw RuntimeException("Could not find $identifier in itemIds"))

            items.add(o)
        }

        ExtractionUtil.writeJson(this.dataExtractor.gson().toJson(items), File("src/main/resources/extracted/item_palette/item_palette.${this.dataExtractor.codec().minecraftVersion.replace(".", "_")}.json"))

        println("extracted item palette")

        return PacketSignal.UNHANDLED
    }

    override fun handle(packet: CreativeContentPacket): PacketSignal {
        val creativeItems = LinkedList<CreativeItem>()

        for (content in packet.contents) {
            var nbtBase64: String? = null
            var blockStateBase64: String? = null

            if (content.tag != null) {
                nbtBase64 = ExtractionUtil.nbtToBase64(content.tag!!)
            }

            if (content.blockDefinition != null && content.blockDefinition is NbtBlockDefinitionRegistry.NbtBlockDefinition) {
                blockStateBase64 = ExtractionUtil.nbtToBase64((content.blockDefinition as NbtBlockDefinitionRegistry.NbtBlockDefinition).nbtTag)
            }

            creativeItems.add(CreativeItem(content.definition.identifier, nbtBase64, blockStateBase64))
        }

        ExtractionUtil.writeJson(this.dataExtractor.gson().toJson(CreativeContents(creativeItems)), File("src/main/resources/extracted/creative_items/creative_items.${this.dataExtractor.codec().minecraftVersion.replace(".", "_")}.json"))

        println("extracted creative items")

        return PacketSignal.UNHANDLED
    }

    override fun handle(packet: CraftingDataPacket): PacketSignal {
        RecipeUtil.writeRecipes(packet, this.dataExtractor)

        println("extracted recipes")

        return PacketSignal.UNHANDLED
    }

    data class CreativeContents(val items: List<CreativeItem>)

    data class CreativeItem(val id: String, @SerializedName("nbt_b64") val nbtBase64: String?, @SerializedName("block_state_b64") val blockStateBase64: String?)
}