package org.cgutman.usbip.server.protocol.dev

import org.cgutman.usbip.utils.StreamUtils
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class UsbIpUnlinkUrb(header: ByteArray) : UsbIpDevicePacket(header) {
    @JvmField var seqNumToUnlink: Int = 0

    override fun toString(): String {
        return super.toString() +
                String.format("Sequence number to unlink: %d\n", seqNumToUnlink)
    }

    override protected fun serializeInternal(): ByteArray {
        throw UnsupportedOperationException("Serializing not supported")
    }

    companion object {
        const val WIRE_SIZE = 4

        @JvmStatic
        @Throws(IOException::class)
        fun read(header: ByteArray, `in`: InputStream): UsbIpUnlinkUrb {
            val msg = UsbIpUnlinkUrb(header)

            val continuationHeader = ByteArray(WIRE_SIZE)
            StreamUtils.readAll(`in`, continuationHeader)

            val bb = ByteBuffer.wrap(continuationHeader).order(ByteOrder.BIG_ENDIAN)
            msg.seqNumToUnlink = bb.int

            // Finish reading the remaining bytes of the header as padding
            for (i in 0 until UsbIpDevicePacket.USBIP_HEADER_SIZE - (header.size + bb.position())) {
                `in`.read()
            }

            return msg
        }
    }
}
