# ADR 0003 — Dual OCR: ML Kit (Latin) + Tesseract (Hebrew)

Status: accepted
Date: 2026-10-19

## Context

Cue reads the song title and artist from the video's frames. It must work
offline, must be accurate on clean UI text, and must support Hebrew, since the
primary user records Hebrew-titled songs and the app itself is bilingual.

Fact: **ML Kit Text Recognition v2 supports the Latin, Chinese, Devanagari,
Japanese, and Korean scripts — but not Hebrew.** Hebrew appears only in ML Kit's
separate language-*identification* API, not in text recognition.

Alternatives:

1. ML Kit only, accepting that Hebrew titles cannot be read via OCR.
2. Tesseract for everything.
3. ML Kit for Latin, plus a Tesseract Hebrew pass when needed.

## Decision

Run **ML Kit Text Recognition v2 (Latin)** first. If the app language is Hebrew,
or if no plausible title emerges from the Latin pass, run a second pass with
**Tesseract4Android** using bundled Hebrew traineddata on the same frames, and
select whichever pass yields the better candidate.

Fingerprint (ADR 0006 / PLAN) and manual entry remain the downstream fallbacks.

## Consequences

- Hebrew-script titles are readable offline, satisfying the bilingual goal.
- Adds roughly 5–8 MB (Tesseract library + Hebrew traineddata) and a second OCR
  pass over the (small) sampled frame set on Hebrew paths.
- Two OCR engines must be kept behind one interface so the rest of the pipeline is
  engine-agnostic and testable.
- Tesseract is less accurate than ML Kit on stylized Latin UI text, so it is used
  only for the Hebrew path, not as the general engine.
- Candidate aggregation, not a single frame, decides the result, which reduces the
  impact of any one engine's occasional misread.

## Alternatives considered

- **ML Kit only.** Rejected: cannot read Hebrew script, which is a core scenario
  for this user.
- **Tesseract only.** Rejected: noticeably worse Latin accuracy on UI text than
  ML Kit, and a heavier single dependency.
- **Cloud OCR with Hebrew support.** Rejected: conflicts with the on-device-first
  architecture and introduces keys, cost, and privacy questions.
