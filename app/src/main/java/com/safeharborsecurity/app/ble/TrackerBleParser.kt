package com.safeharborsecurity.app.ble

import android.bluetooth.le.ScanResult
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

private const val TAG = "TrackerBleParser"

/**
 * Item 3 — BLE tracker (AirTag/Tile/SmartTag/Chipolo/Pebblebee) recognition.
 *
 * Each tracker family advertises a recognisable signature in its BLE scan
 * payload. We don't try to decrypt any of them — we just recognise them and
 * tell the user "you've got a Tile near you" so they can decide if it's
 * theirs or someone else's.
 *
 * References:
 * - Apple AirTag: manufacturer ID 0x004C, payload byte 2 == 0x12 (FindMy)
 * - Samsung SmartTag: service UUID 0xFD5A
 * - Tile: service UUID 0xFEED (Tile, Inc.)
 * - Chipolo (non-Apple): service UUID 0xFE9F
 * - Pebblebee: service UUID 0xFE74
 */
enum class TrackerKind(val displayName: String, val emoji: String) {
    AIRTAG("Apple AirTag", "🍎"),
    APPLE_FINDMY("Apple Find My device", "🍎"),
    SAMSUNG_SMARTTAG("Samsung SmartTag", "📍"),
    TILE("Tile tracker", "🟦"),
    CHIPOLO("Chipolo tracker", "🟢"),
    PEBBLEBEE("Pebblebee tracker", "🟡"),
    UNKNOWN_TRACKER_LIKE("Unknown tracker-like device", "❓")
}

data class DetectedTracker(
    val kind: TrackerKind,
    val mac: String,
    val rssi: Int,
    val name: String?
)

object TrackerBleParser {

    private const val APPLE_MFG_ID = 0x004C
    private val SAMSUNG_SMARTTAG_UUID = ParcelUuid.fromString("0000FD5A-0000-1000-8000-00805F9B34FB")
    private val TILE_UUID = ParcelUuid.fromString("0000FEED-0000-1000-8000-00805F9B34FB")
    private val CHIPOLO_UUID = ParcelUuid.fromString("0000FE9F-0000-1000-8000-00805F9B34FB")
    private val PEBBLEBEE_UUID = ParcelUuid.fromString("0000FE74-0000-1000-8000-00805F9B34FB")

    /** Returns null if [result] doesn't look like a known tracker. */
    fun parse(result: ScanResult): DetectedTracker? {
        val record = result.scanRecord ?: return null
        val mac = result.device?.address ?: return null
        @Suppress("MissingPermission")
        val name: String? = try { result.device?.name } catch (_: SecurityException) { null }
        val rssi = result.rssi

        // Service UUID-based detections (cheap, exact)
        record.serviceUuids?.forEach { uuid ->
            when (uuid) {
                SAMSUNG_SMARTTAG_UUID ->
                    return DetectedTracker(TrackerKind.SAMSUNG_SMARTTAG, mac, rssi, name)
                TILE_UUID ->
                    return DetectedTracker(TrackerKind.TILE, mac, rssi, name)
                CHIPOLO_UUID ->
                    return DetectedTracker(TrackerKind.CHIPOLO, mac, rssi, name)
                PEBBLEBEE_UUID ->
                    return DetectedTracker(TrackerKind.PEBBLEBEE, mac, rssi, name)
            }
        }

        // Apple manufacturer-specific data: 0x12 prefix marks an AirTag /
        // FindMy advertisement. Other Apple devices (AirPods, iPhones) use
        // different prefixes; we only flag the FindMy family.
        val appleData = record.getManufacturerSpecificData(APPLE_MFG_ID)
        if (appleData != null && appleData.size >= 2) {
            val typeByte = appleData[0].toInt() and 0xFF
            val length = appleData[1].toInt() and 0xFF
            // Diagnostic: log every Apple advert we see, even ones that don't
            // match the FindMy 0x12 type, so when an AirTag isn't detected we
            // can tell from logcat whether it's a parsing bug or whether the
            // tag isn't broadcasting in our window.
            Log.d(TAG, "Apple BLE advert: mac=$mac type=0x${"%02x".format(typeByte)} len=0x${"%02x".format(length)} rssi=$rssi")
            if (typeByte == 0x12) {
                // length 25 = "nearby" AirTag, 2 = "owner connected" (less alarming)
                val kind = if (length >= 0x19) TrackerKind.AIRTAG else TrackerKind.APPLE_FINDMY
                return DetectedTracker(kind, mac, rssi, name)
            }
        }

        // Heuristic fallback: tiny BLE devices with names like "Trkr" or
        // "Finder" that don't match a known UUID. Low confidence; we mark
        // these for user review rather than auto-flagging.
        val nm = name?.lowercase() ?: return null
        val keywords = listOf("airtag", "smarttag", "tile", "chipolo", "pebblebee", "tracker", "finder", "lost")
        if (keywords.any { nm.contains(it) }) {
            return DetectedTracker(TrackerKind.UNKNOWN_TRACKER_LIKE, mac, rssi, name)
        }
        return null
    }

    /** Coarse "very close / nearby / far" categorisation from RSSI dBm. */
    fun proximityLabel(rssi: Int): String = when {
        rssi >= -55 -> "Very close"
        rssi >= -75 -> "Nearby"
        else -> "Farther away"
    }

    fun proximityWarningLevel(rssi: Int): Int = when {
        rssi >= -55 -> 2  // very close — most concerning if not yours
        rssi >= -75 -> 1
        else -> 0
    }
}
