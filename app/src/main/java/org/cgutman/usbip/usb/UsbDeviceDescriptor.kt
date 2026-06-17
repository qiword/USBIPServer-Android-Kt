package org.cgutman.usbip.usb

import java.nio.ByteBuffer
import java.nio.ByteOrder

class UsbDeviceDescriptor(data: ByteArray) {
    var bLength: Byte = 0
    var bDescriptorType: Byte = 0
    var bcdUSB: Short = 0
    var bDeviceClass: Byte = 0
    var bDeviceSubClass: Byte = 0
    var bDeviceProtocol: Byte = 0
    var bMaxPacketSize: Byte = 0
    var idVendor: Short = 0
    var idProduct: Short = 0
    var bcdDevice: Short = 0
    var iManufacturer: Byte = 0
    var iProduct: Byte = 0
    var iSerialNumber: Byte = 0
    var bNumConfigurations: Byte = 0

    companion object {
        const val DESCRIPTOR_SIZE = 18
    }

    init {
        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        bLength = bb.get()
        bDescriptorType = bb.get()
        bcdUSB = bb.short
        bDeviceClass = bb.get()
        bDeviceSubClass = bb.get()
        bDeviceProtocol = bb.get()
        bMaxPacketSize = bb.get()
        idVendor = bb.short
        idProduct = bb.short
        bcdDevice = bb.short
        iManufacturer = bb.get()
        iProduct = bb.get()
        iSerialNumber = bb.get()
        bNumConfigurations = bb.get()
    }
}
