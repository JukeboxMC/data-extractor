/*
 * Copyright (c) 2023, Kaooot
 */
package org.jukeboxmc.extractor.util

import kotlin.experimental.and

/**
 * @author Kaooot
 * @version 1.0
 */
class BlockPaletteHashUtil {

    companion object {
        private val fnv1_32_init: Long = 0x811c9dc5
        private val fnv1_32_prime: Long = 0x01000193

        /**
         * Creates a hashed runtime identifier from the raw nbt byte data. Hashed block runtime ids are persistent
         * across versions which should make support for custom blocks easier.
         *
         * @return a hashed block runtime id
         */
        fun fnv1a_32(data: ByteArray): Int {
            var hash: Long = this.fnv1_32_init

            for (datum in data) {
                hash = hash.xor(datum.and(0xff.toByte()).toLong())
                hash *= this.fnv1_32_prime
            }

            return hash.toInt()
        }
    }
}