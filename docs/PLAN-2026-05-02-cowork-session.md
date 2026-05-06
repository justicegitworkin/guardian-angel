# Safe Companion — Plan from 2026-05-02 Cowork session

This document captures the answers, specs, and Claude Code prompts for the seven items raised in the 2026-05-02 working session. Items 1 and 1.5 were fixed directly in code (see "Code changes already made" at the bottom). The rest are documented here for Claude Code to execute.

Build order recommendation: 5 (API-key optional) → 6 (GitHub) → 4 (weekly email) → 3 (AirTags) → 2 (text monitoring research already decided, ready to spec) → 7 (developer news scanner, already scheduled in Cowork separately).

---

## Item 2 — Text monitoring approach (decided): Screenshot + local SLM

**Decisions captured this session.** Architectural direction: Screenshot + local SLM. Privacy posture: on-device first, cloud opt-in. Target audience: AARP-style older adults.

This is the single highest-leverage decision: it removes the scary `NotificationListenerService` and `READ_SMS` permissions entirely (the two that AARP testers were most uncomfortable with) and replaces them with a one-time MediaProjection consent that Android shows as a dialog the user actually understands ("Safe Companion will start capturing everything that's displayed on your screen").

### How it works (architecture)

The flow is three layers, each a clean module:

1. **Capture layer** — A foreground service holds a `MediaProjection` token granted once by the user via the system consent dialog. The service registers an `ImageReader` and grabs a frame whenever its trigger condition fires. No camera, no notification listener, no SMS permission.

2. **Trigger layer** — Frame capture is *event-driven*, not constant. Three triggers, in priority order:
   - **Foreground app changes** to a known messaging app (Messages, WhatsApp, Telegram, Signal, Messenger, etc.) — capture once, then again 4 seconds later in case the user is still scrolling.
   - **Notification posted** by a known messaging app (using `UsageStatsManager` + `RECEIVER_NOT_EXPORTED` broadcast — *not* NotificationListener). Capture immediately.
   - **User long-presses** the persistent Safe Companion notification's "Scan this screen" action. Capture once.
   
   This keeps capture rate at roughly 0–10 frames per day for a typical user, not continuous.

3. **Analysis layer** — Each captured frame goes through:
   - **OCR** via on-device ML Kit Text Recognition (`com.google.mlkit:text-recognition:16.0.1`). No network, no API key.
   - **SLM classification** via the existing `OnDeviceScamClassifier` (Feature 62, already DONE). Pass the OCR text + sender heuristics (top of screen often shows the sender name).
   - **If verdict is SUSPICIOUS or DANGEROUS** → fire the existing alert pipeline, save to Room as a `SafetyCheckResultEntity` with `contentType = "SCREENSHOT_SMS"`, optionally trigger Family Alerts.
   - **If verdict is ambiguous AND user has opted into cloud second-opinion** → send the OCR text only (never the screenshot bytes) to Claude Haiku via the existing `HybridAnalysisRepository`.

The screenshot bytes are *deleted immediately* after OCR. Only the extracted text + verdict + timestamp survive in Room. This is the privacy story: we ran your eyes over your screen for two seconds, found no scam, threw the picture away.

### Permissions (the headline win)

Removed entirely:
- `BIND_NOTIFICATION_LISTENER_SERVICE`
- `READ_SMS`, `RECEIVE_SMS`
- `READ_MMS`

Added:
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PROJECTION` (Android 14+) — already covered by existing guardian service.
- `POST_NOTIFICATIONS` — already requested.
- The MediaProjection consent itself is *not* a manifest permission — it's a runtime intent dialog the user accepts once.

`UsageStatsManager` is still used (already in the app for App Safety Checker), so no new "scary" special permission.

### Files to create / modify

```
app/src/main/java/com/safecompanion/app/
├── service/
│   ├── ScreenScanService.kt          NEW — foreground service, holds MediaProjection
│   ├── ScreenScanTriggerReceiver.kt  NEW — broadcasts foreground app changes
│   └── ScreenScanWorker.kt           NEW — OCR + SLM pipeline (CoroutineWorker)
├── data/
│   └── repository/
│       └── ScreenScanRepository.kt    NEW — orchestration
├── ui/
│   └── settings/
│       └── ScreenMonitorScreen.kt     NEW — opt-in toggle, "Why we use this" copy
└── util/
    └── MessagingAppDetector.kt        NEW — package allowlist + foreground detection
