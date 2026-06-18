package org.cgutman.usbip.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.util.SparseArray
import androidx.core.app.NotificationCompat
import org.cgutman.usbip.config.UsbIpConfig
import org.cgutman.usbip.server.UsbDeviceInfo
import org.cgutman.usbip.server.UsbIpServer
import org.cgutman.usbip.server.UsbRequestHandler
import org.cgutman.usbip.server.protocol.ProtoDefs
import org.cgutman.usbip.server.protocol.UsbIpDevice
import org.cgutman.usbip.server.protocol.UsbIpInterface
import org.cgutman.usbip.server.protocol.dev.UsbIpDevicePacket
import org.cgutman.usbip.server.protocol.dev.UsbIpSubmitUrb
import org.cgutman.usbip.server.protocol.dev.UsbIpSubmitUrbReply
import org.cgutman.usbip.server.protocol.dev.UsbIpUnlinkUrb
import org.cgutman.usbip.server.protocol.dev.UsbIpUnlinkUrbReply
import org.cgutman.usbip.usb.UsbControlHelper
import org.cgutman.usbip.usb.UsbDeviceDescriptor
import org.cgutman.usbip.usb.XferUtils
import org.cgutman.usbipserverforandroid.R
import java.io.IOException
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class UsbIpService : Service(), UsbRequestHandler {

    private lateinit var usbManager: UsbManager

    private lateinit var connections: SparseArray<AttachedDeviceContext>
    private lateinit var permission: SparseArray<Boolean?>
    private lateinit var socketMap: HashMap<Socket, AttachedDeviceContext>
    private lateinit var server: UsbIpServer
    private lateinit var cpuWakeLock: PowerManager.WakeLock
    private lateinit var highPerfWifiLock: WifiManager.WifiLock
    private var lowLatencyWifiLock: WifiManager.WifiLock? = null

    // Devices marked as "shared" by the Activity (via Binder)
    private val sharedDevices = ConcurrentHashMap.newKeySet<Int>()

    private lateinit var usbPermissionIntent: PendingIntent

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (ACTION_USB_PERMISSION == action) {
                val dev = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)!!

                synchronized(dev) {
                    permission.put(
                        dev.deviceId,
                        intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    )
                    (dev as java.lang.Object).notifyAll()
                }

                // If the user granted permission, automatically mark as shared
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                if (granted) {
                    Log.i(
                        TAG,
                        "usbReceiver: permission granted for device " + dev.deviceId
                    )
                    sharedDevices.add(dev.deviceId)
                    updateNotification()
                } else {
                    Log.i(
                        TAG,
                        "usbReceiver: permission denied for device " + dev.deviceId
                    )
                }
            }
        }
    }

    private fun updateNotification() {
        Log.d(
            TAG,
            "updateNotification: shared=" + sharedDevices.size +
                    " connections=" + connections.size()
        )
        val intent = Intent(this, UsbIpConfig::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

        var intentFlags = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            intentFlags = intentFlags or PendingIntent.FLAG_IMMUTABLE
        }

        val pendIntent = PendingIntent.getActivity(this, 0, intent, intentFlags)

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.notification_icon)
            .setOngoing(true)
            .setSilent(true)
            .setTicker(getString(R.string.notification_ticker))
            .setContentTitle(getString(R.string.notification_title))
            .setAutoCancel(false)
            .setContentIntent(pendIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (sharedDevices.isEmpty()) {
            builder.setContentText(getString(R.string.notification_no_devices))
        } else {
            builder.setContentText(
                getString(R.string.notification_sharing_devices, sharedDevices.size)
            )
        }

        startForeground(NOTIFICATION_ID, builder.build())
    }

    @SuppressLint("UseSparseArrays", "UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate: starting USB/IP Service")

        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        connections = SparseArray()
        permission = SparseArray()
        socketMap = HashMap()

        var intentFlags = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // This PendingIntent must be mutable to allow the framework to populate EXTRA_DEVICE and EXTRA_PERMISSION_GRANTED.
            intentFlags = intentFlags or PendingIntent.FLAG_MUTABLE
        }

        val i = Intent(ACTION_USB_PERMISSION)
        i.setPackage(packageName)

        usbPermissionIntent = PendingIntent.getBroadcast(this, 0, i, intentFlags)
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbReceiver, filter)
        }

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        // Check battery optimization exemption status
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val isExempted = pm.isIgnoringBatteryOptimizations(packageName)
            if (!isExempted) {
                Log.w(
                    TAG,
                    "Battery optimization is NOT exempted. The service may be killed or suspended " +
                            "when the screen is off (Doze mode). Consider adding this app to the battery " +
                            "optimization whitelist via Settings."
                )
            } else {
                Log.i(TAG, "Battery optimization is exempted.")
            }
        }

        cpuWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "USBIPServerForAndroid:Service")
        cpuWakeLock.acquire()

        highPerfWifiLock = wm.createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "USBIPServerForAndroid:Service:HP"
        )
        highPerfWifiLock.acquire()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            lowLatencyWifiLock = wm.createWifiLock(
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                "USBIPServerForAndroid:Service:LL"
            )
            lowLatencyWifiLock!!.acquire()
        }

        Log.i(TAG, "onCreate: starting Java TCP server...")
        server = UsbIpServer()
        server.start(this)
        Log.i(TAG, "onCreate: Java server start call returned")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Service Info",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        updateNotification()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy: stopping service")

        server.stop()
        unregisterReceiver(usbReceiver)

        // Clear all shared devices
        sharedDevices.clear()
        updateNotification()

        // Detach all active connections
        for (i in 0 until connections.size()) {
            cleanupDetachedDevice(connections.keyAt(i))
        }

        lowLatencyWifiLock?.release()
        highPerfWifiLock.release()
        cpuWakeLock.release()
    }

    override fun onBind(intent: Intent): IBinder {
        return UsbIpServiceBinder(this)
    }

    // Here we're going to enumerate interfaces and endpoints
    // to eliminate possible speeds until we've narrowed it
    // down to only 1 which is our speed real speed. In a typical
    // USB driver, the host controller knows the real speed but
    // we need to derive it without HCI help.
    private fun detectSpeed(dev: UsbDevice, devDesc: UsbDeviceDescriptor?): Int {
        var possibleSpeeds = FLAG_POSSIBLE_SPEED_LOW or
                FLAG_POSSIBLE_SPEED_FULL or
                FLAG_POSSIBLE_SPEED_HIGH or
                FLAG_POSSIBLE_SPEED_SUPER

        for (i in 0 until dev.interfaceCount) {
            val iface = dev.getInterface(i)
            for (j in 0 until iface.endpointCount) {
                val endpoint = iface.getEndpoint(j)
                if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK ||
                    endpoint.type == UsbConstants.USB_ENDPOINT_XFER_ISOC
                ) {
                    // Low speed devices can't implement bulk or iso endpoints
                    possibleSpeeds = possibleSpeeds and FLAG_POSSIBLE_SPEED_LOW.inv()
                }

                if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_CONTROL) {
                    if (endpoint.maxPacketSize > 8) {
                        // Low speed devices can't use control transfer sizes larger than 8 bytes
                        possibleSpeeds = possibleSpeeds and FLAG_POSSIBLE_SPEED_LOW.inv()
                    }
                    if (endpoint.maxPacketSize < 64) {
                        // High speed devices can't use control transfer sizes smaller than 64 bytes
                        possibleSpeeds = possibleSpeeds and FLAG_POSSIBLE_SPEED_HIGH.inv()
                    }
                    if (endpoint.maxPacketSize < 512) {
                        // Super speed devices can't use control transfer sizes smaller than 512 bytes
                        possibleSpeeds = possibleSpeeds and FLAG_POSSIBLE_SPEED_SUPER.inv()
                    }
                } else if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_INT) {
                    if (endpoint.maxPacketSize > 8) {
                        // Low speed devices can't use interrupt transfer sizes larger than 8 bytes
                        possibleSpeeds = possibleSpeeds and FLAG_POSSIBLE_SPEED_LOW.inv()
                    }
                    if (endpoint.maxPacketSize > 64) {
                        // Full speed devices can't use interrupt transfer sizes larger than 64 bytes
                        possibleSpeeds = possibleSpeeds and FLAG_POSSIBLE_SPEED_FULL.inv()
                    }
                    if (endpoint.maxPacketSize > 512) {
                        // High speed devices can't use interrupt transfer sizes larger than 512 bytes
                        possibleSpeeds = possibleSpeeds and FLAG_POSSIBLE_SPEED_HIGH.inv()
                    }
                } else if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    // A bulk endpoint alone can accurately distiniguish between
                    // full, high, and super speed devices
                    possibleSpeeds = when (endpoint.maxPacketSize) {
                        512 -> FLAG_POSSIBLE_SPEED_HIGH
                        1024 -> FLAG_POSSIBLE_SPEED_SUPER
                        else -> FLAG_POSSIBLE_SPEED_FULL
                    }
                }
            }
        }

        if (devDesc != null) {
            if (devDesc.bcdUSB < 0x200) {
                // High speed only supported on USB 2.0 or higher
                possibleSpeeds = possibleSpeeds and FLAG_POSSIBLE_SPEED_HIGH.inv()
            }
            if (devDesc.bcdUSB < 0x300) {
                // Super speed only supported on USB 3.0 or higher
                possibleSpeeds = possibleSpeeds and FLAG_POSSIBLE_SPEED_SUPER.inv()
            }
        }

        // Return the lowest speed that we're compatible with
        println(
            "Speed heuristics for device %d left us with 0x%x\n".format(
                dev.deviceId,
                possibleSpeeds
            )
        )

        return when {
            (possibleSpeeds and FLAG_POSSIBLE_SPEED_LOW) != 0 -> UsbIpDevice.USB_SPEED_LOW
            (possibleSpeeds and FLAG_POSSIBLE_SPEED_FULL) != 0 -> UsbIpDevice.USB_SPEED_FULL
            (possibleSpeeds and FLAG_POSSIBLE_SPEED_HIGH) != 0 -> UsbIpDevice.USB_SPEED_HIGH
            (possibleSpeeds and FLAG_POSSIBLE_SPEED_SUPER) != 0 -> UsbIpDevice.USB_SPEED_SUPER
            else -> UsbIpDevice.USB_SPEED_UNKNOWN // Something went very wrong in speed detection
        }
    }

    private fun getInfoForDevice(
        dev: UsbDevice,
        devConn: UsbDeviceConnection?
    ): UsbDeviceInfo {
        val info = UsbDeviceInfo()
        val ipDev = UsbIpDevice()

        ipDev.path = dev.deviceName
        ipDev.busnum = deviceIdToBusNum(dev.deviceId)
        ipDev.devnum = deviceIdToDevNum(dev.deviceId)
        ipDev.busid = String.format("%d-%d", ipDev.busnum, ipDev.devnum)

        ipDev.idVendor = dev.vendorId.toShort()
        ipDev.idProduct = dev.productId.toShort()
        ipDev.bcdDevice = -1

        ipDev.bDeviceClass = dev.deviceClass.toByte()
        ipDev.bDeviceSubClass = dev.deviceSubclass.toByte()
        ipDev.bDeviceProtocol = dev.deviceProtocol.toByte()

        ipDev.bConfigurationValue = 0
        ipDev.bNumConfigurations = dev.configurationCount.toByte()

        ipDev.bNumInterfaces = dev.interfaceCount.toByte()

        info.dev = ipDev
        info.interfaces = arrayOfNulls<UsbIpInterface>(ipDev.bNumInterfaces.toInt()) as Array<UsbIpInterface>?

        for (i in 0 until ipDev.bNumInterfaces) {
            info.interfaces!![i] = UsbIpInterface()
            val iface = dev.getInterface(i.toInt())

            info.interfaces!![i]!!.bInterfaceClass = iface.interfaceClass.toByte()
            info.interfaces!![i]!!.bInterfaceSubClass = iface.interfaceSubclass.toByte()
            info.interfaces!![i]!!.bInterfaceProtocol = iface.interfaceProtocol.toByte()
        }

        val context = connections[dev.deviceId]
        var devDesc: UsbDeviceDescriptor? = null
        if (context != null) {
            // Since we're attached already, we can directly query the USB descriptors
            // to fill some information that Android's USB API doesn't expose
            devDesc = UsbControlHelper.readDeviceDescriptor(context.devConn!!)
            if (devDesc != null) {
                ipDev.bcdDevice = devDesc.bcdDevice
            }
        }

        ipDev.speed = detectSpeed(dev, devDesc)

        return info
    }

    override fun getDevices(): List<UsbDeviceInfo> {
        val list = ArrayList<UsbDeviceInfo>()

        Log.i(
            TAG,
            "getDevices: sharedDevices=" + sharedDevices +
                    " totalUSB=" + usbManager.deviceList.size
        )
        for (dev in usbManager.deviceList.values) {
            if (!sharedDevices.contains(dev.deviceId)) {
                Log.d(
                    TAG,
                    "getDevices: skipping device " + dev.deviceName + " (not shared)"
                )
                continue
            }
            Log.i(
                TAG,
                "getDevices: including device " + dev.deviceName +
                        " VendorId=0x" + Integer.toHexString(dev.vendorId)
            )
            val context = connections[dev.deviceId]
            val devConn = context?.devConn

            list.add(getInfoForDevice(dev, devConn))
        }

        Log.i(TAG, "getDevices: returning " + list.size + " devices")
        return list
    }

    // FIXME: This dispatching could use some refactoring so we don't have to pass
    // a million parameters to this guy
    private fun dispatchRequest(
        context: AttachedDeviceContext,
        deviceId: Int,
        s: Socket,
        selectedEndpoint: UsbEndpoint,
        buff: ByteBuffer,
        msg: UsbIpSubmitUrb
    ) {
        context.requestPool!!.submit {
            val reply = UsbIpSubmitUrbReply(msg.seqNum, msg.devId, msg.direction, msg.ep)

            if (msg.direction == UsbIpDevicePacket.USBIP_DIR_IN) {
                // We need to store our buffer in the URB reply
                reply.inData = buff.array()
            }

            if (selectedEndpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (DEBUG) {
                    println(
                        "Bulk transfer - %d bytes %s on EP %d\n".format(
                            buff.array().size,
                            if (msg.direction == UsbIpDevicePacket.USBIP_DIR_IN) "in" else "out",
                            selectedEndpoint.endpointNumber
                        )
                    )
                }

                var res: Int
                do {
                    res = XferUtils.doBulkTransfer(
                        context.devConn!!,
                        selectedEndpoint,
                        buff.array(),
                        1000
                    )

                    if (context.requestPool!!.isShutdown) {
                        // Bail if the queue is being torn down
                        return@submit
                    }

                    if (!context.activeMessages!!.contains(msg)) {
                        // Somebody cancelled the URB, return without responding
                        return@submit
                    }
                } while (res == -110) // ETIMEDOUT

                if (DEBUG) {
                    println(
                        "Bulk transfer complete with %d bytes (wanted %d)\n".format(
                            res,
                            msg.transferBufferLength
                        )
                    )
                }

                if (!context.activeMessages!!.remove(msg)) {
                    // Somebody cancelled the URB, return without responding
                    return@submit
                }

                if (res < 0) {
                    // If the request failed, let's see if the device is still around
                    val dev = getDevice(deviceId)
                    if (dev == null) {
                        // The device is gone, so terminate the client
                        server.killClient(s)
                        return@submit
                    }

                    reply.status = res
                } else {
                    reply.actualLength = res
                    reply.status = ProtoDefs.ST_OK.toInt()
                }

                sendReply(s, reply, reply.status)
            } else if (selectedEndpoint.type == UsbConstants.USB_ENDPOINT_XFER_INT) {
                if (DEBUG) {
                    println(
                        "Interrupt transfer - %d bytes %s on EP %d\n".format(
                            msg.transferBufferLength,
                            if (msg.direction == UsbIpDevicePacket.USBIP_DIR_IN) "in" else "out",
                            selectedEndpoint.endpointNumber
                        )
                    )
                }

                var res: Int
                do {
                    res = XferUtils.doInterruptTransfer(
                        context.devConn!!,
                        selectedEndpoint,
                        buff.array(),
                        1000
                    )

                    if (context.requestPool!!.isShutdown) {
                        // Bail if the queue is being torn down
                        return@submit
                    }

                    if (!context.activeMessages!!.contains(msg)) {
                        // Somebody cancelled the URB, return without responding
                        return@submit
                    }
                } while (res == -110) // ETIMEDOUT

                if (DEBUG) {
                    println(
                        "Interrupt transfer complete with %d bytes (wanted %d)\n".format(
                            res,
                            msg.transferBufferLength
                        )
                    )
                }

                if (!context.activeMessages!!.remove(msg)) {
                    // Somebody cancelled the URB, return without responding
                    return@submit
                }

                if (res < 0) {
                    reply.status = res

                    // If the request failed, let's see if the device is still around
                    val dev = getDevice(deviceId)
                    if (dev == null) {
                        // The device is gone, so terminate the client
                        server.killClient(s)
                        return@submit
                    }
                } else {
                    reply.actualLength = res
                    reply.status = ProtoDefs.ST_OK.toInt()
                }

                sendReply(s, reply, reply.status)
            } else {
                System.err.println(
                    "Unsupported endpoint type: " + selectedEndpoint.type
                )
                context.activeMessages!!.remove(msg)
                server.killClient(s)
            }
        }
    }

    override fun submitUrbRequest(s: Socket, msg: UsbIpSubmitUrb) {
        val reply = UsbIpSubmitUrbReply(msg.seqNum, msg.devId, msg.direction, msg.ep)

        val deviceId = devIdToDeviceId(msg.devId)

        val context = connections[deviceId]
        if (context == null) {
            // This should never happen, but kill the connection if it does
            server.killClient(s)
            return
        }

        val devConn = context.devConn!!

        // Control endpoint is handled with a special case
        if (msg.ep == 0) {
            // This is little endian
            val bb = ByteBuffer.wrap(msg.setup).order(ByteOrder.LITTLE_ENDIAN)

            val requestType = bb.get()
            val request = bb.get()
            val value = bb.short
            val index = bb.short
            val length = bb.short

            if (length.toInt() != 0) {
                reply.inData = ByteArray(length.toInt())
            }

            // This message is now active
            context.activeMessages!!.add(msg)

            var res: Int

            // We have to handle certain control requests (SET_CONFIGURATION/SET_INTERFACE) by calling
            // Android APIs rather than just submitting the URB directly to the device
            if (!UsbControlHelper.handleInternalControlTransfer(
                    context,
                    requestType.toInt(),
                    request.toInt(),
                    value.toInt(),
                    index.toInt()
                )
            ) {
                do {
                    res = XferUtils.doControlTransfer(
                        devConn,
                        requestType.toInt(),
                        request.toInt(),
                        value.toInt(),
                        index.toInt(),
                        if ((requestType.toInt() and 0x80) != 0) reply.inData else msg.outData,
                        length.toInt(),
                        1000
                    )

                    if (context.requestPool!!.isShutdown) {
                        // Bail if the queue is being torn down
                        return
                    }

                    if (!context.activeMessages!!.contains(msg)) {
                        // Somebody cancelled the URB, return without responding
                        return
                    }
                } while (res == -110) // ETIMEDOUT
            } else {
                // Handled the request internally
                res = 0
            }

            if (!context.activeMessages!!.remove(msg)) {
                // Somebody cancelled the URB, return without responding
                return
            }

            if (res < 0) {
                // If the request failed, let's see if the device is still around
                val dev = getDevice(deviceId)
                if (dev == null) {
                    // The device is gone, so terminate the client
                    server.killClient(s)
                    return
                }

                reply.status = res
            } else {
                reply.actualLength = res
                reply.status = ProtoDefs.ST_OK.toInt()
            }

            sendReply(s, reply, reply.status)
        } else {
            // Find the correct endpoint
            var selectedEndpoint: UsbEndpoint? = null
            if (context.activeConfigurationEndpointsByNumDir != null) {
                val endptNumDir = msg.ep +
                        if (msg.direction == UsbIpDevicePacket.USBIP_DIR_IN) UsbConstants.USB_DIR_IN else 0
                selectedEndpoint = context.activeConfigurationEndpointsByNumDir!![endptNumDir]
            } else {
                System.err.println(
                    "Attempted to transfer to non-control EP before SET_CONFIGURATION!"
                )
            }

            if (selectedEndpoint == null) {
                System.err.println("EP not found: " + msg.ep)
                sendReply(s, reply, ProtoDefs.ST_NA.toInt())
                return
            }

            val buff: ByteBuffer
            if (msg.direction == UsbIpDevicePacket.USBIP_DIR_IN) {
                // The buffer is allocated by us
                buff = ByteBuffer.allocate(msg.transferBufferLength)
            } else {
                // The buffer came in with the request
                buff = ByteBuffer.wrap(msg.outData)
            }

            // This message is now active
            context.activeMessages!!.add(msg)

            // Dispatch this request asynchronously
            dispatchRequest(context, deviceId, s, selectedEndpoint, buff, msg)
        }
    }

    private fun getDevice(deviceId: Int): UsbDevice? {
        for (dev in usbManager.deviceList.values) {
            if (dev.deviceId == deviceId) {
                return dev
            }
        }
        return null
    }

    private fun getDevice(busId: String): UsbDevice? {
        return getDevice(busIdToDeviceId(busId))
    }

    override fun getDeviceByBusId(busId: String): UsbDeviceInfo? {
        val dev = getDevice(busId) ?: return null

        val context = connections[dev.deviceId]
        val devConn = context?.devConn

        return getInfoForDevice(dev, devConn)
    }

    override fun attachToDevice(s: Socket, busId: String): Boolean {
        val dev = getDevice(busId) ?: return false

        if (connections[dev.deviceId] != null) {
            // Already attached
            return false
        }

        if (!usbManager.hasPermission(dev)) {
            // Try to get permission from the user
            permission.put(dev.deviceId, null)
            usbManager.requestPermission(dev, usbPermissionIntent)
            synchronized(dev) {
                while (permission[dev.deviceId] == null) {
                    try {
                        (dev as java.lang.Object).wait(1000)
                    } catch (e: InterruptedException) {
                        return false
                    }
                }
            }

            // User may have rejected this
            if (!permission[dev.deviceId]!!) {
                return false
            }
        }

        val devConn = usbManager.openDevice(dev) ?: return false

        // Create a context for this attachment
        val context = AttachedDeviceContext()
        context.devConn = devConn
        context.device = dev

        // Count all endpoints on all interfaces
        var endpointCount = 0
        for (i in 0 until dev.interfaceCount) {
            endpointCount += dev.getInterface(i).endpointCount
        }

        // Use a thread pool with a thread per endpoint
        context.requestPool = ThreadPoolExecutor(
            endpointCount,
            endpointCount,
            Long.MAX_VALUE,
            TimeUnit.DAYS,
            LinkedBlockingQueue(),
            ThreadPoolExecutor.DiscardPolicy()
        )

        // Create the active message set
        context.activeMessages = HashSet()

        connections.put(dev.deviceId, context)
        socketMap[s] = context

        updateNotification()
        return true
    }

    private fun cleanupDetachedDevice(deviceId: Int) {
        val context = connections[deviceId] ?: return

        // Clear this attachment's context
        connections.remove(deviceId)

        // Signal queue death
        context.requestPool!!.shutdownNow()

        // Release our claim to the interfaces
        for (i in 0 until context.device!!.interfaceCount) {
            context.devConn!!.releaseInterface(context.device!!.getInterface(i))
        }

        // Close the connection
        context.devConn!!.close()

        // Wait for the queue to die
        try {
            context.requestPool!!.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS)
        } catch (_: InterruptedException) {
        }

        updateNotification()
    }

    override fun detachFromDevice(s: Socket, busId: String) {
        val dev = getDevice(busId) ?: return
        cleanupDetachedDevice(dev.deviceId)
    }

    override fun cleanupSocket(s: Socket) {
        val context = socketMap.remove(s) ?: return
        cleanupDetachedDevice(context.device!!.deviceId)
    }

    override fun abortUrbRequest(s: Socket, msg: UsbIpUnlinkUrb) {
        val context = socketMap[s] ?: return

        val reply = UsbIpUnlinkUrbReply(msg.seqNum, msg.devId, msg.direction, msg.ep)

        var found = false
        synchronized(context.activeMessages!!) {
            for (urbMsg in context.activeMessages!!) {
                if (msg.seqNumToUnlink == urbMsg.seqNum) {
                    context.activeMessages!!.remove(urbMsg)
                    found = true
                    break
                }
            }
        }

        println("Removed URB? " + if (found) "yes" else "no")
        sendReply(
            s,
            reply,
            if (found) UsbIpDevicePacket.USBIP_STATUS_URB_ABORTED else -22 // EINVAL
        )
    }

    // === Methods called by UsbIpServiceBinder (Activity communication) ===

    internal fun doShareDevice(deviceId: Int): Boolean {
        val dev = getDevice(deviceId)
        if (dev == null) {
            Log.w(TAG, "doShareDevice: device $deviceId not found")
            return false
        }

        // If we already have permission, share immediately
        if (usbManager.hasPermission(dev)) {
            Log.i(TAG, "doShareDevice: already have permission for device $deviceId")
            sharedDevices.add(deviceId)
            updateNotification()
            return true
        }

        // Request permission asynchronously
        Log.i(TAG, "doShareDevice: requesting permission for device $deviceId")
        permission.put(deviceId, null)
        usbManager.requestPermission(dev, usbPermissionIntent)
        return false
    }

    internal fun doUnshareDevice(deviceId: Int) {
        sharedDevices.remove(deviceId)

        // If this device is currently attached by a remote client, kill its socket
        var clientSocket: Socket? = null
        for (entry in socketMap.entries) {
            if (entry.value.device!!.deviceId == deviceId) {
                clientSocket = entry.key
                break
            }
        }
        if (clientSocket != null) {
            server.killClient(clientSocket)
        }

        updateNotification()
    }

    internal fun getSharedDeviceIds(): Set<Int> {
        return HashSet(sharedDevices)
    }

    companion object {
        private const val TAG = "UsbIpService"
        private const val DEBUG = false

        private const val NOTIFICATION_ID = 100

        private const val CHANNEL_ID = "serviceInfo"

        private const val ACTION_USB_PERMISSION = "org.cgutman.usbip.USB_PERMISSION"

        private const val FLAG_POSSIBLE_SPEED_LOW = 0x01
        private const val FLAG_POSSIBLE_SPEED_FULL = 0x02
        private const val FLAG_POSSIBLE_SPEED_HIGH = 0x04
        private const val FLAG_POSSIBLE_SPEED_SUPER = 0x08

        @JvmStatic
        fun deviceIdToBusNum(deviceId: Int): Int {
            return deviceId / 1000
        }

        @JvmStatic
        fun deviceIdToDevNum(deviceId: Int): Int {
            return deviceId % 1000
        }

        @JvmStatic
        fun devIdToDeviceId(devId: Int): Int {
            // This is the same algorithm as Android uses
            return ((devId shr 16) and 0xFF) * 1000 + (devId and 0xFF)
        }

        @JvmStatic
        fun busIdToBusNum(busId: String): Int {
            if (busId.indexOf('-') == -1) {
                return -1
            }
            return Integer.parseInt(busId.substring(0, busId.indexOf('-')))
        }

        @JvmStatic
        fun busIdToDevNum(busId: String): Int {
            if (busId.indexOf('-') == -1) {
                return -1
            }
            return Integer.parseInt(busId.substring(busId.indexOf('-') + 1))
        }

        @JvmStatic
        fun busIdToDeviceId(busId: String): Int {
            return devIdToDeviceId(
                ((busIdToBusNum(busId) shl 16) and 0xFF0000) or busIdToDevNum(busId)
            )
        }

        @JvmStatic
        fun dumpInterfaces(dev: UsbDevice) {
            for (i in 0 until dev.interfaceCount) {
                println(
                    "%d - Iface %d (%02x/%02x/%02x)\n".format(
                        i,
                        dev.getInterface(i).id,
                        dev.getInterface(i).interfaceClass,
                        dev.getInterface(i).interfaceSubclass,
                        dev.getInterface(i).interfaceProtocol
                    )
                )

                val iface = dev.getInterface(i)
                for (j in 0 until iface.endpointCount) {
                    println(
                        "\t%d - Endpoint %d (%x/%x)\n".format(
                            j,
                            iface.getEndpoint(j).endpointNumber,
                            iface.getEndpoint(j).address,
                            iface.getEndpoint(j).attributes
                        )
                    )
                }
            }
        }

        @JvmStatic
        private fun sendReply(s: Socket, reply: UsbIpSubmitUrbReply, status: Int) {
            reply.status = status
            try {
                // We need to synchronize to avoid writing on top of ourselves
                synchronized(s) {
                    s.getOutputStream().write(reply.serialize())
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        @JvmStatic
        private fun sendReply(s: Socket, reply: UsbIpUnlinkUrbReply, status: Int) {
            reply.status = status
            try {
                // We need to synchronize to avoid writing on top of ourselves
                synchronized(s) {
                    s.getOutputStream().write(reply.serialize())
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}
