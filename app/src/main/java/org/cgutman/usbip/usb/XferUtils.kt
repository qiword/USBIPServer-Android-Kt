package org.cgutman.usbip.usb

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import org.cgutman.usbip.jni.UsbLib

object XferUtils {

    @JvmStatic
    fun doInterruptTransfer(
        devConn: UsbDeviceConnection,
        endpoint: UsbEndpoint,
        buff: ByteArray,
        timeout: Int
    ): Int {
        val res = UsbLib.doBulkTransfer(
            devConn.fileDescriptor,
            endpoint.address,
            buff,
            timeout
        )
        if (res < 0 && res != -110) {
            System.err.println("Interrupt Xfer failed: $res")
        }
        return res
    }

    @JvmStatic
    fun doBulkTransfer(
        devConn: UsbDeviceConnection,
        endpoint: UsbEndpoint,
        buff: ByteArray,
        timeout: Int
    ): Int {
        val res = UsbLib.doBulkTransfer(
            devConn.fileDescriptor,
            endpoint.address,
            buff,
            timeout
        )
        if (res < 0 && res != -110) {
            System.err.println("Bulk Xfer failed: $res")
        }
        return res
    }

    @JvmStatic
    fun doControlTransfer(
        devConn: UsbDeviceConnection,
        requestType: Int,
        request: Int,
        value: Int,
        index: Int,
        buff: ByteArray?,
        length: Int,
        interval: Int
    ): Int {
        val res = devConn.controlTransfer(
            requestType, request, value, index,
            buff, length, interval
        )
        if (res < 0 && res != -110) {
            System.err.println("Control Xfer failed: $res")
        }
        return res
    }
}
