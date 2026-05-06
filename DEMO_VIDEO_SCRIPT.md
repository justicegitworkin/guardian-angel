# Safe Companion — 30-Second Demo Video Script

**Audience:** AARP submission. Older adults and the family members who care
about them. Trust and clarity are the brand promise — match that tone in
every shot, every line, every transition.

**Total runtime:** 30.0s
**Aspect ratio:** 1920×1080 horizontal (primary). Re-export 1080×1920 vertical
and 1080×1080 square from the same master timeline.
**Voiceover:** warm, calm, mid-50s feminine. ~135 wpm (slightly slower than
typical commercial pace — gives the words time to land for an older audience).
**Captions:** always on, large sans-serif, white with 4px black outline,
bottom-third safe area.

---

## Master timing

| Time | Shot | Beat |
|---|---|---|
| 0:00–0:02 | A. Title card | Establish brand |
| 0:02–0:10 | B. Scam SMS caught | The hook (emotional payoff) |
| 0:10–0:20 | C. "Is This Safe?" | The everyday tool |
| 0:20–0:28 | D. Family alert arriving | The safety net |
| 0:28–0:30 | E. Outro card | CTA |

Total VO: 37 words across 23 seconds of speaking time + 7s of breathing /
visual moments. Test pace by reading aloud; trim if it feels rushed.

---

## Shot A — Title card  ·  0:00–0:02

