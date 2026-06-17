package org.cgutman.usbip.service

import android.hardware.usb.UsbConfiguration
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.util.SparseArray
import org.cgutman.usbip.server.protocol.dev.UsbIpSubmitUrb
import java.util.HashSet
import java.util.concurrent.ThreadPoolExecutor

class AttachedDeviceContext {
    var device: UsbDevice? = null
    var devConn: UsbDeviceConnection? = null
    var activeConfiguration: UsbConfiguration? = null
    var activeConfigurationEndpointsByNumDir: SparseArray<UsbEndpoint>? = null
    var requestPool: ThreadPoolExecutor? = null
    var activeMessages: HashSet<UsbIpSubmitUrb>? = null
}
