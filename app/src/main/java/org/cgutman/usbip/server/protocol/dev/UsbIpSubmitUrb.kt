package org.cgutman.usbip.server.protocol.dev

import org.cgutman.usbip.utils.StreamUtils
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class UsbIpSubmitUrb(header: ByteArray) : UsbIpDevicePacket(header) {
    @JvmField var transferFlags: Int = 0
    @JvmField var transferBufferLength: Int = 0
    @JvmField var startFrame: Int = 0
    @JvmField var numberOfPackets: Int = 0
    @JvmField var interval: Int = 0
    @JvmField var setup: ByteArray? = null

    @JvmField var outData: ByteArray? = null

    override fun toString(): String {
        return super.toString() +
                String.format("Xfer flags: 0x%x\n", transferFlags) +
                String.format("Xfer length: %d\n", transferBufferLength) +
                String.format("Start frame: %d\n", startFrame) +
                String.format("Number Of Packets: %d\n", numberOfPackets) +
                String.format("Interval: %d\n", interval)
    }

    override protected fun serializeInternal(): ByteArray {
        throw UnsupportedOperationException("Serializing not supported")
    }

    companion object {
        const val WIRE_SIZE = 20 + 8

        @JvmStatic
        @Throws(IOException::class)
        fun read(header: ByteArray, `in`: InputStream): UsbIpSubmitUrb {
            val msg = UsbIpSubmitUrb(header)

            val continuationHeader = ByteArray(WIRE_SIZE)
            StreamUtils.readAll(`in`, continuationHeader)

            val bb = ByteBuffer.wrap(continuationHeader).order(ByteOrder.BIG_ENDIAN)
            msg.transferFlags = bb.int
            msg.transferBufferLength = bb.int
            msg.startFrame = bb.int
            msg.numberOfPackets = bb.int
            msg.interval = bb.int

            msg.setup = ByteArray(8)
            bb[msg.setup!!]

            // Finish reading the remaining bytes of the header as padding
            while (bb.position() < UsbIpDevicePacket.USBIP_HEADER_SIZE - header.size) {
                `in`.read()
                bb.position(bb.position() + 1)
            }

            if (msg.direction == UsbIpDevicePacket.USBIP_DIR_OUT) {
                msg.outData = ByteArray(msg.transferBufferLength)
                StreamUtils.readAll(`in`, msg.outData!!)
            }

            return msg
        }
    }
}