```

### Settings UX (AARP-aware)

Off by default. Settings → Privacy → "Watch for scams in my text messages." Tapping the toggle shows this copy verbatim before the consent dialog, in 18sp:

> Safe Companion can watch your texts for scams without reading them.
>
> When a text comes in, we'll take a quick picture of your screen, look at the words, and tell you if it might be a scam. The picture is thrown away right after.
>
> Nothing leaves your phone unless you ask us to double-check with our cloud helper.

Then the system MediaProjection dialog. On grant → enable. On deny → toggle stays off, show "Maybe later" snackbar, no error.

### Honest limits to disclose

- Doesn't see encrypted messages we never display (Signal disappearing messages after timeout, etc.).
- Doesn't see messages received while screen is off and never opened.
- OCR accuracy degrades for non-standard fonts or screenshot-of-screenshot RCS messages.
- Stops working if the user revokes the MediaProjection token (Android sometimes does this automatically after a few days; we re-prompt with a dismissible banner).

### Why this beats the alternatives

| Approach | Pros | Cons |
|---|---|---|
| Manual share-sheet only | Zero permissions | User has to remember to use it |
| Become default SMS app (TextNow-style) | Full message access, normal SMS permissions | AARP users won't switch SMS apps; huge UX lift; Play Store policy review for SMS-default apps |
| NotificationListener (current) | Background, real-time | Scary system dialog, "denied access" message, half of test users bail |
| **Screenshot + local SLM (chosen)** | Familiar consent dialog, privacy-positive story, no SMS permission | OCR latency ~300ms; doesn't see un-displayed messages |

### Claude Code prompt to execute

```
Read CLAUDE.md and docs/PLAN-2026-05-02-cowork-session.md (Item 2 section).
Implement the screenshot + local SLM text monitoring feature.

Build order:
1. Add MessagingAppDetector with package allowlist (Messages, WhatsApp, Signal,
   Telegram, Messenger, Google Messages, Samsung Messages, RCS).
2. Add ScreenScanService as a foreground service that requests MediaProjection.
3. Add OCR pipeline using ML Kit Text Recognition (16.0.1).
4. Wire OCR output to OnDeviceScamClassifier (Feature 62).
5. Add ScreenMonitorScreen settings UI with opt-in toggle and consent copy.
6. Remove NotificationListener-based SMS reading paths from manifest and code.
7. Run ./gradlew assembleBetaDebug. Fix all compile errors. Report APK path.

