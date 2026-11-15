# ADR 0009 — Enrichment after a confident match

Status: accepted
Date: 2026-11-15

## Context

A correct title and artist are the minimum, but the files Cue produces are
meant to be usable in players and DJ tools. Lyrics, tempo, loudness, the ISRC
and the label are standard tags those tools read, and none of them are part of
an iTunes or MusicBrainz search result.

Two constraints shape the design: a save must never be blocked or delayed by a
service that is slow or down, and a value the user typed (or an earlier stage
supplied) must never be replaced.

## Decision

After a **confident** identification, run a best-effort enrichment step that
only fills blanks.

- **Sources:** LRCLIB for lyrics; Deezer for BPM, ReplayGain and ISRC.
  MusicBrainz recording/release/artist IDs come from the existing text stage
  and are written as `TXXX` frames.
- **Parallel:** lyrics and the Deezer lookup are independent and run
  concurrently. Either failing is "nothing found", not an error.
- **Blanks only:** every field is merged only when the draft has no value, so
  user edits and provider values win in that order.
- **Deezer needs two calls:** `/search` returns names and the ISRC but not BPM
  or gain, so the chosen track is read once more from `/track/{id}`. A failed
  detail call still keeps the search result.
- **Tags:** `USLT` for lyrics, `TBPM`, `TSRC`, `TPUB`, `TXXX` for ReplayGain
  and the MusicBrainz IDs. `TagField` gained the matching fields so in-place
  edits and re-encodes carry them too.
- **UI:** the review screen shows what was filled and where it came from
  (`LRCLIB`, `DEEZER`, `MUSICBRAINZ`), and offers a manual lyrics fetch.

## Consequences

- A saved file carries lyrics, tempo, gain, ISRC and label without extra user
  action, and the review screen still lets the user see and change them.
- An offline or failing enrichment never blocks or fails the save; the file is
  simply less complete.
- The job handed to the foreground service must carry these fields. The
  Intent codec is part of the contract and is covered by a round-trip test —
  omitting a field there silently drops it from the saved MP3.
- Deezer is an unofficial public API; a change on their side degrades to "no
  BPM/gain", not to a broken save.

## Alternatives considered

- **Enrich on the Saved screen.** Rejected: the file would need a second write,
  and the user would no longer review the values before they land.
- **MusicBrainz for BPM/label.** Rejected: no tempo or gain, and the label is
  release-level and often missing.
- **Hi-res artwork from a dedicated provider.** Not selected for this pass;
  the existing artwork path already covers the cover.
