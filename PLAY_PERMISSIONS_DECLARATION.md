# Safe Companion — Play Console Permissions Declaration

This document collects the per-permission justifications you'll need to paste
into the Play Console at submission time. Each section corresponds to a Play
Console form field. Where Play asks for a screen recording, the suggested clip
is described too.

---

## Sensitive permissions

### `android.permission.SEND_SMS`

**Form field — "Justification":**

> Safe Companion is a scam-protection app for senior users. SEND_SMS is used
> only for the **Family Safety Alerts** feature: when the on-device scam
> classifier (or the user's optional Claude verification) flags an active call
> or message as a high-confidence scam, Safe Companion sends a one-line text to
> the family contacts the user has explicitly added in Settings. The user
> opts in to the feature, names each recipient, and can disable it at any
> time. SMS is never sent to any number not entered by the user in Settings,
> and the body is a fixed-template family alert (no marketing, no advertising).

**Form field — "Why no other API works":**

> We considered FCM push, but the family contact may not have Safe Companion
> installed (and shouldn't need to). SMS is the only delivery channel that
> reaches a phone number the user already trusts, with no extra setup on the
> recipient's side.

**Demo recording (suggested):**

1. Settings → Family Alerts → "Add family member" → enter name + your own number
> 2. Trigger a sample alert (long-press the Sample Card on the home screen) and
   show the SMS arriving on the recipient phone.
3. Show the toggle being turned off and the alert no longer firing.

---

### `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`

**Justification:**

> Safe Companion's core function is detecting scam SMS and email messages
> directed at senior users. The NotificationListenerService reads the
> incoming-message previews posted by the user's existing SMS / Messenger /
> email apps (Google Messages, Samsung Messages, WhatsApp, Gmail, etc.),
> classifies the body via an on-device scam detector, and warns the user when
> the message looks dangerous. Reading is the only operation performed —
> Safe Companion never dismisses, modifies, or impersonates other apps'
> notifications. Coverage is required because the legacy SMS-handler approach
> is no longer permissible to non-default-SMS apps and would require Safe
> Companion to replace the user's existing messages app, which we do not want.

**Demo recording:**

1. Settings → notification access → enable Safe Companion.
2. From a second phone, send the test message
   `"URGENT: your bank account has been suspended, click http://bank.tk/verify"`
   to the test phone.
3. Show the Safe Companion warning notification appearing within seconds, with
   the verdict and reasoning.

---

### `android.permission.SYSTEM_ALERT_WINDOW`

**Justification:**

> Used only by the Call Overlay feature. When `CallScreeningService` flags an
> incoming call as suspected scam, Safe Companion shows a small floating card
> at the top of the screen with the verdict and three buttons: Answer, Decline,
> Screen with Safe Companion. The overlay is only displayed during an
> incoming-call event and is removed when the call ends. It is never used for
> advertising, monetisation, or hijacking other apps.

---

### `android.permission.USE_FULL_SCREEN_INTENT`

**Justification:**

> Used only by the optional "Grab my attention" alert level (off by default).
> A senior user can opt into a full-screen alert specifically because a
> standard high-priority notification can be missed in real-world testing.
> Triggered only by `riskLevel == "SCAM"` or call screening verdicts of
> `SCAM` / `CRITICAL`. Never used for ads, app launches, or marketing. The
> user can downgrade this to a quiet notification (or off entirely) at any
> time in Settings → Alert Level.

---

### `android.permission.QUERY_ALL_PACKAGES`

**Justification:**

> Required by two safety features:
>
> 1. **App Safety Checker** — lets the user review every installed app's
>    permissions in plain English ("Can read your contacts", risk level: HIGH)
>    and decide whether to uninstall.
> 2. **Listening Shield Privacy Monitor** — scans the user's installed apps
>    for known mic-access patterns and surfaces a remediation guide.
>
> The package list is processed only on-device. It is never uploaded to a
> server and is not used for analytics. Both features are core to the app's
> stated purpose of protecting elderly users from on-device privacy/security
> risks.

---

### `android.permission.PACKAGE_USAGE_STATS`

**Justification:**

> Used only for the optional **Payment Safety Reminder** feature. When the
> user opens Venmo / Zelle / Cash App / PayPal / Google Wallet / Samsung
> Wallet, Safe Companion shows a 8-second reminder notification ("Only send
> money to people you know in real life"). Implementation: a 30-second poll
> from the foreground service checks the most recent foreground app via
> `UsageStatsManager.queryUsageStats`. We never read app contents, screen
> data, or anything beyond the package name of the app currently in the
> foreground. The user opts into the feature in onboarding and grants the
> permission via the system Settings page.
>
> Note: Safe Companion previously used an AccessibilityService for this
> feature. We removed it because Google Play correctly reserves accessibility
> services for apps whose core purpose is helping users with disabilities.

---

### `android.permission.REQUEST_DELETE_PACKAGES`

**Justification:**

> The App Safety Checker can recommend uninstalling an app the user no longer
> trusts. Tapping the "Uninstall" button launches the standard system delete
> dialog (`Intent.ACTION_DELETE`). Safe Companion never silently uninstalls
> anything; the user must confirm in the system UI.

---

### `android.permission.RECORD_AUDIO`

**Justification:**

> Three on-device features use the microphone, all opt-in:
>
> 1. Voice chat with the AI companion (only while the user holds/taps the mic
>    button).
> 2. Voicemail Scanner in "Is This Safe?" — user plays a voicemail aloud and
>    the app transcribes it locally for scam analysis.
> 3. Room Scanner ultrasonic sweep — records 5 seconds of room audio and runs
>    the Goertzel algorithm on-device to detect ultrasonic surveillance
>    signals.
>
> Audio is never sent off the device, never persisted to disk beyond the
> 5-second ultrasonic scan buffer, and never used for advertising.

---

### `android.permission.READ_PHONE_STATE`

**Justification:**

> Used by `CallDurationTracker` to detect when an in-progress call (already
> screened by `CallScreeningService` and tagged SCAM/SUSPICIOUS) crosses the
> 1-minute and 5-minute thresholds, so a Family Safety Alert can be sent if
> the user is talking to a known scam number for too long. We do not read the
> user's phone number, IMEI, IMSI, or any device identifier — only the
> CALL_STATE_OFFHOOK / CALL_STATE_IDLE transitions.

---

### Foreground service: `specialUse` subtype `security_monitoring`

**Justification (Special Use FGS review):**

> The `GuardianService` foreground service is required to keep three protective
> components running while the user is on the lock screen or in another app:
>
> - WiFi connection monitor (to warn on connection to an unsecured AP)
> - Call-duration tracker (to fire timed family alerts on suspicious calls)
> - Payment-app foreground poll (UsageStats-based reminder)
>
> None of the existing typed FGS values fit precisely (it is not a media
> session, location share, sync, or call). The `specialUse` subtype is
> declared per the Android 14 requirement.

---

## Restricted permissions intentionally NOT requested

These were removed from the manifest after audit because they were not actually
needed by the codebase. Documenting here in case Play tooling flags by association.

| Permission | Why we don't need it |
|---|---|
| `READ_CALL_LOG` | Call screening uses `CallScreeningService`, which provides call details directly without needing CallLog access. |
| `ANSWER_PHONE_CALLS` | The app surfaces a custom UI overlay during incoming calls but does not programmatically answer or end them; the user always confirms in the system dialer. |
| `BIND_ACCESSIBILITY_SERVICE` | The previous payment-warning Accessibility service was replaced by a `UsageStatsManager` poll (see PACKAGE_USAGE_STATS above). |
| `MANAGE_EXTERNAL_STORAGE` | Photos for the Safety Checker are accessed via `ACTION_GET_CONTENT` / `MediaStore` only. |
| `REQUEST_INSTALL_PACKAGES` | The app never installs other APKs. |
| `WRITE_SETTINGS` | Never modifies system settings programmatically. |

---

## Data Safety form (Play Console answers)

Use these answers verbatim in the Data Safety section.

### Data collected

| Data type | Collected? | Why | Optional? |
|---|---|---|---|
| Name | No | – | – |
| Email address | No | – | – |
| User IDs | No | – | – |
| Address / Location precise | No | We use ACCESS_FINE_LOCATION only to enable WiFi scan results; we never read or store the location. | n/a |
| Phone number | No | – | – |
| Photos | No (optional) | If the user picks a photo for the Safety Checker, the image is sent to Anthropic for analysis only with the user's API key. Not stored on our side. | Yes |
| Voice recordings | No | Audio captured for voice chat / voicemail scan / ultrasonic sweep is processed locally and never stored or transmitted. | – |
| Calendar events | No | – | – |
| Contacts | No | Contact names are read on-device for caller-ID lookup; never uploaded. | – |
| Call log | No | Not collected. CallScreeningService surfaces call details only. | – |
| SMS messages | **Yes (optional)** | The user can opt in to add a Claude API key. With a key configured, ambiguous SMS bodies are sent to Anthropic for verification. With no key, no SMS data leaves the device. | Yes |
| Email messages | **Yes (optional)** | Same pattern as SMS. | Yes |
| Files / docs | No | – | – |
| Installed apps | No | List is read on-device for App Checker; never uploaded. | – |
| App interactions | No | – | – |
| Diagnostics / crash logs | No | No analytics or crash reporters integrated. | – |
| App performance / errors | No | – | – |
| Device or other IDs | No | – | – |

### Data shared with third parties

| Recipient | Data | Purpose | User consent |
|---|---|---|---|
| Anthropic (Claude API) | SMS / email / image content of messages the on-device classifier flagged as ambiguous | Scam verification | Yes — only sent if the user manually configures a Claude API key in Settings. |
| ElevenLabs | Reply text from the AI companion | Voice synthesis | Yes — only if the user configures an ElevenLabs key. |
| Google Cloud TTS | Reply text from the AI companion | Voice synthesis (fallback tier) | Yes — only if the user configures a Google API key. |

### Security practices

- Data is encrypted in transit — **YES** (TLS enforced via `network_security_config.xml`, `cleartextTrafficPermitted=false`).
- You can request data deletion — **YES** (Settings → Clear All Data; uninstalling removes everything; no server-side storage).
- Data follows Play Families policy — n/a (app is 18+).
- Independent security review — **NO**.
- Encrypted at rest — **YES** (Room DB; API keys encrypted with AES/GCM in AndroidKeyStore).

---

## App content rating

- Target audience: Adults (18+), specifically senior users.
- No violence, sexual content, profanity, or simulated gambling.
- App contains references to fraud / scam content only as warnings (educational).

---

## Sensitive content disclosure (in-app)

The app stores and displays simulated scam SMS and emails (3 sample alerts on
first launch, marked `[SAMPLE]` in their reason text) to demonstrate the
verdict UI. This is non-real demonstration content; it can be cleared via
Settings → Clear All Data.
