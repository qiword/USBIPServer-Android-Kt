package org.cgutman.usbip.server.protocol.dev

import org.cgutman.usbip.utils.StreamUtils
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

abstract class UsbIpDevicePacket {
    @JvmField var command: Int = 0
    @JvmField var seqNum: Int = 0
    @JvmField var devId: Int = 0
    @JvmField var direction: Int = 0
    @JvmField var ep: Int = 0

    constructor(header: ByteArray) {
        val bb = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
        command = bb.int
        seqNum = bb.int
        devId = bb.int
        direction = bb.int
        ep = bb.int
    }

    constructor(command: Int, seqNum: Int, devId: Int, dir: Int, ep: Int) {
        this.command = command
        this.seqNum = seqNum
        this.devId = devId
        this.direction = dir
        this.ep = ep
    }

    protected abstract fun serializeInternal(): ByteArray

    override fun toString(): String {
        return String.format("Command: 0x%x\n", command) +
                String.format("Seq: %d\n", seqNum) +
                String.format("Dev ID: 0x%x\n", devId) +
                String.format("Direction: %d\n", direction) +
                String.format("Endpoint: %d\n", ep)
    }

    fun serialize(): ByteArray {
        val internalData = serializeInternal()

        val bb = ByteBuffer.allocate(20 + internalData.size)

        bb.putInt(command)
        bb.putInt(seqNum)
        bb.putInt(devId)
        bb.putInt(direction)
        bb.putInt(ep)

        bb.put(internalData)

        return bb.array()
    }

    companion object {
        const val USBIP_CMD_SUBMIT = 0x0001
        const val USBIP_CMD_UNLINK = 0x0002
        const val USBIP_RET_SUBMIT = 0x0003
        const val USBIP_RET_UNLINK = 0x0004
        const val USBIP_RESET_DEV = 0xFFFF

        const val USBIP_DIR_OUT = 0
        const val USBIP_DIR_IN = 1

        const val USBIP_STATUS_ENDPOINT_HALTED = -32
        const val USBIP_STATUS_URB_ABORTED = -54
        const val USBIP_STATUS_DATA_OVERRUN = -75
        const val USBIP_STATUS_URB_TIMED_OUT = -110
        const val USBIP_STATUS_SHORT_TRANSFER = -121

        const val USBIP_HEADER_SIZE = 48

        @JvmStatic
        @Throws(IOException::class)
        fun read(`in`: InputStream): UsbIpDevicePacket? {
            val bb = ByteBuffer.allocate(20)

            StreamUtils.readAll(`in`, bb.array())

            val command = bb.int
            return when (command) {
                USBIP_CMD_SUBMIT -> UsbIpSubmitUrb.read(bb.array(), `in`)
                USBIP_CMD_UNLINK -> UsbIpUnlinkUrb.read(bb.array(), `in`)
                else -> {
                    System.err.println("Unknown command: $command")
                    null
                }
            }
        }
    }
}
