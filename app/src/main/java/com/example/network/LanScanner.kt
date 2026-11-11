package com.example.network

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

object LanScanner {

    const val DEFAULT_9ROUTER_PORT = 20128

    fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu"))
    }

    /**
     * Returns all active private IPv4 addresses on this device (e.g. ["192.168.1.24"]).
     */
    fun getDeviceLanIps(): List<String> {
        val result = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        // Exclude Android emulator internal guest IP 10.0.2.15 when looking for real LAN IPs
                        if (ip != "10.0.2.15" && (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172."))) {
                            result.add(ip)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result.distinct()
    }

    /**
     * Scans the phone's Wi-Fi / hotspot / USB-tethering /24 subnets for an open 9Router port (20128).
     * Returns the discovered base URL (e.g. "http://192.168.1.10:20128/v1") or null if none found.
     */
    suspend fun discover9RouterOnLan(
        port: Int = DEFAULT_9ROUTER_PORT,
        onProgress: (String) -> Unit = {}
    ): String? = withContext(Dispatchers.IO) {
        val deviceIps = getDeviceLanIps()

        if (deviceIps.isEmpty()) {
            // If on emulator, test 10.0.2.2
            if (isEmulator() && isPortOpen("10.0.2.2", port, 500)) {
                return@withContext "http://10.0.2.2:$port/v1"
            }
            return@withContext null
        }

        val semaphore = Semaphore(64)

        for (myIp in deviceIps) {
            val parts = myIp.split(".")
            if (parts.size != 4) continue
            val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
            val myLastOctet = parts[3].toIntOrNull() ?: -1

            onProgress("Memindai jaringan $prefix.1 - $prefix.254 pada port $port...")

            // Priority check: .1 (router/hotspot host) first
            if (myLastOctet != 1 && isPortOpen("$prefix.1", port, 350)) {
                return@withContext "http://$prefix.1:$port/v1"
            }

            // Scan all 1..254 in parallel
            val foundIp = coroutineScope {
                (1..254).filter { it != myLastOctet }.map { host ->
                    async {
                        semaphore.withPermit {
                            val candidateIp = "$prefix.$host"
                            if (isPortOpen(candidateIp, port, 380)) candidateIp else null
                        }
                    }
                }.awaitAll().firstOrNull { it != null }
            }

            if (foundIp != null) {
                return@withContext "http://$foundIp:$port/v1"
            }
        }

        // Also check 10.0.2.2 as fallback if on emulator
        if (isEmulator() && isPortOpen("10.0.2.2", port, 500)) {
            return@withContext "http://10.0.2.2:$port/v1"
        }

        null
    }

    fun isPortOpen(ip: String, port: Int, timeoutMs: Int = 400): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
