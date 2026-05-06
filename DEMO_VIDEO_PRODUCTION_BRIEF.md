# Safe Companion — Demo Video Producer Brief

This is the practical "how to actually shoot it" companion to
`DEMO_VIDEO_SCRIPT.md`. Hand both to whoever's editing.

---

## 1. Hardware

**Subject device (primary)**
- Pixel 7 / 7 Pro / 8 / 8a or Samsung Galaxy S22+. Why: clean stock-ish
  Android UI is what reviewers expect to see. Avoid heavily-skinned phones
  for the recording.
- Android 12 or newer (the app requires API 26+, but newer screen
  recorders are smoother).
- 1080p+ display. Brightness 70%, auto-rotate **OFF**, all "Always-on
  display" features **OFF**.
- Dark mode **OFF**. The app uses a light theme; mismatch looks unpolished.
- Status bar: turn on Do Not Disturb so unrelated notifications don't
  intrude. Set the system clock to **10:14 AM** (gives a "morning, daily
  use" feel — avoids late-night vibes).

**Family-member device (Shot D)**
- Any Android phone (or even an old iPhone — the SMS arriving will
  obviously look "iPhone-y", which can read as authentic; reviewers like
  proof that the alert reaches a real family device).
- Same Do Not Disturb posture so a stray Slack ping doesn't ruin the take.

**Camera (for filming the devices)**
- Mirrorless or DSLR with a 35–50mm equivalent lens, OR an iPhone Pro
  shooting 4K 24fps.
- Tripod. Avoid handheld — for an AARP-grade submission, every shake is
  visible.
- Soft, diffused lighting. One overhead key + a soft fill. Avoid harsh
  speculars on the screens — those will pulse on camera.

**If filming devices is too complex:** record screens directly using
**adb screenrecord** + composite onto a phone-mockup PNG in the editor.
Looks just as polished and avoids reflections / focus issues. See §3.

---

## 2. Recording the screen

```bash
# Install the standard debug build
./gradlew installStandardDebug

# Open the app on the device, get past onboarding, ensure sample data is seeded.
# Force a clean state if needed:
adb shell pm clear com.safeharborsecurity.app

# Start screen recording (no audio — VO is recorded separately)
adb shell screenrecord --bit-rate 12000000 --size 1080x2400 /sdcard/safecompanion_demo_raw.mp4

# Stop with Ctrl-C, then pull
adb pull /sdcard/safecompanion_demo_raw.mp4 ./
```

Record each shot in **separate takes**, longer than you need. It's far
easier to trim in the edit than to re-record.

For Shot D's family-side notification, do a second pass on the family
device with `adb` connected to *that* device (or just film it; an SMS
arriving on a phone is straightforward).

---

## 3. Phone-mockup compositing (recommended)

If you don't want to film physical devices:

1. Capture screen recordings as in §2.
2. Use a stock phone mockup PNG (Pixel 8 silhouette is free at
   designstripe.com or pixeltrue.com — get one with a transparent
   screen area).
3. In your editor, position the screen recording inside the mockup's
   screen window. Mask to the device's rounded corners.
4. Add a subtle drop shadow underneath the mockup (12px blur, 30%
   opacity, offset 8px down).

Result looks identical to a physical-device shot but with perfect focus,
no glare, and pixel-perfect text legibility.

---

## 4. Voiceover

**Talent direction**
- Warm but not saccharine. Think: a calm pharmacist explaining your
  prescription, not a TV ad.
- 50s feminine voice tests well with AARP audiences. A 60s grandmother
  voice can also work but only if it's genuine, never affected.
- Pace **135–145 wpm**. Slower than typical commercial; let words land.
- Smile while reading the line "the people we love" and "Quiet
  protection". The smile is audible; viewers feel it.

**Recording**
- USB condenser (Shure MV7, RØDE NT-USB Mini) into Audacity or
  GarageBand. A phone Voice Memo works in a pinch but you'll spend more
  time cleaning room tone.
- Record three full takes of each line. Mix-and-match for the best
  reading per line.
- Apply: light de-essing, EQ rolloff below 80 Hz, gentle compression
  (3:1 ratio, –18 dB threshold), final loudness target **−16 LUFS**
  integrated.

**Music ducking**
- Sidechain or manual: drop the music bed by 12 dB when VO is present,
  ramp back up over 400ms when VO ends.

---

## 5. Edit timeline

Use any NLE. **DaVinci Resolve** is free and professional; **CapCut** is
free and faster for short-form; **iMovie** works for a final pass.

Suggested track layout:

```
V4 — Captions (text overlay layer)
V3 — On-device "highlight" effects (pulse rings, tap indicators)
V2 — Phone mockup PNG with masked screen recording inside
V1 — Background gradient / brand canvas
A4 — VO
A3 — UI sound effects (tap clicks, notification chime — kept low)
A2 — Logo sting at 0:00 only
A1 — Music bed (full duration, ducked under VO)
```

Each shot transitions via **200 ms cross-dissolve**. No flashy wipes —
they undermine the trust-and-calm tone.

---

## 6. Color, type, brand assets

Pull these straight from the app's theme so the video and product are
visually identical:

| Use | Hex |
|---|---|
| Brand primary (backgrounds, outro) | `#1A3A5C` (Navy Blue) |
| Action accent (FAB, "tap" highlights) | `#00897B` (Accent Teal) |
| Danger (scam verdict) | `#D32F2F` (Scam Red) |
| Caution (suspicious verdict) | `#F57C00` (Warning Amber) |
| Safe (positive verdict) | `#2E7D32` (Safe Green) |
| Surface / background canvas | `#F5F7FA` (Warm White) |

Logo: pull `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png` (or extract
the foreground SVG-equivalent from the adaptive icon assets). For the
title/outro card, use the foreground shield on a Navy Blue background;
do not use the launcher's adaptive-icon background as-is.

Type:
- Headlines / outro: **Inter Semibold** or **SF Pro Display Semibold**.
- Captions: same family, Medium weight.
- Body in any callout cards: same family, Regular.

---

## 7. Export

**Master:** ProRes 422 HQ or DNxHR HQX, 1920×1080, 24fps. Keep this
locally — you'll re-export from it for every variant.

**Delivery files:**
- `safecompanion-30s-1080p.mp4` — H.264, 12 Mbps, AAC 256 kbps stereo.
  *Primary submission.*
- `safecompanion-30s-vertical.mp4` — 1080×1920 from the same timeline,
  reframed (crop and reposition device mockups).
- `safecompanion-30s-square.mp4` — 1080×1080 for grid placements.
- `safecompanion-15s.mp4` and `safecompanion-6s.mp4` — see cutdown
  scripts below.

**Caption files:**
- Always burn captions into the deliverable for AARP — never rely on
  player-side toggling.
- Also export an `.srt` sidecar so reviewers can verify caption text in
  their accessibility audit.

---

## 8. Pre-flight QA before submission

- [ ] Watch with **sound off**. Story still readable from captions
      alone? (AARP tests this.)
- [ ] Watch on a phone in direct sunlight — text still legible?
- [ ] Watch with subtitles forced on the underlying media player —
      no double-captions?
- [ ] No real phone numbers or email addresses appear anywhere.
- [ ] No real third-party brand logos are visible (the "Bank of
      America" mention in the scam SMS body is fine; an Amazon logo
      mockup is **not** — keep brand mentions textual, not logoed).
- [ ] Audio loudness measured at −16 LUFS integrated, true-peak
      below −1 dBFS.
- [ ] Final file under 100 MB. AARP uploaders have hard caps.

---

## 9. 15-second cutdown script

| Time | Shot | VO | Caption |
|---|---|---|---|
| 0:00–0:01 | Title card | (chime) | SAFE COMPANION |
| 0:01–0:07 | Scam SMS caught | "Safe Companion catches scam texts before you even read them." | "Caught automatically." |
| 0:07–0:12 | "Is This Safe?" | "Or check anything yourself in seconds." | "Plain-English answers." |
| 0:12–0:14 | Family alert | "Family stays in the loop." | "Family is alerted." |
| 0:14–0:15 | Outro | "Safe Companion." | safecompanion.app |

VO total: 18 words. Tight; rehearse to nail the pace.

---

## 10. 6-second cutdown script (social pre-roll)

Pure visual + caption-only, no VO. Music bed full strength.

| Time | Shot | Caption |
|---|---|---|
| 0:00–0:01 | Scam SMS arrives | "A scam text…" |
| 0:01–0:03 | Verdict banner overlays | "…stopped before you see it." |
| 0:03–0:05 | "Is This Safe?" verdict card | "Plain-English answers." |
| 0:05–0:06 | Logo + URL | "Safe Companion · safecompanion.app" |

---

## 11. Done-when

This is shippable when, in this order:

1. Scriptwriter approves the VO transcript reads naturally aloud.
2. Producer can perform every on-device beat in a single take of the
   debug build (no edits required to make the app cooperate).
3. Voice talent delivers takes that hit −16 LUFS without aggressive
   compression.
4. Editor produces the 30s master with captions burned in, then
   spot-checks the §8 QA list.
5. The 15s and 6s variants come out of the same master timeline —
   not re-edited from scratch.
