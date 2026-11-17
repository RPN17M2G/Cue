# ADR 0010 — AcoustID only, because it is free

Status: accepted (supersedes ADR 0008)
Date: 2026-11-16

## Context

The owner asked for an audio-identification stack that is **100% free**, and
preferred AcoustID alone if it works well enough. AcoustID is open source, free
for non-commercial use, and the only free service that matches a Chromaprint
fingerprint against a large public database. Every alternative that recognises
a fragment from the middle of a song (ACRCloud, AudD, Sonovault, ShazamKit) is
either paid, quota-limited, or platform-locked.

AcoustID was measured against the real fixtures before removing anything:

| Case | Result |
|---|---|
| `he-song-1939.mp4` — *favorite crime* (2:33, full track) | matched in-app at **97%** |
| `he-song-1935.mp4` — *תן / Roni Daloomi* (3:24, full track) | fingerprint matched at **0.946**, but the AcoustID submission is not linked to a MusicBrainz recording, so no title/artist was returned |
| 18-second clip that starts mid-song | no result, even when the true track length was supplied |
| 15-second prefix taken from the song's beginning, true length supplied | matched |

The *תן* recording exists in MusicBrainz (recording `d8f7edde-f6cf-40ac-be56-db084d94b3eb`,
ISRC `IL5181100081`); the AcoustID fingerprint entry `2b02aee6-bb2f-4d68-8f67-033776ae99be`
simply has no recording link. That is a catalogue gap, not an app bug: the same
fingerprint lookup path returned 97% for *favorite crime* on the same device.

The app already reads the whole-track length from the player UI and sends it to
AcoustID, which is what makes a prefix match work. OCR + iTunes/MusicBrainz text
lookup remains the first stage and covers the Hebrew recordings that AcoustID
does not know.

## Decision

Use **AcoustID as the only audio-fingerprint provider**. Remove ACRCloud and
every fragment-matching dependency:

- `AcrCloudClient`, `AcrCloudSignature`, `AcrCloudProbe`
- `FfmpegAudioSampleExtractor` and the ACRCloud FFmpeg command
- the `AudioRecognitionAttempt` cascade stage and its Settings/UI strings
- the `ACRCLOUD_*` build fields and DataStore keys

The cascade is now text → AcoustID. A manual audio search runs AcoustID → text.

## Consequences

- No account, key, quota or billing beyond the free AcoustID application key.
- A clip that starts mid-song cannot be identified by audio alone. The text
  stage, the manual edit fields and "Re-run text scan" are the fallbacks.
- Some tracks present in MusicBrainz are still unlinked in AcoustID (the *תן*
  case). The app reports "no song found" and keeps the OCR reading, which is
  accurate and editable.
- One fewer network dependency and one fewer credential surface in Settings.

## Alternatives considered

- **ACRCloud** (ADR 0008). Matches mid-song fragments, but needs a commercial
  project and a quota; rejected because the requirement is fully free.
- **AudD.** Comparable matching, paid beyond a small trial; rejected.
- **Sonovault / ShazamKit.** Paid recognition or platform-locked; rejected.
- **Self-hosted fragment recogniser.** No maintained free database to match
  against; out of scope for a local Android app.
