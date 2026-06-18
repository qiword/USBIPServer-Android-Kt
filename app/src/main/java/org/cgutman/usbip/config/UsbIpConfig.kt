package org.cgutman.usbip.config

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.text.format.Formatter
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import org.cgutman.usbip.service.UsbIpService
import org.cgutman.usbip.service.UsbIpServiceBinder
import org.cgutman.usbipserverforandroid.R

class UsbIpConfig : ComponentActivity() {

    private lateinit var serviceButton: MaterialButton
    private lateinit var serviceStatus: TextView
    private lateinit var serviceReadyText: TextView
    private lateinit var connectionInfoCard: MaterialCardView
    private lateinit var ipAddressText: TextView
    private lateinit var portText: TextView
    private lateinit var deviceListTitle: TextView
    private lateinit var deviceRecyclerView: RecyclerView
    private lateinit var deviceAdapter: DeviceAdapter
    private lateinit var emptyDeviceText: TextView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var batteryOptimizationButton: Button
    private lateinit var toolbar: MaterialToolbar

    private var running: Boolean = false

    // Track which devices are currently shared (mirrored from Service)
    private val sharedDevices = HashSet<Int>()

    private lateinit var usbManager: UsbManager
    private var serviceBinder: UsbIpServiceBinder? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshDeviceList()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            serviceBinder = service as UsbIpServiceBinder
            syncSharedDevicesFromService()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            serviceBinder = null
        }
    }

    private fun syncSharedDevicesFromService() {
        serviceBinder?.let {
            sharedDevices.clear()
            sharedDevices.addAll(it.getSharedDeviceIds())
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            startServiceAndBind()
        }

    private fun startServiceAndBind() {
        val intent = Intent(this@UsbIpConfig, UsbIpService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun updateStatus() {
        if (running) {
            serviceButton.text = getString(R.string.btn_stop_service)
            serviceButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#B71C1C")))
            serviceStatus.text = getString(R.string.status_running)
            serviceReadyText.visibility = View.VISIBLE
            serviceReadyText.setText(R.string.ready_text)
            ipAddressText.text = getWifiIpAddress()
        } else {
            serviceButton.text = getString(R.string.btn_start_service)
            serviceButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CAF50")))
            serviceStatus.text = getString(R.string.status_stopped)
            serviceReadyText.visibility = View.INVISIBLE
            ipAddressText.text = getWifiIpAddress()
        }
        portText.text = USBIP_PORT.toString()
        connectionInfoCard.visibility = View.VISIBLE
    }

    private fun getWifiIpAddress(): String {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null) {
                val wifiInfo = wifiManager.connectionInfo
                if (wifiInfo != null) {
                    val ip = wifiInfo.ipAddress
                    if (ip != 0) {
                        return Formatter.formatIpAddress(ip)
                    }
                }
            }
        } catch (e: SecurityException) {
            return getString(R.string.wifi_permission_denied)
        }
        return getString(R.string.wifi_not_connected)
    }

    private fun shareDevice(device: UsbDevice) {
        Log.i(
            TAG,
            "shareDevice: deviceId=" + device.deviceId + " serviceBinder=" + (serviceBinder != null)
        )
        serviceBinder?.let {
            val granted = it.shareDevice(device.deviceId)
            Log.i(TAG, "shareDevice: Binder result=$granted")
            if (granted) {
                syncSharedDevicesFromService()
                refreshDeviceList()
            }
        }
    }

    private fun unshareDevice(device: UsbDevice) {
        serviceBinder?.unshareDevice(device.deviceId)
        sharedDevices.remove(device.deviceId)
        refreshDeviceList()
    }

    private fun refreshDeviceList() {
        syncSharedDevicesFromService()

        val deviceMap = usbManager.deviceList

        if (deviceMap.isEmpty()) {
            deviceListTitle.visibility = View.GONE
            deviceRecyclerView.visibility = View.GONE
            emptyDeviceText.visibility = View.VISIBLE
            return
        }

        deviceListTitle.visibility = View.VISIBLE
        deviceRecyclerView.visibility = View.VISIBLE
        emptyDeviceText.visibility = View.GONE

        val deviceList = deviceMap.values.map { device ->
            Pair(device, sharedDevices.contains(device.deviceId))
        }
        deviceAdapter.submitList(deviceList)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_usbip_config)

        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        toolbar = findViewById(R.id.toolbar)
        serviceButton = findViewById(R.id.serviceButton)
        serviceStatus = findViewById(R.id.serviceStatus)
        serviceReadyText = findViewById(R.id.serviceReadyText)
        connectionInfoCard = findViewById(R.id.connectionInfoCard)
        ipAddressText = findViewById(R.id.ipAddressText)
        portText = findViewById(R.id.portText)
        deviceListTitle = findViewById(R.id.deviceListTitle)
        deviceRecyclerView = findViewById(R.id.deviceRecyclerView)
        emptyDeviceText = findViewById(R.id.emptyDeviceText)
        drawerLayout = findViewById(R.id.drawerLayout)
        batteryOptimizationButton = findViewById(R.id.batteryOptimizationButton)

        // Setup RecyclerView
        deviceRecyclerView.layoutManager = LinearLayoutManager(this)
        deviceAdapter = DeviceAdapter()
        deviceRecyclerView.adapter = deviceAdapter

        toolbar.inflateMenu(R.menu.menu_main)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_settings -> {
                    drawerLayout.openDrawer(GravityCompat.END)
                    true
                }

                else -> false
            }
        }

        updateStatus()

        serviceButton.setOnClickListener {
            if (running) {
                stopService(Intent(this@UsbIpConfig, UsbIpService::class.java))
                if (serviceBinder != null) {
                    unbindService(serviceConnection)
                    serviceBinder = null
                }
                sharedDevices.clear()
                refreshDeviceList()
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(
                            this@UsbIpConfig,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        startServiceAndBind()
                    } else {
                        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    startServiceAndBind()
                }
            }

            running = !running
            updateStatus()
        }

        // Battery optimization button in drawer
        batteryOptimizationButton.setOnClickListener {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to open battery settings", e)
            }
            drawerLayout.closeDrawer(GravityCompat.END)
        }

        // Listen for USB device attach/detach instead of polling
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        registerReceiver(usbReceiver, filter)

        refreshDeviceList()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        if (running) {
            ipAddressText.text = getWifiIpAddress()
        }
        refreshDeviceList()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(usbReceiver)
        } catch (_: IllegalArgumentException) {
        }
        try {
            unbindService(serviceConnection)
        } catch (_: IllegalArgumentException) {
        }
    }

    // --- RecyclerView Adapter ---

    private inner class DeviceAdapter : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {

        private val devices = mutableListOf<Pair<UsbDevice, Boolean>>()

        fun submitList(list: List<Pair<UsbDevice, Boolean>>) {
            devices.clear()
            devices.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val itemView = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_device, parent, false)
            return ViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val (device, isShared) = devices[position]

            // Status dot
            holder.statusDot.setBackgroundColor(
                Color.parseColor(if (isShared) "#4CAF50" else "#757575")
            )

            // Device name
            val deviceName = device.productName
            val vendorName = device.manufacturerName
            val labelBuilder = StringBuilder()
            if (vendorName != null && vendorName.isNotEmpty()) {
                labelBuilder.append(vendorName).append(" ")
            }
            if (deviceName != null && deviceName.isNotEmpty()) {
                labelBuilder.append(deviceName)
            } else {
                labelBuilder.append(getString(R.string.device_unknown))
            }
            holder.nameText.text = labelBuilder.toString().trim()

            // Meta: status + VID/PID
            val statusText = if (isShared) getString(R.string.device_shared) else getString(R.string.device_not_shared)
            holder.metaText.text = "$statusText | ${String.format("%04X:%04X", device.vendorId, device.productId)}"
            holder.metaText.setTextColor(
                if (isShared) Color.parseColor("#4CAF50")
                else Color.parseColor("#999999")
            )

            // Action button
            if (isShared) {
                holder.actionButton.text = getString(R.string.btn_unshare)
                holder.actionButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#B71C1C")))
            } else {
                holder.actionButton.text = getString(R.string.btn_share)
                holder.actionButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CAF50")))
            }
            holder.actionButton.minWidth = 0

            holder.actionButton.setOnClickListener {
                if (sharedDevices.contains(device.deviceId)) {
                    unshareDevice(device)
                } else {
                    shareDevice(device)
                }
            }
        }

        override fun getItemCount(): Int = devices.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val statusDot: View = itemView.findViewById(R.id.device_status_dot)
            val nameText: TextView = itemView.findViewById(R.id.device_name)
            val metaText: TextView = itemView.findViewById(R.id.device_meta)
            val actionButton: MaterialButton = itemView.findViewById(R.id.device_action_button)
        }
    }

    companion object {
        private const val TAG = "UsbIpConfig"
        private const val USBIP_PORT = 3240
    }
}
