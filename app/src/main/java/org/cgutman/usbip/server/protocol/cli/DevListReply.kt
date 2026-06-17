package org.cgutman.usbip.server.protocol.cli

import java.nio.ByteBuffer
import org.cgutman.usbip.server.UsbDeviceInfo
import org.cgutman.usbip.server.protocol.ProtoDefs

class DevListReply : CommonPacket {
    var devInfoList: List<UsbDeviceInfo>? = null

    constructor(header: ByteArray) : super(header)

    constructor(version: Short) : super(version, ProtoDefs.OP_REP_DEVLIST, ProtoDefs.ST_OK.toInt())

    override fun serializeInternal(): ByteArray {
        val bb = ByteBuffer.allocate(
            if (devInfoList != null) {
                4 + devInfoList!!.sumOf { it.getWireSize() }
            } else {
                4
            }
        )

        if (devInfoList != null) {
            bb.putInt(devInfoList!!.size)
            for (info in devInfoList!!) {
                bb.put(info.serialize())
            }
        } else {
            bb.putInt(0)
        }

        return bb.array()
    }
}
