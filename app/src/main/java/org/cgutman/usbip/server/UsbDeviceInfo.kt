package org.cgutman.usbip.server

import java.nio.ByteBuffer
import org.cgutman.usbip.server.protocol.UsbIpDevice
import org.cgutman.usbip.server.protocol.UsbIpInterface

class UsbDeviceInfo {
    var dev: UsbIpDevice? = null
    var interfaces: Array<UsbIpInterface>? = null

    // --- Read-only accessors for JNI callbacks from Rust ---

    @JvmName("getBusId")
    fun getBusId(): String? = dev?.busid

    @JvmName("getPath")
    fun getPath(): String? = dev?.path

    @JvmName("getBusNum")
    fun getBusNum(): Int = dev?.busnum ?: 0

    @JvmName("getDevNum")
    fun getDevNum(): Int = dev?.devnum ?: 0

    @JvmName("getSpeed")
    fun getSpeed(): Int = dev?.speed ?: 0

    @JvmName("getIdVendor")
    fun getIdVendor(): Short = dev?.idVendor ?: 0

    @JvmName("getIdProduct")
    fun getIdProduct(): Short = dev?.idProduct ?: 0

    @JvmName("getBcdDevice")
    fun getBcdDevice(): Short = dev?.bcdDevice ?: 0

    @JvmName("getBDeviceClass")
    fun getBDeviceClass(): Byte = dev?.bDeviceClass ?: 0

    @JvmName("getBDeviceSubClass")
    fun getBDeviceSubClass(): Byte = dev?.bDeviceSubClass ?: 0

    @JvmName("getBDeviceProtocol")
    fun getBDeviceProtocol(): Byte = dev?.bDeviceProtocol ?: 0

    @JvmName("getBNumInterfaces")
    fun getBNumInterfaces(): Byte = dev?.bNumInterfaces ?: 0

    @JvmName("getWireSize")
    fun getWireSize(): Int {
        val ifaceCount = dev?.bNumInterfaces?.toInt() ?: 0
        return (UsbIpDevice.WIRE_LENGTH + (UsbIpInterface.WIRE_SIZE * ifaceCount))
    }

    @JvmName("serialize")
    fun serialize(): ByteArray {
        val safeDev = dev ?: return ByteArray(0)
        val safeIfaces = interfaces ?: return ByteArray(0)
        val devSerialized = safeDev.serialize()
        val bb = ByteBuffer.allocate(getWireSize())
        bb.put(devSerialized)
        for (iface in safeIfaces) {
            bb.put(iface.serialize())
        }
        return bb.array()
    }
}
