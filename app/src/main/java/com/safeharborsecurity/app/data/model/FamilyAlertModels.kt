package com.safeharborsecurity.app.data.model

enum class AlertTrigger(val displayName: String, val urgency: AlertUrgency, val messageTemplate: String) {
    SUSPICIOUS_CALL_1_MIN(
        "Suspicious call (1 min)",
        AlertUrgency.STANDARD,
        "{name} has been on a suspicious call for over a minute. You may want to check in."
    ),
    SUSPICIOUS_CALL_5_MIN(
        "Suspicious call (5 min)",
        AlertUrgency.URGENT,
        "{name} has been on a suspicious call for over 5 minutes. Please call them now."
    ),
    GIFT_CARD_PURCHASE_DETECTED(
        "Gift card purchase detected",
        AlertUrgency.STANDARD,
        "{name} may be buying a gift card as part of a scam. Please check in right away."
    ),
    WIRE_TRANSFER_DETECTED(
        "Wire transfer detected",
        AlertUrgency.URGENT,
        "{name} may be making a wire transfer as part of a scam. Please call immediately."
    ),
    CRYPTO_DETECTED(
        "Cryptocurrency payment detected",
        AlertUrgency.URGENT,
        "{name} may be sending cryptocurrency as part of a scam. Please call immediately."
    ),
    PANIC_BUTTON_PRESSED(
        "Panic button pressed",
        AlertUrgency.URGENT,
        "{name} pressed the emergency help button in Safe Companion. Please contact them now."
    ),
    DANGEROUS_SMS_RESPONDED(
        "Responded to dangerous SMS",
        AlertUrgency.STANDARD,
        "{name} may have responded to a suspicious text message. You may want to check in."
    ),
    DANGEROUS_EMAIL_CLICKED(
        "Clicked dangerous email link",
        AlertUrgency.STANDARD,
        "{name} may have opened a suspicious email link. You may want to check in."
    ),
    DANGEROUS_QR_SCANNED(
        "Scanned dangerous QR code",
        AlertUrgency.STANDARD,
        "{name} scanned a QR code that Safe Companion flagged as suspicious."
    ),
    DANGEROUS_URL_VISITED(
        "Visited dangerous URL",
        AlertUrgency.STANDARD,
        "{name} visited a website that Safe Companion flagged as suspicious."
    ),
    NO_CHECKIN_RESPONSE(
        "Missed daily check-in",
        AlertUrgency.STANDARD,
        "{name} has not checked in today on Safe Companion. Please give them a call."
    )
}

enum class AlertUrgency(val label: String) {
    STANDARD("Standard"),
    URGENT("Urgent")
}

enum class AlertLevel(val displayName: String, val minConfidence: Int) {
    ALL("All alerts", 0),
    HIGH_ONLY("High risk only", 70),
    CRITICAL("Critical only", 90)
}

data class FamilyAlertEvent(
    val trigger: AlertTrigger,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val contactName: String = "",
    val contactNumber: String = "",
    val delivered: Boolean = true
)