Do not enable cloud second-opinion by default. Opt-in only, separate toggle.
```

---

## Item 3 — AirTag / tracker detection in "Is This Safe?"

**Yes, this is feasible on Android.** Apple opened the AirTag advertising protocol after EFF/regulator pressure, and Android 12+ has native support via Google's "Find My Device" unwanted-tracker alerts. We can also scan ourselves via BLE for finer control.

### Two-tier approach

**Tier 1 — Manual scan in "Is This Safe?":** A new card called "Scan for trackers nearby." Tapping it does a 30-second BLE scan and reports any trackers it sees with signal strength as a proximity hint. This requires `BLUETOOTH_SCAN` and `ACCESS_FINE_LOCATION` (already granted for Feature 47).

**Tier 2 — Always-on alert:** An optional `AlwaysOnTrackerWorker` (`PeriodicWorkRequest` every 15 minutes — minimum WorkManager allows) plus a low-priority foreground service that holds a low-power BLE scan (`SCAN_MODE_LOW_POWER`). When a tracker is detected following the user (i.e., the same device MAC seen in two scans more than 10 minutes apart and >100m of GPS movement between them), fire a notification.

The "always on" mode runs the scan every 15 minutes; this is enough to catch a stalker AirTag inside a backpack or car within an hour of separating from its owner. Apple's own iOS detection takes about 8–24 hours, so 15-minute intervals is meaningfully better.

### What we can detect

Reliable (BLE advertising signature, no decryption needed):
- **Apple AirTag** — `0x004C` manufacturer ID + 25-byte payload + nearby/lost mode byte
- **Apple FindMy network** (any item using FindMy: Chipolo Card, VanMoof bikes, etc.) — same `0x004C` ID, different payload type
- **Samsung SmartTag / SmartTag+** — service UUID `0xFD5A`
- **Tile** — service UUID `0xFEED`
- **Chipolo (non-Apple)** — service UUID `0xFE9F`
- **Pebblebee** — service UUID `0xFE74`
- **Eufy SmartTrack** — service UUID `0xFEAA` (Eddystone, also used by other things)

Less reliable (require behavioural inference):
- Generic BLE devices that move with you for hours but aren't paired to your phone
- Hidden Bluetooth audio-bug devices (covered by existing Feature 47 — Hidden Device Scanner)

### "Is this following me?" heuristic

A tracker is *suspicious* (vs. just "nearby") only if it meets all three:
1. Detected in ≥ 2 scans separated by ≥ 10 minutes,
2. AND the user moved ≥ 100m between those scans (so it's not just your own neighbour's tag),
3. AND the device is not in the user's "known trackers" allowlist (we let users mark their own AirTags as safe).

This matches Apple's own algorithm closely.

### Files to create

```
app/src/main/java/com/safecompanion/app/
├── service/
│   ├── TrackerScanService.kt              NEW — foreground service, BLE scan
│   ├── AlwaysOnTrackerWorker.kt           NEW — 15-min PeriodicWorkRequest
│   └── TrackerNotificationBuilder.kt      NEW — "A tracker may be following you"
├── data/
│   ├── local/entity/
│   │   └── DetectedTrackerEntity.kt       NEW — Room entity (mac, type, firstSeen, lastSeen, locations)
│   ├── repository/
│   │   └── TrackerRepository.kt           NEW
│   └── ble/
│       └── TrackerBleParser.kt            NEW — manufacturer/service UUID parsing
├── ui/
│   ├── safety/
│   │   └── TrackerScanScreen.kt           NEW — manual scan + results
│   └── settings/
│       └── TrackerAlertsScreen.kt         NEW — always-on toggle + known trackers
```

### Permissions

Already in manifest: `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`. Adding:
- `ACCESS_BACKGROUND_LOCATION` — *only if* user enables always-on mode. We must request this with explicit copy: "To check if a tracker is following you, Safe Companion needs to see your location even when the app isn't open. We never share or store your exact location."

### UI copy

In "Is This Safe?": new card titled "Check for trackers nearby" with body "Scan for AirTags, Tiles, and other tracking devices that might be hidden in your bag or car." Result screen lists any trackers found with a coloured chip (RED if suspicious by the heuristic, YELLOW if seen but not following, GREEN if "no trackers detected").

In Settings → "Alert me if there's a tracker nearby" toggle. When enabled, show the always-on caveat: "Safe Companion will check every 15 minutes. This uses about 1% of your battery per day."

### Claude Code prompt

```
Read CLAUDE.md and docs/PLAN-2026-05-02-cowork-session.md (Item 3 section).
Implement AirTag/tracker detection.

Build order:
1. TrackerBleParser (manufacturer 0x004C, service UUIDs FD5A, FEED, FE9F, FE74).
2. DetectedTrackerEntity Room entity + DAO + migration (DB version bump).
3. TrackerRepository orchestration.
4. TrackerScanService (foreground) + manual TrackerScanScreen card in
   IsThisSafeScreen.
