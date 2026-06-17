package org.cgutman.usbip.server.protocol.dev

import java.nio.ByteBuffer
import java.nio.ByteOrder

class UsbIpUnlinkUrbReply(
    seqNum: Int, devId: Int, dir: Int, ep: Int
) : UsbIpDevicePacket(UsbIpDevicePacket.USBIP_RET_UNLINK, seqNum, devId, dir, ep) {
    @JvmField var status: Int = 0

    override protected fun serializeInternal(): ByteArray {
        val bb = ByteBuffer.allocate(UsbIpDevicePacket.USBIP_HEADER_SIZE - 20)
            .order(ByteOrder.BIG_ENDIAN)

        bb.putInt(status)

        return bb.array()
    }

    override fun toString(): String {
        return super.toString() +
                String.format("Status: 0x%x\n", status)
    }
}
