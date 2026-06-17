package org.cgutman.usbip.usb

import android.hardware.usb.UsbConfiguration
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.util.SparseArray
import org.cgutman.usbip.service.AttachedDeviceContext

object UsbControlHelper {

    private const val GET_DESCRIPTOR_REQUEST_TYPE = 0x80
    private const val GET_DESCRIPTOR_REQUEST = 0x06

    private const val GET_STATUS_REQUEST_TYPE = 0x82
    private const val GET_STATUS_REQUEST = 0x00

    private const val CLEAR_FEATURE_REQUEST_TYPE = 0x02
    private const val CLEAR_FEATURE_REQUEST = 0x01

    private const val SET_CONFIGURATION_REQUEST_TYPE = 0x00
    private const val SET_CONFIGURATION_REQUEST = 0x9

    private const val SET_INTERFACE_REQUEST_TYPE = 0x01
    private const val SET_INTERFACE_REQUEST = 0xB

    private const val FEATURE_VALUE_HALT = 0x00

    private const val DEVICE_DESCRIPTOR_TYPE = 1

    @JvmStatic
    fun readDeviceDescriptor(devConn: UsbDeviceConnection): UsbDeviceDescriptor? {
        val descriptorBuffer = ByteArray(UsbDeviceDescriptor.DESCRIPTOR_SIZE)

        val res = XferUtils.doControlTransfer(
            devConn, GET_DESCRIPTOR_REQUEST_TYPE,
            GET_DESCRIPTOR_REQUEST,
            (DEVICE_DESCRIPTOR_TYPE shl 8) or 0x00, // Devices only have 1 descriptor
            0, descriptorBuffer, descriptorBuffer.size, 0
        )
        if (res != UsbDeviceDescriptor.DESCRIPTOR_SIZE) {
            return null
        }

        return UsbDeviceDescriptor(descriptorBuffer)
    }

    @JvmStatic
    fun isEndpointHalted(devConn: UsbDeviceConnection, endpoint: UsbEndpoint?): Boolean {
        val statusBuffer = ByteArray(2)

        val res = XferUtils.doControlTransfer(
            devConn, GET_STATUS_REQUEST_TYPE,
            GET_STATUS_REQUEST,
            0,
            endpoint?.address ?: 0,
            statusBuffer, statusBuffer.size, 0
        )
        if (res != statusBuffer.size) {
            return false
        }

        return (statusBuffer[0].toInt() and 1) != 0
    }

    @JvmStatic
    fun clearHaltCondition(devConn: UsbDeviceConnection, endpoint: UsbEndpoint): Boolean {
        val res = XferUtils.doControlTransfer(
            devConn, CLEAR_FEATURE_REQUEST_TYPE,
            CLEAR_FEATURE_REQUEST,
            FEATURE_VALUE_HALT,
            endpoint.address,
            null, 0, 0
        )
        return res >= 0
    }

    @JvmStatic
    fun handleInternalControlTransfer(
        deviceContext: AttachedDeviceContext,
        requestType: Int,
        request: Int,
        value: Int,
        index: Int
    ): Boolean {
        // Mask out possible sign expansions
        val rt = requestType and 0xFF
        val rq = request and 0xFF
        val v = value and 0xFFFF
        val idx = index and 0xFFFF

        if (rt == SET_CONFIGURATION_REQUEST_TYPE && rq == SET_CONFIGURATION_REQUEST) {
            println("Handling SET_CONFIGURATION via Android API")

            val dev = deviceContext.device ?: return false
            val conn = deviceContext.devConn ?: return false

            for (i in 0 until dev.configurationCount) {
                val config = dev.getConfiguration(i)
                if (config.id == v) {
                    // If we have a current config, we need unclaim all interfaces to allow the
                    // configuration change to work properly.
                    if (deviceContext.activeConfiguration != null) {
                        val activeConfig = deviceContext.activeConfiguration!!
                        println("Unclaiming all interfaces from old configuration: ${activeConfig.id}")
                        for (j in 0 until activeConfig.interfaceCount) {
                            val iface = activeConfig.getInterface(j)
                            conn.releaseInterface(iface)
                        }
                    }

                    if (!conn.setConfiguration(config)) {
                        // This can happen for certain types of devices where Android itself
                        // has set the configuration for us. Let's just hope that whatever the
                        // client wanted is also what Android selected :/
                        System.err.println("Failed to set configuration! Proceeding anyway!")
                    }

                    // This is now the active configuration
                    deviceContext.activeConfiguration = config

                    // Construct the cache of endpoint mappings
                    deviceContext.activeConfigurationEndpointsByNumDir = SparseArray()
                    for (j in 0 until config.interfaceCount) {
                        val iface = config.getInterface(j)
                        for (k in 0 until iface.endpointCount) {
                            val endp = iface.getEndpoint(k)
                            deviceContext.activeConfigurationEndpointsByNumDir!!.put(
                                endp.direction or endp.endpointNumber,
                                endp
                            )
                        }
                    }

                    println("Claiming all interfaces from new configuration: ${config.id}")
                    for (j in 0 until config.interfaceCount) {
                        val iface = config.getInterface(j)
                        if (!conn.claimInterface(iface, true)) {
                            System.err.println("Unable to claim interface: ${iface.id}")
                        }
                    }

                    return true
                }
            }

            System.err.printf("SET_CONFIGURATION specified invalid configuration: %d\n", v)
        } else if (rt == SET_INTERFACE_REQUEST_TYPE && rq == SET_INTERFACE_REQUEST) {
            println("Handling SET_INTERFACE via Android API")

            if (deviceContext.activeConfiguration != null) {
                val activeConfig = deviceContext.activeConfiguration!!
                val conn = deviceContext.devConn ?: return false

                for (i in 0 until activeConfig.interfaceCount) {
                    val iface = activeConfig.getInterface(i)
                    if (iface.id == idx && iface.alternateSetting == v) {
                        if (!conn.setInterface(iface)) {
                            System.err.println("Unable to set interface: ${iface.id}")
                        }
                        return true
                    }
                }

                System.err.printf("SET_INTERFACE specified invalid interface: %d %d\n", idx, v)
            } else {
                System.err.println("Attempted to use SET_INTERFACE before SET_CONFIGURATION!")
            }
        }

        return false
    }
}
