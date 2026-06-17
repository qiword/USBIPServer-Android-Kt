package org.cgutman.usbip.server.protocol.cli

class DevListRequest(header: ByteArray) : CommonPacket(header) {

    override fun serializeInternal(): ByteArray {
        throw UnsupportedOperationException("Serialization not supported")
    }
}
