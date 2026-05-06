package com.safeharborsecurity.app.util

import android.os.Build
import com.safeharborsecurity.app.BuildConfig
import java.net.URLEncoder

/**
 * Substitutes Safe Companion's placeholder tokens into the developer-supplied
 * Google Form URL so beta-feedback responses arrive with app/device context
 * already filled in.
 *
 * How to set up your Google Form (one-time, by the developer):
 *
 *   1. Create a Google Form with whatever feedback fields you want, plus
 *      hidden device-info fields. Recommended set:
 *        - Linear scale 1-5 ("Overall rating")
 *        - Long-text "What's working well"
 *        - Long-text "What's not working"
 *        - Long-text "Anything else"
 *        - Short-text "App version"
 *        - Short-text "Device"
 *        - Short-text "Android version"
 *        - Short-text "Build"
 *
 *   2. Open the form, click the three-dot menu → "Get pre-filled link." Type
 *      the literal placeholder strings APP_VERSION_HERE, DEVICE_HERE,
 *      OS_VERSION_HERE, BUILD_FLAVOR_HERE in the matching device-info
 *      fields. Click "Get link."
 *
 *   3. Google produces a URL like:
 *        https://docs.google.com/forms/d/e/<id>/viewform?usp=pp_url
 *           &entry.1234567=APP_VERSION_HERE
 *           &entry.7654321=DEVICE_HERE
 *           &entry.5556667=OS_VERSION_HERE
 *           &entry.8889990=BUILD_FLAVOR_HERE
 *      Drop that whole URL into local.properties as
 *        safe.companion.feedback.form.url=<that URL>
 *
 *   4. Rebuild. The button at the bottom of the home screen swaps the
 *      placeholders for the live values per device when the user taps it.
 */
object FeedbackFormUrlBuilder {

    fun build(template: String): String {
        if (template.isBlank()) return template
        return template
            .replace("APP_VERSION_HERE", encode(BuildConfig.VERSION_NAME))
            .replace("DEVICE_HERE", encode("${Build.MANUFACTURER} ${Build.MODEL}"))
            .replace("OS_VERSION_HERE", encode("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"))
            .replace("BUILD_FLAVOR_HERE", encode(
                if (BuildConfig.FLAVOR.isBlank()) BuildConfig.BUILD_TYPE
                else "${BuildConfig.FLAVOR}-${BuildConfig.BUILD_TYPE}"
            ))
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8")
}
