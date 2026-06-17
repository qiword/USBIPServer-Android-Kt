package org.cgutman.usbip.server.protocol.cli

import org.cgutman.usbip.server.UsbDeviceInfo
import org.cgutman.usbip.server.protocol.ProtoDefs

class ImportDeviceReply : CommonPacket {
    var devInfo: UsbDeviceInfo? = null

    constructor(header: ByteArray) : super(header)

    constructor(version: Short) : super(version, ProtoDefs.OP_REP_IMPORT, ProtoDefs.ST_OK.toInt())

    override fun serializeInternal(): ByteArray? {
        return devInfo?.dev?.serialize()
    }
}
