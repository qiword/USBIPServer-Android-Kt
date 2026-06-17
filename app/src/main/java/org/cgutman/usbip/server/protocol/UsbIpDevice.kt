package org.cgutman.usbip.server.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

class UsbIpDevice {
    var path: String? = null
    var busid: String? = null
    var busnum: Int = 0
    var devnum: Int = 0
    var speed: Int = 0

    var idVendor: Short = 0
    var idProduct: Short = 0
    var bcdDevice: Short = 0

    var bDeviceClass: Byte = 0
    var bDeviceSubClass: Byte = 0
    var bDeviceProtocol: Byte = 0
    var bConfigurationValue: Byte = 0
    var bNumConfigurations: Byte = 0
    var bNumInterfaces: Byte = 0

    companion object {
        const val USB_SPEED_UNKNOWN = 0
        const val USB_SPEED_LOW = 1
        const val USB_SPEED_FULL = 2
        const val USB_SPEED_HIGH = 3
        const val USB_SPEED_VARIABLE = 4
        const val USB_SPEED_SUPER = 5

        const val BUS_ID_SIZE = 32
        const val DEV_PATH_SIZE = 256

        const val WIRE_LENGTH = BUS_ID_SIZE + DEV_PATH_SIZE + 24

        private fun putChars(bb: ByteBuffer, str: String?, size: Int) {
            val chars = str?.toCharArray() ?: CharArray(0)
            for (i in 0 until size) {
                bb.put(if (i < chars.size) chars[i].code.toByte() else 0)
            }
        }
    }

    fun serialize(): ByteArray {
        val bb = ByteBuffer.allocate(WIRE_LENGTH).order(ByteOrder.BIG_ENDIAN)

        putChars(bb, path, DEV_PATH_SIZE)
        putChars(bb, busid, BUS_ID_SIZE)
        bb.putInt(busnum)
        bb.putInt(devnum)
        bb.putInt(speed)

        bb.putShort(idVendor)
        bb.putShort(idProduct)
        bb.putShort(bcdDevice)

        bb.put(bDeviceClass)
        bb.put(bDeviceSubClass)
        bb.put(bDeviceProtocol)
        bb.put(bConfigurationValue)
        bb.put(bNumConfigurations)
        bb.put(bNumInterfaces)

        return bb.array()
    }
}