5. AlwaysOnTrackerWorker — 15-min PeriodicWorkRequest, runs the
   "is-following-me" heuristic.
6. TrackerAlertsScreen settings UI with toggle and known-trackers allowlist.
7. ACCESS_BACKGROUND_LOCATION with custom rationale dialog.
8. assembleBetaDebug, fix errors, report APK path.

Add unit tests for TrackerBleParser (sample byte arrays for each tracker type).
Add unit tests for the "following me" heuristic.
```

---

## Item 4 — Weekly email summary (real email, not just notification)

**Existing state:** `WeeklyEmailSummaryWorker` (TaskG, marked DONE) only summarises EMAIL alerts and posts a phone notification. It does *not* email the user or family. We need to extend it.

### Required changes

**Scope expansion** — include all detection types: SMS, calls, emails, URLs, app installs, voicemails, hidden-device scans, tracker alerts. Group by category in the summary.

**Send actual email** — three implementation options, ranked:

1. **JavaMail (`com.sun.mail:android-mail` + `com.sun.mail:android-activation`)** — sends directly from the device via the user's existing Gmail (uses the OAuth token already obtained for Gmail inbox scanning, Feature 36). Pros: no third-party service, works offline on cell data. Cons: tied to Gmail.

2. **SendGrid HTTP API** — clean REST API. Pros: works with any sender domain. Cons: adds an API key; costs money at scale (~$15/month for 50k emails); we'd be the sender, which might trigger user spam filters.

3. **`Intent.ACTION_SENDTO` with `mailto:`** — opens user's default mail app pre-filled. Pros: zero permissions, zero network. Cons: requires the user to tap "send" each week — defeats the "background summary" goal.

**Recommendation:** Option 1 (JavaMail via Gmail OAuth) for users who've connected Gmail. For users who haven't, fall back to a "tap to email" notification that opens their default mail app. Don't add SendGrid.

**PII redaction.** The summary must not include:
- Phone numbers (replace with "an unknown caller" or "your bank's number")
- Sender names from contacts (replace with generic "a person not in your contacts")
- URLs (replace with "a suspicious link")
- Money amounts mentioned in scam texts (replace with "money")
- Any text excerpt longer than 8 words (just describe the category)

### Email format (markdown rendered to HTML)

```
Subject: Your weekly Safe Companion report — May 4-10

Hi Steve,

Here's what Safe Companion did for you this week:

🛡️ Threats stopped
• Tuesday, 2:14 PM — Blocked a phishing text pretending to be your bank.
• Wednesday, 9:03 AM — Warned you about a fake delivery notification.
• Saturday, 6:47 PM — Detected a robocall claiming to be the IRS.

⚠️ Things to keep an eye on
• 3 emails this week looked suspicious. They were marked but not blocked.
• A new app you installed (FreeWeatherPro) had unusual permissions. You
  uninstalled it after we asked.

✓ All clear
• 24 emails checked, looked safe
• 8 phone calls screened, all from your contacts
• 12 websites scanned via "Is This Safe?", all green

Total this week: 47 things checked, 4 threats stopped.

Your data stays on your phone. This summary doesn't include the actual
messages, names, or numbers — just what happened and what we did.

— Safe Companion
```

The HTML version uses inline CSS, no images, large readable fonts (16px body, 18px headers), and a single CTA button at the bottom: "Open Safe Companion."

### Family copy

Same template, with "Hi [family member name]" and a slightly softer subject line: "Steve's weekly Safe Companion report." Family member must have been added in Family Alerts (Feature 50) and their preference set to "weekly summaries". Same redaction rules apply.

### Files to modify / create

```
service/WeeklyEmailSummaryWorker.kt        MODIFY — broaden scope, send email
service/WeeklyDigestComposer.kt            NEW — markdown + HTML composer
service/EmailDispatcher.kt                 NEW — JavaMail wrapper
data/repository/WeeklyDigestRepository.kt  NEW — pulls from all alert sources
ui/settings/WeeklyReportScreen.kt          NEW — toggle + family CC list + preview
data/datastore/UserPreferences.kt          MODIFY — add weeklyReportEnabled,
                                                    weeklyReportFamilyEnabled
