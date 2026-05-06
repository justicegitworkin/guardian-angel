# Safe Companion — Google Play Policy Review Status

Snapshot of where the project sits versus Google Play's published policy
checklist, what was changed to get there, and what's still in your court.

---

## Bottom line

The technical security side (encryption, network, R8, integrity checks) is
already in good shape. After this round, the **policy** side should clear an
initial review with a properly filled Permissions Declaration and a public
privacy-policy URL. Realistic odds, fully prepared:

| State | Approx. odds of clearing first review |
|---|---|
| Before this sprint | 30–40% |
| After this sprint, all three companion docs filled in Play Console | 75–85% |
| With Play Integrity API + signing-cert SHA-256 wired | 85–90% |

Remaining risk is mostly judgemental — reviewers can still ask follow-up
questions on SMS or Notification Listener even when the justification is
solid. Plan for a one-round-of-questions iteration cycle.

---

## What changed in this sprint

### Permissions removed from the manifest

- `READ_CALL_LOG` — never actually read by code; was a string constant only.
- `ANSWER_PHONE_CALLS` — no `acceptRingingCall` / `endCall` callers exist.
- `BIND_ACCESSIBILITY_SERVICE` — replaced by `UsageStatsManager` polling
  (`PaymentAppMonitor`) for the same payment-reminder UX.

### Service removed

- `service/PaymentWarningAccessibilityService.kt` deleted.
- `res/xml/payment_warning_accessibility_config.xml` deleted.
- Manifest `<service>` block removed.

### New components

- `util/PaymentAppMonitor.kt` — UsageStats-based foreground-app poll,
  driven from `GuardianService`.
- `notification/NotificationHelper.showPaymentReminder()` replaces the
  accessibility-service notification.
- `ui/privacy/PrivacyPolicyScreen.kt` — in-app WebView (JS off, file/content
  access off) renders `assets/privacy_policy.html`.
- Settings → "Privacy & Legal" section with **View Privacy Policy** and
  **Our Privacy Promise** entries.

### Onboarding adjusted

- The "accessibility" step (in both `OnboardingScreen.kt` and
  `GuidedPermissionViewModel.kt`) is now a "usage_access" step that opens
  `Settings.ACTION_USAGE_ACCESS_SETTINGS`. Step copy reframes the feature as
  optional ("Skip if you do not use payment apps").
- Granted-check uses `AppOpsManager.OPSTR_GET_USAGE_STATS`.

### Documents written

- `app/src/main/assets/privacy_policy.html` — the canonical privacy policy.
- `PLAY_PERMISSIONS_DECLARATION.md` — paste-ready justifications for each
  sensitive permission, plus the Data Safety form answers.
- `PLAY_RELEASE_SIGNING.md` — keystore generation, signing-config wiring,
  AAB upload steps, CI hints.
- `app/TESTER_README.md` — already existed from the previous sprint.

### Build verified

- `./gradlew assembleStandardDebug` ✓
- `./gradlew bundleStandardRelease` ✓ produces a 19 MB `.aab`
- R8 obfuscation enabled, mapping.txt generated, no crashes from missing
  ProGuard rules.

---

## Manifest state (post-sprint)

Sensitive permissions still declared, with rationale:

