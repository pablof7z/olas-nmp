# 04b — Sound Design

> One-line philosophy: **Olas is a quiet app. The haptic is the feedback; sound
> is a rare, opt-in flourish for the single most meaningful moment — sharing.**

## 1. Design principle

A premium, intimate, private-by-default photo app for small communities should
not chirp. People use it in bed, on transit, beside sleeping kids. Sound is the
most public, least dismissible feedback channel — so we spend it almost nowhere.

Rules:
1. **Default state: silent.** Likes, follows, navigation, tab switches,
   refreshes, errors, sheet snaps — **none** emit sound, ever. The haptic carries
   them.
2. **Exactly two app-authored sounds exist**, and both are **OFF by default**,
   behind one Settings toggle ("Capture sounds"):
   - `Sound(ShutterSoft)` — publish/onboarding-complete confirmation.
   - `Sound(ZapChime)` — zap-paid confirmation.
   When the toggle is off (default), these are never played; the success haptic
   still fires. When on, the sound plays **in addition to** the haptic.
3. **Camera shutter at capture:** when the user takes a photo/video **inside
   Olas's own camera**, play the OS's standard camera shutter via the system
   capture sound API (iOS `AudioServicesPlaySystemSound(1108)` is implicit when
   using `AVCapturePhotoOutput`; Android `MediaActionSound.SHUTTER_CLICK`). This
   is **not** governed by the Olas toggle — it is a **legal/social requirement**
   in several jurisdictions and a user expectation. It always plays at the
   regulatory volume the OS enforces, even on silent, where the OS mandates it.
   This is separate from the *publish* sound below.
4. **Respect the silent switch and audio session.** The two opt-in Olas sounds
   must use a non-mixing-hostile, ambient session that:
   - is **silenced by the hardware mute switch** (iOS `.ambient` category;
     Android `STREAM_NOTIFICATION` respecting Do-Not-Disturb / ring mode),
   - **never ducks or interrupts** the user's music/podcast (no
     `.duckOthers`, no audio focus request — play over the top at low level or
     skip if exclusive playback is active),
   - never routes to the earpiece, never forces the speaker.
5. **No looping UI sound, no sound on passive events, no sound on errors.**
   Errors are haptic-only (`Notify(Error)`); a failure sound would be punitive
   and embarrassing in public.

## 2. The sound set (exact character)

### 2.1 `Sound(ShutterSoft)` — publish / onboarding complete
- **Character:** a soft, rounded mechanical "thunk-click" — think a quality
  film-camera shutter heard through a coat pocket, not a digital beep. Warm, not
  bright. No metallic high-end ring.
- **Spectral content:** energy concentrated **120 Hz – 2.5 kHz**, gentle rolloff
  above 3 kHz (no sparkle/sizzle). A low body thump (~140 Hz) under a brief
  mid-click transient (~900 Hz–1.8 kHz).
- **Envelope:** fast attack (< 5 ms), short decay; **total duration 90–130 ms.**
  No sustain, no tail beyond ~140 ms.
- **Level:** peak ≈ **−18 dBFS**; quiet, sits under ambient. Single shot, never
  repeats.
- **Timing:** fires at the relay-accepted ack, sample-aligned with the first
  frame of the success choreography and the `Notify(Success)` haptic (`07` §1).

### 2.2 `Sound(ZapChime)` — zap paid
- **Character:** a single, soft, two-partial bell/chime — a tiny "ting" with a
  brief shimmer that says "value sent." Warmer and shorter than a notification
  chime; no long reverb tail.
- **Spectral content:** fundamental ~**1.05 kHz** with a consonant partial near
  ~1.6 kHz (a pleasant ascending interval feel); fast natural decay. Avoid the
  2–4 kHz "alert" band that reads as a notification.
- **Envelope:** soft attack (~8 ms), exponential decay; **total duration
  180–260 ms** including the short tail.
- **Level:** peak ≈ **−18 dBFS.** Single shot.
- **Timing:** fires at the zap payment-success ack, aligned with
  `Notify(Success)` and the lightning flash (`03` §3).

### 2.3 System camera shutter (capture, not publish)
- Owned by the OS; we do not author or modify it. iOS plays it automatically with
  `AVCapturePhotoOutput`; on Android call `MediaActionSound().play(SHUTTER_CLICK)`
  at the capture instant. Pair with `Impact(Rigid)` for the physical "snap" feel.
  This is the *only* sound that may play while the Olas toggle is off.

## 3. Implementation notes (capability layer)

- Rust emits `Feedback::Sound(ShutterSoft | ZapChime)` **only if** the user
  toggle is on (the toggle is app state in Rust; native never checks it). If the
  toggle is off, Rust simply does not emit the sound effect. Native plays
  whatever sound effect it receives — it never decides eligibility.
- The system camera shutter is the exception: it is a property of the capture
  capability itself, fired by native at the capture moment as part of executing
  the camera capability, and is not gated by the Rust toggle.
- Preload both sound buffers at app launch (iOS `AVAudioPlayer.prepareToPlay()` /
  a primed `AVAudioPlayerNode`; Android `SoundPool.load()`), so the shot is
  latency-free. A late sound is worse than no sound.
- Bundle both as short, high-quality assets (e.g. 48 kHz, mono, AAC or CAF/OGG).
  Author once; ship identical perceptual character on both platforms (parity).
