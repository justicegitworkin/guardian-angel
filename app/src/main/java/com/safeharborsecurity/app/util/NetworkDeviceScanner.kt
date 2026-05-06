package com.safeharborsecurity.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.safeharborsecurity.app.data.model.DeviceType
import com.safeharborsecurity.app.data.model.NetworkDeviceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.FileReader
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Part D1: Discovers devices on the local subnet by reading the kernel ARP
 * table at /proc/net/arp. Then matches MAC OUI prefixes against known camera
 * manufacturer ranges to flag potential hidden cameras.
 *
 * On modern Android (10+), ping must succeed first to populate the ARP cache.
 * We do a parallel ping sweep of the /24 subnet (max 254 hosts), then re-read
 * the ARP table.
 */
@Singleton
class NetworkDeviceScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "NetworkDeviceScanner"
        private const val PING_TIMEOUT_MS = 250
        private const val SWEEP_TIMEOUT_MS = 4_000L

        // MAC OUI → manufacturer (lowercase, colon-separated 8 chars)
        private val OUI_MAP: Map<String, String> = mapOf(
            // Hikvision
            "3c:a3:08" to "Hikvision", "bc:ad:28" to "Hikvision",
            "c0:56:e3" to "Hikvision", "28:57:be" to "Hikvision",
            "54:c4:15" to "Hikvision",
            // Dahua
            "90:02:a9" to "Dahua", "4c:11:ae" to "Dahua",
            "a4:14:37" to "Dahua", "3c:ef:8c" to "Dahua",
            // Reolink
            "ec:71:db" to "Reolink", "48:02:2a" to "Reolink",
            // Axis
            "00:40:8c" to "Axis", "ac:cc:8e" to "Axis",
            // Wyze
            "2c:aa:8e" to "Wyze", "d0:3f:27" to "Wyze",
            // Ring (Amazon Lab126)
            "54:88:0e" to "Ring", "d4:73:d7" to "Ring",
            // Nest / Google
            "f4:f5:d8" to "Nest", "64:16:66" to "Nest",
            "18:b4:30" to "Nest",
            // Amcrest
            "e0:50:8b" to "Amcrest", "38:af:d7" to "Amcrest",
            // Foscam
            "c4:3c:b0" to "Foscam",
            // Yi
            "00:62:6e" to "Yi",
            // Eufy / Anker
            "d4:ad:bd" to "Eufy",
            // Arlo / Netgear
            "10:0d:7f" to "Arlo", "9c:3d:cf" to "Arlo"
        )

        private val CAMERA_BRANDS = setOf(
            "Hikvision", "Dahua", "Reolink", "Axis", "Wyze",
            "Ring", "Nest", "Amcrest", "Foscam", "Yi",
            "Eufy", "Arlo"
        )
    }

    /**
     * Run a discovery sweep. Returns a list of devices found on the local subnet.
     * Skips quickly if not connected to WiFi or if the local subnet can't be
     * determined.
     */
    suspend fun discoverLocalDevices(): List<NetworkDeviceInfo> = withContext(Dispatchers.IO) {
        if (!isOnWifi()) {
            Log.d(TAG, "Not on WiFi — skipping local device discovery")
            return@withContext emptyList()
        }

        val gateway = getGatewayIp() ?: run {
            Log.d(TAG, "Could not determine gateway / subnet")
            return@withContext emptyList()
        }
        val subnetPrefix = gateway.substringBeforeLast('.')

        // Ping sweep to populate ARP cache. Bounded by SWEEP_TIMEOUT_MS overall.
        withTimeoutOrNull(SWEEP_TIMEOUT_MS) {
            coroutineScope {
                (1..254).map { i ->
                    async(Dispatchers.IO) {
                        runCatching {
                            InetAddress.getByName("$subnetPrefix.$i").isReachable(PING_TIMEOUT_MS)
                        }
                    }
                }.awaitAll()
            }
        }

        // Read the ARP table now that the cache is warm
        val devices = readArpTable(subnetPrefix)
        Log.d(TAG, "Discovered ${devices.size} devices on subnet $subnetPrefix.0/24")
        devices
    }

    private fun isOnWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun getGatewayIp(): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        val active = cm.activeNetwork ?: return null
        val link: LinkProperties = cm.getLinkProperties(active) ?: return null
        for (route in link.routes) {
            val gw = route.gateway?.hostAddress ?: continue
            if (gw != "0.0.0.0" && gw != "::") return gw
        }
        // Fallback: first IPv4 address in our own subnet → assume .1 is the gateway
        for (addr in link.linkAddresses) {
            val host = addr.address.hostAddress ?: continue
            if (host.contains(":")) continue  // skip IPv6
            return host.substringBeforeLast('.') + ".1"
        }
        return null
    }

    private fun readArpTable(subnetPrefix: String): List<NetworkDeviceInfo> {
        val devices = mutableListOf<NetworkDeviceInfo>()
        runCatching {
            BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                reader.readLine() // header
                while (true) {
                    val line = reader.readLine() ?: break
                    val parts = line.split("\\s+".toRegex()).filter { it.isNotBlank() }
                    if (parts.size < 6) continue
                    val ip = parts[0]
                    val flags = parts[2]
                    val mac = parts[3].lowercase()
                    if (mac == "00:00:00:00:00:00" || flags == "0x0") continue
                    if (!ip.startsWith("$subnetPrefix.")) continue

                    val macPrefix = mac.take(8)
                    val manufacturer = OUI_MAP[macPrefix] ?: "Unknown"
                    val type = when {
                        manufacturer in CAMERA_BRANDS -> DeviceType.CAMERA
                        manufacturer != "Unknown" -> DeviceType.IOT_LIKELY
                        else -> DeviceType.UNKNOWN
                    }
                    val confidence = when (type) {
                        DeviceType.CAMERA -> 90
                        DeviceType.IOT_LIKELY -> 70
                        DeviceType.UNKNOWN -> 30
                    }
                    devices.add(
                        NetworkDeviceInfo(
                            ipAddress = ip,
                            macAddress = mac,
                            manufacturer = manufacturer,
                            deviceType = type,
                            confidence = confidence
                        )
                    )
                }
            }
        }.onFailure { e -> Log.w(TAG, "Could not read ARP table: ${e.message}") }
        return devices.sortedWith(
            compareByDescending<NetworkDeviceInfo> { it.deviceType.ordinal == DeviceType.CAMERA.ordinal }
                .thenByDescending { it.confidence }
        )
    }
}
