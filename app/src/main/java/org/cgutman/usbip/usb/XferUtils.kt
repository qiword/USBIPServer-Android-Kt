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
        // Interrupt transfers are implemented as one-shot bulk transfers
        val res = UsbLib.doBulkTransfer(
            devConn.fileDescriptor,
            endpoint.address,
            buff,
            timeout
        )
        if (res < 0 && res != -110) {
            // Don't print for ETIMEDOUT
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
            // Don't print for ETIMEDOUT
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
        // Mask out possible sign expansions
        val rt = requestType and 0xFF
        val rq = request and 0xFF
        val v = value and 0xFFFF
        val idx = index and 0xFFFF
        val len = length and 0xFFFF

        // NOTE: The native (Rust) implementation uses buff.length as wLength,
        // not the 'length' parameter. Callers must ensure buff.length >= length.

        println(
            java.lang.String.format(
                "SETUP: %x %x %x %x %x",
                rt,
                rq,
                v,
                idx,
                buff?.size ?: 0
            )
        )

        val res = UsbLib.doControlTransfer(
            devConn.fileDescriptor,
            rt,
            rq,
            v,
            idx,
            buff ?: ByteArray(0),
            interval
        )
        if (res < 0 && res != -110) {
            // Don't print for ETIMEDOUT
            System.err.println("Control Xfer failed: $res")
        }

        return res
    }
}
