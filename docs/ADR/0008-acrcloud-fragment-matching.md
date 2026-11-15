# ADR 0008 — ACRCloud for fragment matching

Status: accepted
Date: 2026-11-15

## Context

AcoustID fingerprints the **whole file**, so it only matches a recording that
starts at the beginning of the song. Measured on the emulator fixtures, the
match rate by lead-in is 0.97 at 2 s, 0.79 at 5 s, 0.68 at 8 s, 0.63 at 10 s,
and nothing from 12 s on. Screen recordings routinely start mid-song, which is
exactly the case the fingerprint stage cannot cover.

The app's bundled FFmpeg build also has no `silenceremove`/`silencedetect`
filter, so trimming the lead-in before fingerprinting is not available (see the
note in `FfmpegCommandBuilder`). Detecting the start from OCR was tried and
dropped: the player UI does not reliably show when the audio began.

## Decision

Add **ACRCloud** as a second audio-recognition stage. It matches a short
fragment taken from anywhere in the track, so a clip that starts mid-song still
resolves.

- **Sample:** mono 64 kbps MP3, 12 seconds, taken from the middle of the
  recording (`FfmpegAudioSampleExtractor.startOffsetMs`), uploaded as a
  multipart `sample`.
- **Request:** `POST /v1/identify`, signed with HMAC-SHA1 over method, path,
  access key, data type, signature version and timestamp. The secret never
  leaves the device.
- **Cascade order:** text → AcoustID → ACRCloud. A manual or forced audio
  search runs AcoustID → ACRCloud → text, because the user asked for audio.
- **Credentials:** host, access key and access secret come from the untracked
  `local.properties` (`acrcloud.host`, `acrcloud.accessKey`,
  `acrcloud.accessSecret`); Settings can override them. They are never
  committed.
- **Credential check:** uploads a generated silent MP3 probe. Any non-auth
  business answer (`1002`–`1005`) proves the credentials are accepted; only
  `3000`–`3010` count as an auth or quota failure.

## Consequences

- A recording that starts anywhere in the song can now be identified without
  manual entry, which is the common case for screen recordings.
- One more network dependency and a monthly free-tier quota. A failed or
  exhausted stage degrades to "no audio match", never to a blocked save.
- The user must create an ACRCloud project to use the stage; without
  credentials the stage is skipped and the app behaves as before.
- Two audio services mean two failure modes to explain in Settings, so each
  stage reports its own reason (no match, rejected key, quota) rather than one
  generic error.

## Alternatives considered

- **AudD.** Comparable fragment matching, but the account model is a single
  token and the free tier is smaller. ACRCloud was chosen for the project
  (host + key + secret) model and its larger free allowance.
- **AcoustID with a trimmed lead-in.** Not possible with the bundled FFmpeg
  filters, and the exact start is not reliably readable from OCR.
- **Fingerprint the middle slice only.** AcoustID rejects mid-track windows
  outright, so this returns nothing.
