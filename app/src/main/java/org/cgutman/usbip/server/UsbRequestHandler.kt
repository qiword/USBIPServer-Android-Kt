package org.cgutman.usbip.server

import java.net.Socket
import org.cgutman.usbip.server.protocol.dev.UsbIpSubmitUrb
import org.cgutman.usbip.server.protocol.dev.UsbIpUnlinkUrb

interface UsbRequestHandler {
    fun getDevices(): List<UsbDeviceInfo>
    fun getDeviceByBusId(busId: String): UsbDeviceInfo?

    fun attachToDevice(s: Socket, busId: String): Boolean
    fun detachFromDevice(s: Socket, busId: String)

    fun submitUrbRequest(s: Socket, msg: UsbIpSubmitUrb)
    fun abortUrbRequest(s: Socket, msg: UsbIpUnlinkUrb)

    fun cleanupSocket(s: Socket)
}
