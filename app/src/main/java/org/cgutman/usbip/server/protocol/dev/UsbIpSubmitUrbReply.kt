package org.cgutman.usbip.server.protocol.dev

import java.nio.ByteBuffer
import java.nio.ByteOrder

class UsbIpSubmitUrbReply(
    seqNum: Int, devId: Int, dir: Int, ep: Int
) : UsbIpDevicePacket(UsbIpDevicePacket.USBIP_RET_SUBMIT, seqNum, devId, dir, ep) {
    @JvmField var status: Int = 0
    @JvmField var actualLength: Int = 0
    @JvmField var startFrame: Int = 0
    @JvmField var numberOfPackets: Int = 0
    @JvmField var errorCount: Int = 0

    @JvmField var inData: ByteArray? = null

    override protected fun serializeInternal(): ByteArray {
        val inDataLen = if (inData == null) 0 else actualLength
        val bb = ByteBuffer.allocate(
            (UsbIpDevicePacket.USBIP_HEADER_SIZE - 20) + inDataLen
        ).order(ByteOrder.BIG_ENDIAN)

        bb.putInt(status)
        bb.putInt(actualLength)
        bb.putInt(startFrame)
        bb.putInt(numberOfPackets)
        bb.putInt(errorCount)

        bb.position(UsbIpDevicePacket.USBIP_HEADER_SIZE - 20)

        if (inDataLen != 0) {
            bb.put(inData, 0, inDataLen)
        }

        return bb.array()
    }

    override fun toString(): String {
        return super.toString() +
                String.format("Status: 0x%x\n", status) +
                String.format("Actual length: %d\n", actualLength) +
                String.format("Start frame: %d\n", startFrame) +
                String.format("Number Of Packets: %d\n", numberOfPackets) +
                String.format("Error Count: %d\n", errorCount)
    }
}
