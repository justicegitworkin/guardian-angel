package com.safeharborsecurity.app.util

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Build
import android.util.Log
import com.safeharborsecurity.app.data.model.NdefPayload
import com.safeharborsecurity.app.data.model.NfcRiskLevel
import com.safeharborsecurity.app.data.model.NfcState
import com.safeharborsecurity.app.data.model.NfcTagAnalysis
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NfcSecurityManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(context)

    fun getNfcState(): NfcState {
        return NfcState(
            isNfcAvailable = nfcAdapter != null,
            isNfcEnabled = nfcAdapter?.isEnabled == true
        )
    }

    fun enableForegroundDispatch(activity: Activity) {
        val adapter = nfcAdapter ?: return
        val intent = Intent(activity, activity.javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(activity, 0, intent, flags)
        val techList = arrayOf(
            arrayOf("android.nfc.tech.Ndef"),
            arrayOf("android.nfc.tech.NdefFormatable"),
            arrayOf("android.nfc.tech.NfcA"),
            arrayOf("android.nfc.tech.NfcB"),
            arrayOf("android.nfc.tech.NfcF"),
            arrayOf("android.nfc.tech.NfcV"),
            arrayOf("android.nfc.tech.IsoDep"),
            arrayOf("android.nfc.tech.MifareClassic"),
            arrayOf("android.nfc.tech.MifareUltralight")
        )
        val filters = arrayOf(
            IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        )
        try {
            adapter.enableForegroundDispatch(activity, pendingIntent, filters, techList)
        } catch (e: Exception) {
            Log.w(TAG, "Could not enable NFC foreground dispatch: ${e.message}")
        }
    }

    fun disableForegroundDispatch(activity: Activity) {
        try {
            nfcAdapter?.disableForegroundDispatch(activity)
        } catch (e: Exception) {
            Log.w(TAG, "Could not disable NFC foreground dispatch: ${e.message}")
        }
    }

    fun analyzeTag(intent: Intent): NfcTagAnalysis? {
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        } ?: return null

        val tagId = tag.id?.joinToString("") { "%02X".format(it) } ?: "Unknown"
        val techList = tag.techList.map { it.substringAfterLast('.') }

        Log.d(TAG, "NFC tag detected: id=$tagId techs=$techList")

        val payloads = mutableListOf<NdefPayload>()
        var riskLevel = NfcRiskLevel.SAFE
        val riskReasons = mutableListOf<String>()

        // Read NDEF data
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            try {
                ndef.connect()
                val ndefMessage = ndef.ndefMessage
                if (ndefMessage != null) {
                    for (record in ndefMessage.records) {
                        val payload = parseNdefRecord(record)
                        payloads.add(payload)

                        if (payload.isSuspicious) {
                            riskLevel = maxOf(riskLevel, NfcRiskLevel.CAUTION)
                            riskReasons.add("Suspicious content: ${payload.type}")
                        }
                        if (payload.isUrl) {
                            val url = payload.content.lowercase()
                            if (isSuspiciousUrl(url)) {
                                riskLevel = NfcRiskLevel.DANGEROUS
                                riskReasons.add("Suspicious URL detected")
                            } else {
                                riskLevel = maxOf(riskLevel, NfcRiskLevel.CAUTION)
                                riskReasons.add("Contains a web link — check before visiting")
                            }
                        }
                    }
                }
                ndef.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error reading NFC tag: ${e.message}")
                riskLevel = NfcRiskLevel.UNKNOWN
                riskReasons.add("Could not fully read this tag")
            }
        } else {
            // Non-NDEF tag
            payloads.add(NdefPayload(type = "Non-NDEF", content = "This tag uses a format Safe Companion cannot read"))
            riskLevel = NfcRiskLevel.CAUTION
            riskReasons.add("Unknown tag format")
        }

        // Check for payment-related tech
        if (techList.any { it in listOf("IsoDep", "NfcA", "NfcB") } && payloads.isEmpty()) {
            riskLevel = maxOf(riskLevel, NfcRiskLevel.CAUTION)
            riskReasons.add("This may be a payment card or access card")
        }

        val summary = when (riskLevel) {
            NfcRiskLevel.SAFE -> "This NFC tag looks safe."
            NfcRiskLevel.CAUTION -> "This NFC tag needs a closer look."
            NfcRiskLevel.DANGEROUS -> "This NFC tag may be dangerous!"
            NfcRiskLevel.UNKNOWN -> "Safe Companion could not fully read this tag."
        }

        val details = if (riskReasons.isNotEmpty()) {
            riskReasons.joinToString(". ") + "."
        } else {
            "No risks found. Tag ID: $tagId."
        }

        val recommendation = when (riskLevel) {
            NfcRiskLevel.SAFE -> "This tag is safe to use."
            NfcRiskLevel.CAUTION -> "Be careful. Don't tap this tag again unless you trust where it came from."
            NfcRiskLevel.DANGEROUS -> "Do not use this tag. It may try to take you to a dangerous website or steal information."
            NfcRiskLevel.UNKNOWN -> "If you don't recognise this tag, avoid tapping it again."
        }

        return NfcTagAnalysis(
            tagId = tagId,
            techList = techList,
            payloads = payloads,
            riskLevel = riskLevel,
            summary = summary,
            details = details,
            recommendation = recommendation
        )
    }

    private fun parseNdefRecord(record: NdefRecord): NdefPayload {
        return when (record.tnf) {
            NdefRecord.TNF_WELL_KNOWN -> {
                when {
                    record.type.contentEquals(NdefRecord.RTD_URI) -> {
                        val uri = record.toUri()?.toString() ?: String(record.payload).drop(1)
                        NdefPayload(
                            type = "URL",
                            content = uri,
                            isUrl = true,
                            isSuspicious = isSuspiciousUrl(uri.lowercase())
                        )
                    }
                    record.type.contentEquals(NdefRecord.RTD_TEXT) -> {
                        val payload = record.payload
                        val langCodeLength = payload[0].toInt() and 0x3F
                        val text = String(payload, langCodeLength + 1, payload.size - langCodeLength - 1)
                        NdefPayload(type = "Text", content = text)
                    }
                    record.type.contentEquals(NdefRecord.RTD_SMART_POSTER) -> {
                        NdefPayload(type = "Smart Poster", content = "Smart poster tag", isSuspicious = true)
                    }
                    else -> NdefPayload(type = "Data", content = String(record.payload))
                }
            }
            NdefRecord.TNF_ABSOLUTE_URI -> {
                val uri = String(record.type) + String(record.payload)
                NdefPayload(
                    type = "URL",
                    content = uri,
                    isUrl = true,
                    isSuspicious = isSuspiciousUrl(uri.lowercase())
                )
            }
            NdefRecord.TNF_EXTERNAL_TYPE -> {
                NdefPayload(type = "App Data", content = String(record.type))
            }
            NdefRecord.TNF_MIME_MEDIA -> {
                NdefPayload(type = String(record.type), content = "Media content (${record.payload.size} bytes)")
            }
            else -> NdefPayload(type = "Unknown", content = "Unrecognised data format")
        }
    }

    private fun isSuspiciousUrl(url: String): Boolean {
        val suspicious = listOf(
            "bit.ly", "tinyurl", "t.co", "goo.gl", "is.gd", "ow.ly",
            "login", "signin", "verify", "secure-", "account-",
            "paypal-", "amazon-", "bank-", "update-", ".tk", ".ml", ".cf", ".ga"
        )
        return suspicious.any { url.contains(it) }
    }

    companion object {
        private const val TAG = "NfcSecurity"
    }
}
