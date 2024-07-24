/*
 * Copyright (c) 2024, Kaooot
 */
package org.jukeboxmc.extractor.session

import org.cloudburstmc.protocol.bedrock.BedrockPeer
import org.cloudburstmc.protocol.bedrock.BedrockServerSession
import org.cloudburstmc.protocol.bedrock.netty.BedrockPacketWrapper
import org.cloudburstmc.protocol.bedrock.packet.UnknownPacket
import org.cloudburstmc.protocol.common.PacketSignal
import org.jukeboxmc.extractor.DataExtractor

/**
 * @author Kaooot
 * @version 1.0
 */
class ProxiedBedrockServerSession(private val dataExtractor: DataExtractor, peer: BedrockPeer?, subClientId: Int) : BedrockServerSession(peer, subClientId) {

    override fun onPacket(wrapper: BedrockPacketWrapper?) {
        val packet = wrapper?.packet

        if (this.packetHandler == null) {
            println("Received packet without a packet handler for ${this.socketAddress}:${this.subClientId}: " + packet)
        } else if (this.packetHandler.handlePacket(packet).equals(PacketSignal.UNHANDLED)) {
            val buffer = wrapper!!.packetBuffer.retainedSlice().skipBytes(wrapper.headerLength)
            val pk = UnknownPacket()
            pk.payload = buffer
            pk.packetId = wrapper.packetId
            this.dataExtractor.clientSession().sendPacket(pk)
        }
    }
}