| Permission | Why kept |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Claude / ElevenLabs / Google TTS / RSS feeds. |
| `SEND_SMS` | Family Safety Alerts only — recipients explicitly entered by user. |
| `READ_PHONE_STATE` | `CallDurationTracker` reads CALL_STATE transitions only. |
| `RECORD_AUDIO` | Voice chat / voicemail scan / ultrasonic Room Scan (all on-device). |
| `READ_CONTACTS` | Caller-ID lookup against device contacts; never uploaded. |
| `POST_NOTIFICATIONS` | Standard. |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_PHONE_CALL` + `FOREGROUND_SERVICE_SPECIAL_USE` | `CallOverlayService`, `GuardianService`, `PrivacyMonitorService`. SPECIAL_USE has the required `<property>` declaration. |
| `SYSTEM_ALERT_WINDOW` | Call overlay during incoming-call event only. |
| `WAKE_LOCK`, `VIBRATE` | Routine. |
| `USE_FULL_SCREEN_INTENT` | Optional "Grab my attention" alert tier — user-controlled. |
| `ACCESS_WIFI_STATE`, `ACCESS_FINE_LOCATION` | Required by Android to read SSID list during Room Scan; never stored. |
| `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` | Room Scan only. |
| `CAMERA` | QR scanner / Safety Checker photo / Mirror Check. |
| `NFC` | NFC tag warning ("Is This Safe?"). |
| `QUERY_ALL_PACKAGES` | App Checker + Privacy Monitor; on-device only. |
| `PACKAGE_USAGE_STATS` | Payment-app foreground poll only. |
| `REQUEST_DELETE_PACKAGES` | Launches system uninstall dialog from App Detail. |
| `RECEIVE_BOOT_COMPLETED` | `BootReceiver` restarts `GuardianService`. |

---

## Pre-submission checklist

- [ ] **Generate upload keystore** following `PLAY_RELEASE_SIGNING.md` §1.
- [ ] **Wire `signingConfigs`** per §2.
- [ ] **Capture upload cert SHA-256**, paste into `EXPECTED_SIGNATURE`
      BuildConfig field; rebuild AAB.
- [ ] **Host privacy policy** publicly. Easiest: drop
      `app/src/main/assets/privacy_policy.html` into a GitHub Pages repo;
      the URL goes into Play Console.
- [ ] **Fill Permissions Declaration** in Play Console using
      `PLAY_PERMISSIONS_DECLARATION.md`. Each sensitive permission has a
      pre-written justification block.
- [ ] **Fill Data Safety form** using the table in the same doc.
- [ ] **Record demo videos** for SEND_SMS and Notification Listener (the
      two reviewers most often want to see). Suggested clips are in the
      Permissions Declaration doc.
- [ ] **Content rating questionnaire** — answer "no" to all
      sex/violence/gambling questions; the only sensitive content is
      educational fraud awareness.
- [ ] **App category**: Tools or Lifestyle, not Communication (avoids
      default-SMS-handler comparison flag).
- [ ] **Target audience**: Adults (18+).

---

## Known follow-ups (not blockers, but worth scheduling)

1. **Real cert pin SHA-256** for `api.anthropic.com`, `api.elevenlabs.io`,
   `texttospeech.googleapis.com`. Currently `network_security_config.xml`
   enforces system trust anchors only. Pinning hardens further but pins
   need to be extracted from live endpoints.
2. **Play Integrity API** integration. Listed as Phase 8 in CLAUDE.md.
   Reviewers don't require it but it's expected for security-positioned
   apps.
3. **`@Suppress("DEPRECATION")` audit** — a handful of `GoogleSignIn` calls
   are deprecated as the library migrates to Credential Manager. Not a
   policy issue, but cleaning them avoids future warning noise.
4. **`SmsReceiver.kt`** — file still exists but unregistered. Either
   delete or add a TODO comment indicating it's intentionally inert.

---

## What reviewers will probably ask anyway

These are the typical follow-up questions even after a clean submission. Have
answers ready:

> **Q: Why does Safe Companion need to send SMS instead of using FCM?**
> A: Family contacts are people the user already trusts at a phone-number
> level. Requiring them to install Safe Companion to receive alerts would
> prevent them from being notified at all in real-world testing.

> **Q: Can the Notification Listener read messages from any app?**
> A: It only acts on packages in `SMS_APP_PACKAGES`,
> `SOCIAL_APP_PACKAGES`, `EMAIL_APP_PACKAGES`, and `CAMERA_APP_PACKAGES`.
> Other notifications are ignored. Read-only — never modified, dismissed,
> or impersonated.

> **Q: What happens to message text after it's analysed?**
> A: With no Claude API key configured: never leaves the device.
> With a key: snippets the on-device classifier marks "ambiguous" are sent
> to `api.anthropic.com` over TLS. Anthropic's policy retains them only
> within the request session. The original text isn't persisted on Safe
> Companion's side beyond the local Room database, which is encrypted and
> excluded from backup.
