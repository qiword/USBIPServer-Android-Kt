package org.cgutman.usbip.service

import android.os.Binder

class UsbIpServiceBinder(val service: UsbIpService) : Binder() {
    /** Activity calls this to request sharing a device */
    fun shareDevice(deviceId: Int): Boolean {
        return service.doShareDevice(deviceId)
    }

    /** Activity calls this to stop sharing a device */
    fun unshareDevice(deviceId: Int) {
        service.doUnshareDevice(deviceId)
    }

    /** Activity calls this to get the set of currently shared device IDs */
    fun getSharedDeviceIds(): Set<Int> {
        return service.getSharedDeviceIds()
    }
}
