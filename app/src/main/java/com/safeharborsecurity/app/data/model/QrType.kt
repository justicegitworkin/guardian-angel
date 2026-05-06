package com.safeharborsecurity.app.data.model

enum class QrType(val displayName: String, val riskLevel: QrRiskLevel) {
    URL("Website Link", QrRiskLevel.CHECK),
    WIFI("WiFi Network", QrRiskLevel.MEDIUM),
    EMAIL("Email Address", QrRiskLevel.LOW),
    PHONE("Phone Number", QrRiskLevel.LOW),
    SMS("Text Message", QrRiskLevel.MEDIUM),
    GEO("Location", QrRiskLevel.LOW),
    CONTACT("Contact Card", QrRiskLevel.LOW),
    CALENDAR("Calendar Event", QrRiskLevel.LOW),
    TEXT("Plain Text", QrRiskLevel.LOW),
    PAYMENT("Payment Request", QrRiskLevel.HIGH),
    CRYPTO("Cryptocurrency", QrRiskLevel.HIGH),
    APP_LINK("App Download", QrRiskLevel.MEDIUM),
    UNKNOWN("Unknown Content", QrRiskLevel.MEDIUM);
}

enum class QrRiskLevel(val label: String) {
    LOW("Low Risk"),
    MEDIUM("Worth Checking"),
    HIGH("Be Careful"),
    CHECK("Needs Checking")
}
