package com.guardianangel.app.util

import java.text.SimpleDateFormat
import java.util.*

/** Extracts the first `{...}` JSON object from a string that may have surrounding prose. */
fun extractJson(text: String): String {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    return if (start != -1 && end != -1) text.substring(start, end + 1) else text
}

fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
