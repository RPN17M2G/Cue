# ADR 0006 — Staged identification cascade

Status: accepted
Date: 2026-10-19

## Context

Cue must identify the song from a screen recording (or from an existing MP3 whose
tags may be missing or wrong), offline-tolerant, with a good default and an easy
correction path. No single method is reliable: OCR depends on what the UI shows
and on script support (ADR 0003); fingerprinting needs network and a key;
embedded tags may be absent or wrong.

## Decision

Use an ordered cascade, each stage used only when the previous ones are
insufficient. Every stage feeds a single **Review screen** the user can correct,
and manual entry is always available.

1. **Embedded tags** (MP3 input). If title, artist, and album are present, skip
   the online lookup and go straight to Review; the lookup stays one tap away.
   Otherwise, tags seed the lookup.
2. **OCR** (video input): sampled frames over the first 15 s → ML Kit (Latin),
   then Tesseract (Hebrew) when appropriate.
3. **Online lookup:** iTunes Search first, MusicBrainz second, behind one
   resolver interface; auto-pick the top match and list alternatives.
4. **Fingerprint:** Chromaprint (~30 s slice starting ~20 s in) → AcoustID →
   MusicBrainz / Cover Art Archive.
5. **Manual entry / search:** never blocked.

ADR 0008 briefly added an ACRCloud stage after the fingerprint; ADR 0010
removed it so the audio stage is AcoustID only and fully free (and makes
audio-first the order for a manual audio search). ADR 0009 adds best-effort
enrichment after a confident match.

Offline or no-match never blocks the flow: Review shows the best available data
(with raw OCR text and a retry action).

## Consequences

- Best available identification for each input type, improving automatically when
  network is available and degrading gracefully when not.
- Each stage is an independent, testable unit behind an interface; the cascade
  itself is pure orchestration and is unit-testable with fakes.
- The user always confirms before saving, so a wrong guess costs one edit rather
  than a polluted library (no history is kept).
- Requires network toggles and a key path for fingerprinting (Settings +
  `local.properties`), and compliant clients for iTunes/MusicBrainz/AcoustID.
- Candidate aggregation across frames is needed to suppress OCR noise.

## Alternatives considered

- **Fingerprint-first.** Rejected: depends on network and a key, and is overkill
  when the on-screen title is readable.
- **OCR-only.** Rejected: fails on Hebrew (ADR 0003), on unreadable UI, and on
  recordings that crop the title.
- **Always auto-pick silently.** Rejected: no correction path; the review step is
  the product's quality guarantee.
- **Auto-save then enrich.** Rejected in favour of review-before-save.
