package org.cgutman.usbip.server.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

class UsbIpInterface {
    var bInterfaceClass: Byte = 0
    var bInterfaceSubClass: Byte = 0
    var bInterfaceProtocol: Byte = 0

    companion object {
        const val WIRE_SIZE = 4
    }

    fun serialize(): ByteArray {
        val bb = ByteBuffer.allocate(WIRE_SIZE).order(ByteOrder.BIG_ENDIAN)

        bb.put(bInterfaceClass)
        bb.put(bInterfaceSubClass)
        bb.put(bInterfaceProtocol)
        bb.put(0.toByte()) // alignment padding

        return bb.array()
    }
}
