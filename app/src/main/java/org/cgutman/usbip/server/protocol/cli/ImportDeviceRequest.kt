package org.cgutman.usbip.server.protocol.cli

import java.io.InputStream
import org.cgutman.usbip.server.protocol.UsbIpDevice
import org.cgutman.usbip.utils.StreamUtils

class ImportDeviceRequest(header: ByteArray) : CommonPacket(header) {
    var busid: String? = null

    @Throws(java.io.IOException::class)
    fun populateInternal(`in`: InputStream) {
        val bb = ByteArray(UsbIpDevice.BUS_ID_SIZE)
        StreamUtils.readAll(`in`, bb)

        val sb = StringBuilder()
        for (b in bb) {
            if (b == 0.toByte()) break
            sb.append(b.toInt().toChar())
        }

        busid = sb.toString()
    }

    override fun serializeInternal(): ByteArray {
        throw UnsupportedOperationException("Serialization not supported")
    }
}
