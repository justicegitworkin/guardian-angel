package com.safeharborsecurity.app.util

import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.safeharborsecurity.app.data.model.QrAnalysisResult
import com.safeharborsecurity.app.data.model.QrRiskLevel
import com.safeharborsecurity.app.data.model.QrType

class QrCodeAnalyzer(
    private val onQrCodeDetected: (QrAnalysisResult) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient()
    private var lastProcessedValue: String? = null

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    val rawValue = barcode.rawValue ?: continue
                    if (rawValue == lastProcessedValue) continue
                    lastProcessedValue = rawValue

                    val result = classifyBarcode(barcode, rawValue)
                    Log.d("QrCodeAnalyzer", "Detected QR: type=${result.qrType}, value=${rawValue.take(100)}")
                    onQrCodeDetected(result)
                    break
                }
            }
            .addOnFailureListener { e ->
                Log.w("QrCodeAnalyzer", "Barcode scanning failed: ${e.message}")
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun classifyBarcode(barcode: Barcode, rawValue: String): QrAnalysisResult {
        return when (barcode.valueType) {
            Barcode.TYPE_URL -> {
                val url = barcode.url?.url ?: rawValue
                val warning = getUrlWarning(url)
                QrAnalysisResult(
                    rawValue = url,
                    qrType = QrType.URL,
                    instantWarning = warning,
                    needsClaudeAnalysis = true
                )
            }
            Barcode.TYPE_WIFI -> {
                val ssid = barcode.wifi?.ssid ?: "Unknown"
                val encType = barcode.wifi?.encryptionType
                val warning = if (encType == Barcode.WiFi.TYPE_OPEN)
                    "This WiFi network has no password. Anyone nearby can see what you do on it."
                else null
                QrAnalysisResult(
                    rawValue = "WiFi: $ssid",
                    qrType = QrType.WIFI,
                    instantWarning = warning,
                    needsClaudeAnalysis = encType == Barcode.WiFi.TYPE_OPEN
                )
            }
            Barcode.TYPE_EMAIL -> QrAnalysisResult(
                rawValue = barcode.email?.address ?: rawValue,
                qrType = QrType.EMAIL,
                needsClaudeAnalysis = false
            )
            Barcode.TYPE_PHONE -> QrAnalysisResult(
                rawValue = barcode.phone?.number ?: rawValue,
                qrType = QrType.PHONE,
                needsClaudeAnalysis = false
            )
            Barcode.TYPE_SMS -> QrAnalysisResult(
                rawValue = "SMS to ${barcode.sms?.phoneNumber}: ${barcode.sms?.message}",
                qrType = QrType.SMS,
                instantWarning = "This QR code wants to send a text message. Check the number and message carefully.",
                needsClaudeAnalysis = true
            )
            Barcode.TYPE_GEO -> QrAnalysisResult(
                rawValue = rawValue,
                qrType = QrType.GEO,
                needsClaudeAnalysis = false
            )
            Barcode.TYPE_CONTACT_INFO -> QrAnalysisResult(
                rawValue = barcode.contactInfo?.name?.formattedName ?: rawValue,
                qrType = QrType.CONTACT,
                needsClaudeAnalysis = false
            )
            Barcode.TYPE_CALENDAR_EVENT -> QrAnalysisResult(
                rawValue = barcode.calendarEvent?.summary ?: rawValue,
                qrType = QrType.CALENDAR,
                needsClaudeAnalysis = false
            )
            else -> {
                val type = detectTextType(rawValue)
                QrAnalysisResult(
                    rawValue = rawValue,
                    qrType = type,
                    instantWarning = getTextWarning(rawValue, type),
                    needsClaudeAnalysis = type.riskLevel >= QrRiskLevel.MEDIUM
                )
            }
        }
    }

    private fun detectTextType(text: String): QrType {
        val lower = text.lowercase()
        return when {
            lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("www.") -> QrType.URL
            lower.startsWith("bitcoin:") || lower.startsWith("ethereum:") || lower.startsWith("btc:") -> QrType.CRYPTO
            lower.startsWith("market://") || lower.startsWith("play.google.com") -> QrType.APP_LINK
            lower.contains("pay") && (lower.contains("amount") || lower.contains("$") || lower.contains("usd")) -> QrType.PAYMENT
            else -> QrType.TEXT
        }
    }

    private fun getUrlWarning(url: String): String? {
        val lower = url.lowercase()
        return when {
            lower.contains("bit.ly") || lower.contains("tinyurl") || lower.contains("t.co") ||
            lower.contains("goo.gl") || lower.contains("is.gd") || lower.contains("ow.ly") ->
                "This is a shortened link. Safe Companion will check where it really goes."
            lower.contains("login") || lower.contains("signin") || lower.contains("verify") ||
            lower.contains("secure") || lower.contains("account") ->
                "This link may be asking for personal information. Let Safe Companion check it first."
            else -> null
        }
    }

    private fun getTextWarning(text: String, type: QrType): String? {
        return when (type) {
            QrType.CRYPTO -> "This QR code is related to cryptocurrency. Be very careful with crypto QR codes."
            QrType.PAYMENT -> "This QR code involves a payment. Make sure you know who you are paying."
            QrType.APP_LINK -> "This QR code wants to install an app. Only install apps you trust."
            else -> null
        }
    }

    fun reset() {
        lastProcessedValue = null
    }
}