```

### Claude Code prompt

```
Read CLAUDE.md and docs/PLAN-2026-05-02-cowork-session.md (Item 4 section).
Extend the existing WeeklyEmailSummaryWorker to cover all detection types
(SMS, calls, emails, URLs, apps, voicemails, hidden devices, trackers) and
send an actual email — not just a phone notification.

Build order:
1. WeeklyDigestRepository — pulls last 7 days from AlertRepository, CallRepository,
   AppCheckerRepository, SafetyCheckResultEntity, plus the new Tracker
   detections.
2. WeeklyDigestComposer — renders markdown + HTML with PII redaction (see spec).
3. EmailDispatcher — JavaMail via Gmail OAuth token (re-use Feature 36 OAuth).
   Fall back to ACTION_SENDTO intent if no Gmail.
4. Modify WeeklyEmailSummaryWorker to call composer + dispatcher.
5. WeeklyReportScreen — settings UI: toggle "Email me a weekly report",
   sub-toggle "Also email my family", preview button that shows last week's
   redacted summary.
6. Add unit tests for the redaction rules (no phone numbers, no names, no
   URLs, no money amounts, no text > 8 words).

Run assembleBetaDebug. Report APK path.
```

---

## Item 5 — Why the Anthropic API key is needed on startup, and how to make it optional

### Why it's currently needed

The on-device SLM (Feature 62, `OnDeviceScamClassifier`) is *already implemented* and `HybridAnalysisRepository` (Feature 63) routes through it first. So architecturally, the cloud key is already optional. The reason it *feels* required is that several call-sites bypass the hybrid repository and check `apiKey.isBlank()` directly, then refuse to do anything if it's blank:

- `OnboardingScreen.kt:1335` — Continue button literally disabled until a key is entered
- `SafeHarborChatViewModel.kt:855` — Chat refuses to respond
- `SafetyCheckerViewModel.kt` — Multiple paths block on missing key
- `VoicemailScannerViewModel.kt:131` — Voicemail check blocks
- `AppDetailViewModel.kt:66` — App scan blocks

### Why we need *any* cloud at all

Three reasons, in order of importance:

1. **Quality on ambiguous cases.** The local SLM is fast and free, but it's a small model — roughly comparable to a 2024-era distilled BERT on an embedding task. For clearly-bad inputs ("Send a $500 Apple gift card to claim your prize") it's 95%+ accurate. For sophisticated phishing it's only ~70%. Claude Haiku is 95%+ on those cases. The hybrid repo's whole point is "use local, fall back to cloud only when local is unsure."

2. **Voice agent quality.** The conversational chat ("Is the IRS really calling me?", "What should I do if I clicked a link?") needs an LLM. A 1B-parameter on-device model can't reasonably do open-ended dialogue. We have two options here: keep cloud Claude for chat only, or replace chat with a scripted FAQ/decision-tree (we already have one — `Daily Safety Tip`, `Scam Coaching`).

3. **Personas with natural voice.** Grace/James/Sophie/George rely on Anthropic for the conversation logic. Without it, they become canned response readers.

### How to make the key truly optional

Two-stage rollout:

**Stage 1 — Make startup optional (small change, ~2 hours):**
- `OnboardingScreen.kt:1335` — change `enabled = apiKey.isNotBlank()` to `enabled = true`. Add a Skip text button under the input field: "I'll add this later — just use on-device protection".
- `SettingsScreen.kt` — add a banner at the top of the API Key section: "Working with on-device protection only. Add a key for higher-accuracy analysis."

**Stage 2 — Route every analysis call through HybridAnalysisRepository (~1 day):**
- `SafeHarborChatViewModel.kt:855` — when API key is missing, route to a *local responder* that uses canned scam-coaching responses keyed off the user's question via simple intent detection. Show "Working in offline mode — answers may be less detailed" subtext.
- `SafetyCheckerViewModel.kt` — replace direct `apiKey.isBlank()` checks with `hybridRepo.analyzeText(...)`. The hybrid repo handles the "no key" case cleanly.
- `VoicemailScannerViewModel.kt:131` — same.
- `AppDetailViewModel.kt:66` — same.
- `RemediationSyncWorker.kt`, `ScamTipWorker.kt`, `HaveIBeenPwnedWorker.kt`, `AppCheckWorker.kt`, `CallOverlayService.kt`, `SmsReceiver.kt`, `SafeHarborNotificationListener.kt` — these already silently no-op when key is missing. They're fine as-is, but we should add a one-line log: "Skipping cloud analysis — running on-device only."

### What the user sees in offline mode

- "Is This Safe?" still works for clear scams (>90% of them) using the local model.
- Chat ("Hey Grace, what should I do?") gives a canned but helpful response from a small decision tree, with a banner: "Tap here to add a Claude key for natural conversation."
- Weekly email still works — it's just a summary of detections, doesn't need cloud.
- Voice in/out still works (Android TTS at minimum, ElevenLabs if that key is set).

### Privacy story (the marketing line)

> Safe Companion runs on your phone. Without an API key, nothing about your messages, calls, or screen ever leaves your device. Add a key to unlock more accurate analysis — we'll only send the suspicious bits, never your contacts.

### Claude Code prompt

```
Read CLAUDE.md and docs/PLAN-2026-05-02-cowork-session.md (Item 5 section).
Make the Anthropic API key fully optional.