**Visual**
- Solid background: `#1A3A5C` (Navy Blue — the app's primary).
- Center: Safe Companion shield logo, scaled to ~40% of frame height.
- Logo enters via a 0.5s "settle" animation: starts 110% scale + soft glow,
  eases to 100% scale, glow fades.
- Sub-line below logo, fades in at 0:01.0: **"Quiet protection for the
  people you love."** (32pt, light weight, `#FFFFFF` 80% opacity).
- Hold to 0:02.0, then 200ms cross-fade to Shot B.

**Audio**
- Single soft chime/sting at 0:00.0 (≤1s, no melody — think Apple Watch
  notification, not a fanfare).
- Music bed starts under the chime at 0:00.5, ducked to −12dB until VO ends.

**VO** — silent.

**Caption** — none (the sub-line is the on-screen text).

---

## Shot B — Scam SMS caught  ·  0:02–0:10  *(8.0s)*

**Visual setup**
- Subject device: clean Android home screen (Pixel-style works best for
  reviewers; Samsung is also fine). Wallpaper: solid soft warm gray
  `#ECEEF2` for maximum contrast on captures. Battery 100%, Wi-Fi on,
  status bar clock set to 10:14 AM.
- Frame the device upright, slight 5° tilt, soft drop shadow underneath.
  Background canvas: subtle off-white-to-light-blue gradient.

**Beat 1 — 0:02 to 0:04: the scam arrives.**
- At 0:02.4 a scam SMS heads-up notification slides in from the top:
  > **(800) 555-0199**
  > URGENT: Your bank account has been compromised! Click here immediately
  > to verify: http://bank-secure-verify.tk/login
- Notification holds for 1.5s. Slight pulse animation to draw the eye.

**Beat 2 — 0:04.0 to 0:08.0: Safe Companion intercepts.**
- At 0:04.0 a Safe Companion alert *banner* slides down on top of the SMS
  notification. (Use the existing `showSmsAlert` red `[ACTION_REQUIRED]`
  card — content is already in the codebase via the seeded sample alert.)
- Banner content (large, easy to read):
  > 🛡️ **DANGEROUS — Bank phishing detected**
  > "Uses urgent pressure and a fake bank URL on a free .tk domain. Real
  > banks never send links like this."
  > **Do not click. Delete the message.**
- Subtle red glow around the banner edge. The original scam SMS dims to 50%.

**Beat 3 — 0:08.0 to 0:10.0: hold + breath.**
- A small green "Caught automatically" pill animates in below the banner.
- Hold for 1.5s, then 200ms cross-dissolve to Shot C.

**VO (0:02.5 → 0:09.5)** — *7.0s of speech, leaves a half-second pad each end:*
> "Every day, scammers target the people we love.  *(beat)*  Safe
> Companion catches them — without you doing a thing."

Word count: 17. Pace: 145 wpm. Two natural breath beats at the commas.

**On-screen captions** (sequential, 3-word max each, ~1.5s per card):

| Time | Caption |
|---|---|
| 0:02.5 | "Scammers target everyone." |
| 0:04.5 | "Safe Companion sees it first." |
| 0:07.0 | "Caught automatically." |

---

## Shot C — "Is This Safe?"  ·  0:10–0:20  *(10.0s)*

**Visual setup**
- Same device, same framing. Cross-dissolve from Shot B.

**Beat 1 — 0:10.0 to 0:11.5: open the home screen.**
- Brief glimpse of Safe Companion home screen showing the recent-alert
  card from Shot B at the top (continuity).
- A teal "Is This Safe?" FAB pulses gently in the bottom-right corner.

**Beat 2 — 0:11.5 to 0:13.5: tap-to-open.**
- Animated tap indicator (semi-transparent white circle) lands on the FAB.
- Screen pushes up into the Safety Checker screen with the four entry
  tiles: **Photo · Web Address · Message · QR Code**.

**Beat 3 — 0:13.5 to 0:18.0: the check.**
- Tap "Web Address". Input field appears.
- A URL types itself in (animated, ~0.5s):
  `http://amaz0n-secure-login.tk/verify`
- "Check It" button highlights and is tapped.
- 0.4s loading spinner.
- Result card animates up: red verdict icon, **DANGEROUS**, plain-English
  reasons:
  - "Misspelled brand name (amaz0n with a zero)"
  - "Free .tk domain often used in scams"
  - "Not Amazon's real website"

**Beat 4 — 0:18.0 to 0:20.0: hold + breath.**
- Card holds. A teal "Plain English answer in seconds" pill animates in.
- 200ms cross-dissolve to Shot D.

**VO (0:10.5 → 0:19.0)** — *8.5s of speech:*
> "Not sure about a message, a link, or even a photo?  *(beat)*  Tap 'Is
> This Safe?' for a clear answer in plain English."

Word count: 20. Pace: 141 wpm.

**Captions:**

| Time | Caption |
|---|---|
| 0:10.5 | "Got something suspicious?" |
| 0:13.0 | "Tap 'Is This Safe?'" |
| 0:16.5 | "Get a plain-English answer." |

---

## Shot D — Family alert arriving  ·  0:20–0:28  *(8.0s)*

**Visual setup**
- Wide shot: subject device on the left, a second device (the family
  member's phone) on the right. Both upright, slight inward tilt so the
  camera "sees" both at once.
- If you only have one device, composite a second phone-mockup PNG in
  the editor — the audience won't notice as long as both screens are
  pre-recorded cleanly.

**Beat 1 — 0:20.0 to 0:23.0: subject device fires the alert.**
- The DANGEROUS verdict card from Shot C is still visible on the left
  device.
- A small toast/footer animates up: "Family alert sent to Sarah".
- Faint particle line visualizes the message traveling across the gap to
  the right device.

**Beat 2 — 0:23.0 to 0:27.0: family device receives.**
- The right device's screen brightens. SMS heads-up notification slides
  in:
  > **Safe Companion**
  > Mom may have received a scam text. We blocked it. You may want to
  > check in.
- The recipient device pulses softly.

**Beat 3 — 0:27.0 to 0:28.0: hold.**
- Camera pushes in slightly on the family-side screen. 200ms
  cross-dissolve to Shot E.

**VO (0:20.5 → 0:27.5)** — *7.0s of speech:*
> "And when something dangerous happens, family is alerted right away —
> so no one's facing it alone."

Word count: 18. Pace: 154 wpm. Slightly faster — landing the emotional
beat ("alone") with a gentle hold.

**Captions:**

| Time | Caption |
|---|---|
| 0:20.5 | "Family stays in the loop." |
| 0:24.0 | "No one faces a scam alone." |

---

## Shot E — Outro card  ·  0:28–0:30  *(2.0s)*

**Visual**
- Background: same Navy Blue `#1A3A5C` as the title card.
- Logo center, slightly smaller than title (30% of frame height).
- Below logo, two stacked lines:
  - **Safe Companion** (52pt, semibold, white)
  - **safecompanion.app** (28pt, light, white 80%)
- Gentle 4px white underline beneath the URL animates left-to-right
  across the full 2 seconds.
- Music bed lifts back to 0dB for a 1-second outro tail, then ducks.

**VO (0:28.0 → 0:29.5)** — *1.5s:*
> "Safe Companion. Quiet protection."

Word count: 4. Pace: deliberate.

**Caption** — none. Logo + URL are the visual anchor.

---

## Full VO transcript (paste into your DAW)

> "Every day, scammers target the people we love. Safe Companion catches
> them — without you doing a thing.
>
> Not sure about a message, a link, or even a photo? Tap 'Is This Safe?'
> for a clear answer in plain English.
>
> And when something dangerous happens, family is alerted right away —
> so no one's facing it alone.
>
> Safe Companion. Quiet protection."

**Total:** 59 words across 24.5 seconds of speaking time. Average pace
**144 wpm** — calm but never slow.

---

## Caption style spec

- Font: SF Pro Display Semibold *or* Roboto Medium (fall back to system
  sans-serif).
- Size: 56pt for the 1080p horizontal master. Scale proportionally for
  vertical/square.
- Color: `#FFFFFF`. Outline: 4px `#000000`. Drop shadow: 6px y-offset, 30%
  opacity.
- Position: anchored 12% from bottom of frame. Always above the device's
  visible status bar / home indicator.
- Motion: enter via 200ms slide-up + fade-in; exit via 150ms fade-out.
  No bouncing, no zoom.
- Maximum 5 words per card. If a sentence is longer, split across two
  cards on the same shot.

---

## Music guidance

- Genre: warm acoustic, lightly orchestrated. Think gentle piano + low
  string pad. **No vocals.**
- Tempo: 70–80 BPM.
- Energy curve: builds subtly through Shot C, peaks at the start of
  Shot D ("family is alerted"), resolves on the outro.
- Licensed sources that have something appropriate:
  - **Epidemic Sound** — search "warm hopeful piano underscore"
  - **Artlist.io** — "documentary acoustic"
  - **Musicbed** — "uplifting calm"
- Avoid: anything with a beat drop, percussion-heavy, electronic, or
  nostalgic sad piano.

---

## On-screen content notes (so the recording matches the codebase)

- The bank-phishing scam card is already seeded as `demo_1` in
  `SampleDataSeeder.kt`. On a fresh install of the debug build, the
  home screen will show it on first launch.
- The Safety Checker URL flow is reachable from
  `Screen.SafetyChecker` → "Web Address". `http://amaz0n-secure-login.tk/verify`
  will trip the on-device classifier without needing any API key — both
  the look-alike domain and the `.tk` TLD are flagged in
  `OnDeviceScamClassifier.phishingUrlPatterns`.
- The family-alert SMS body in Shot D matches the template used by
  `FamilyAlertManager.sendFamilyAlert(...)`. Trigger it manually for
  the recording by long-pressing the demo `demo_1` alert card and
  choosing "Tell Family" *(or wire a debug-only "send sample alert" hook
  if the long-press menu doesn't expose it)*.