Stage 1 (do this first):
1. OnboardingScreen.kt:1335 — Continue button always enabled. Add skip text
   button with offline-mode copy.
2. Add "On-device only" banner at top of Settings → API Key section.

Stage 2:
3. SafeHarborChatViewModel.kt — when key missing, route to local responder
   (intent detection over the user's question, return one of ~30 canned
   responses keyed off scam types).
4. SafetyCheckerViewModel, VoicemailScannerViewModel, AppDetailViewModel —
   replace direct apiKey checks with hybridAnalysisRepository calls.
5. Add "Working in offline mode" subtext where applicable.

Run assembleBetaDebug. Test:
- App is fully usable with empty key.
- All analysis paths return a verdict (might be lower confidence).
- Banner appears in Settings.

Report APK path.
```

---

## Item 6 — GitHub repo state (more urgent than you thought)

**What I found when I looked at the repo this session:**

1. **You already have a GitHub repo** at `https://github.com/justicegitworkin/guardian-angel.git` — `origin/master` exists and there are 2 commits there.
2. **The GitHub copy is months out of date.** The remote contains the original *Guardian Angel* / `com.guardianangel.app` package layout. Everything you've done since the rename to *Safe Companion* / `com.safecompanion.app` is sitting in your working tree as **uncommitted, unpushed local changes** (60+ deleted files in the old package, the entire new `com.safeharborsecurity.app` tree untracked, plus all the docs and assets).
3. **There is a GitHub Personal Access Token leaked into `.git/config`** as part of the origin URL: `https://justicegitworkin:ghp_LMzeG…@github.com/justicegitworkin/guardian-angel.git`. That PAT is sitting in plaintext on the file system. Anyone who gets the `.git` folder gets the token. **Rotate it now.**
4. **`.gitignore` was thin.** I rewrote it this session to cover keystores, `google-services.json`, build outputs, IDE state, and Claude local settings. (The current `app/google-services.json` is only 366 bytes — a placeholder, not real Firebase keys, so nothing's leaked yet.)

### Cleanup steps in order (run these locally — I won't run them for you because I'd rather you eyeball the diff)

```bash
# 1. ROTATE THE LEAKED PAT FIRST. Go to:
#    https://github.com/settings/tokens
#    Find the token starting with ghp_LMzeG... and Revoke it.
#    Then create a new token, fine-grained, scoped to just this repo,
#    with Contents: Read/Write permission. Keep it somewhere safe (a
#    password manager — not in .git/config).

# 2. Re-set the origin URL WITHOUT the embedded PAT. After this you'll
#    be prompted for credentials on each push, OR set up the gh CLI / a
#    git credential helper that stores the new PAT outside .git/config.
cd C:\Users\steve\guardian-angel
git remote set-url origin https://github.com/justicegitworkin/guardian-angel.git

# 3. (Optional but recommended) Authenticate with the gh CLI so Git uses
#    its credential helper instead of needing a PAT in the URL:
#    https://cli.github.com — install it, then:
#    gh auth login

# 4. Confirm .gitignore now covers secrets (this session updated it):
git status                # should show .gitignore as modified
git diff .gitignore       # eyeball the new ignore rules
git add .gitignore
git commit -m "Harden .gitignore for keystores, google-services.json, build artifacts"

# 5. Stage the rename and the new code in chunks so the diff is reviewable.
#    First, the deletions of the old com.guardianangel.app package:
git add -u  app/src/main/java/com/guardianangel/
git commit -m "Remove old com.guardianangel.app package (renamed to safeharborsecurity)"

# 6. Then add the new package:
git add app/src/main/java/com/safeharborsecurity/
git commit -m "Add Safe Companion implementation (com.safeharborsecurity.app)"

# 7. Then the rest of the changes:
git add -A
git commit -m "Update build files, manifest, resources, assets, docs"

# 8. Push. If you've revoked the leaked PAT and re-set origin per step 2,
#    git will prompt for the new credentials.
git push origin master

# 9. Optional: rename branch to `main` to match GitHub default:
git branch -m master main
git push origin main
git push origin --delete master
# Then on GitHub, Settings → Branches → change default to `main`.
```

### What about the leaked PAT in git history?

The PAT was only in `.git/config` (a config file, not a tracked file), so it's *not in your commit history*. As long as you revoke the token in step 1, no scrubbing of history is needed. `.git/config` is local to your machine.

### Original "first push" instructions, if you ever start fresh

The project is *already* a git repo (see `.git/` in the project root) with a `master` branch and a remote called `origin`. If you wanted to start from scratch on a different repo, the steps would be:

```bash
# 1. Verify .gitignore covers all secrets — local.properties, google-services.json,
#    keystore files, build outputs, IDE config.
cat .gitignore

# 2. Make sure no API keys are committed accidentally:
git log --all --oneline | head -20
git grep -i 'sk-ant-' || echo "Clean"
git grep -i 'AIza' || echo "Clean"  # Google API keys
git grep -i 'sk_live_\|sk_test_' || echo "Clean"

# 3. Create the GitHub repo. Easiest: install gh CLI then:
gh repo create safe-companion --private --description "AI scam protection for older adults"

# 4. Add the remote and push:
git remote remove origin  # remove any stale origin
git remote add origin https://github.com/<your-username>/safe-companion.git
git branch -M main         # rename master to main (GitHub default)
git push -u origin main
```

### Critical: .gitignore audit

Before the first push, confirm these are listed in `.gitignore` (they're sensitive):

```
# Secrets
local.properties
*.jks
*.keystore
keystore.properties
google-services.json
fastlane/.env*

# Build
.gradle/
build/
app/build/
.idea/
*.iml

# OS
.DS_Store
Thumbs.db
```

If `google-services.json` is currently committed, remove it from history before pushing public:
```bash
git rm --cached app/google-services.json
git commit -m "Stop tracking google-services.json"
```

Then add a sample (`google-services.json.template`) for other developers.

### Recommended repo settings on GitHub

- **Private** initially (since the code includes scam patterns we'd rather not advertise to scammers).
- **Branch protection on `main`:** require pull request, require CI to pass, require 1 review (or self-review for solo work).
- **Secrets:** none yet — keystores stay local. When you set up CI for signed builds, store the keystore and password in GitHub Actions repository secrets.
- **Description:** "AI-powered Android app protecting older adults from scam calls, SMS fraud, suspicious emails, and cyber threats. Voice agent with personas. Local-first with optional cloud verification."

### Claude Code prompt for this

```
Read docs/PLAN-2026-05-02-cowork-session.md (Item 6 section).

1. Audit the .gitignore to make sure no secrets ship.
2. Run `git grep` for sk-ant-, AIza, sk_live_ to confirm no committed keys.
3. If google-services.json is tracked, remove from index.
4. Print the exact commands the user should run to:
   a. Create the GitHub repo
   b. Add remote
   c. Push

Do not run git push yourself — the user has GitHub credentials we don't have
access to. Just print the commands.
```

---

## Item 7 — Weekly developer scam-news scanner (outside the app)

This is a Cowork scheduled task, not an Android feature. It runs on Steve's machine and emails him a weekly digest of scam news with feature recommendations.

The Cowork `scheduled-tasks` MCP supports `create_scheduled_task` with a Claude prompt that runs every Monday morning. The prompt does:

1. Fetch the last 7 days of articles from the same RSS feeds the app already uses (AARP, FTC, FBI, BBB, Snopes — see Feature 48 spec in CLAUDE.md).
2. For each new scam pattern not already covered by an existing feature in CLAUDE.md status table, draft a feature suggestion: name, one-paragraph problem statement, proposed solution, AARP-friendliness rating (1–5), and estimated effort (S/M/L).
3. Compile the top 5 suggestions into an email-friendly markdown digest.
4. Email it to Steve at `mailjustices@gmail.com` (or however he wants to receive it).

This is implemented separately in the Cowork interface (see "Scheduled task created" message at the end of this session). Schedule: Monday 7 AM, every week.

---

## Code changes already made in this session

These compile-tested edits were applied directly to the repo:

| File | Change |
|---|---|
| `ui/chat/SafeHarborChatScreen.kt` | Added `verticalScrollbar(ScrollState)` and `verticalScrollbar(LazyListState)` Modifier extensions. Applied to the agent message Card and to `ChatHistoryView`'s LazyColumn. Bumped agent card max height from 200dp to 220dp; right padding from 16dp to 22dp to make room for the scrollbar gutter. |
| `ui/chat/SafeHarborChatViewModel.kt` | Bumped `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS` 10s→15s, `POSSIBLY_COMPLETE` 6s→10s, `MINIMUM_LENGTH` 2s→3s. Added `EXTRA_PREFER_OFFLINE = true` so the on-device recogniser actually honours those extras on Android 12+. Mid-speech timer 7s→10s. Watchdog 15s→25s. |
| `util/SafeHarborVoiceManager.kt` | `speakWithAndroidTts` now waits up to 2.5s for `isTtsReady` before declaring failure. If TTS still isn't ready, surfaces a Toast explaining the user needs to either add an ElevenLabs key or install a TTS engine. Re-attempts `initialize()` if the engine handle is null. |

These should be the highest-impact diffs against the user's report — voice cutoff (recogniser timeouts) and no-voice-response (Android TTS silent failure). The scrollbar fix is purely visual.

After the user pulls and rebuilds with `./gradlew assembleBetaDebug`, they should test:

1. Tap mic, speak slowly with pauses, see if the recogniser hangs in there for a fuller utterance.
2. Tap mic with no API keys at all (or just Anthropic) — confirm a Toast appears explaining voice is unavailable, *or* the Android TTS reads back the response.
3. Open the chat history view — confirm a scrollbar is visible on the right when content exceeds the viewport.
4. Get a long agent response — confirm the scrollbar appears in the agent-message card.
