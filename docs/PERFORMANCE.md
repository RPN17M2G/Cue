# Identification performance

How long a recording takes to identify, what dominates the time, and how to
measure it again.

## What was changed

- **Frames are decoded at OCR size.** `MediaVideoFrameExtractor` reads the
  video's metadata and calls `getScaledFrameAtTime`, so a 1080×2400 recording
  produces a 576×1280 bitmap directly. The previous path decoded the full frame
  and then shrank it, which allocated the large bitmap first and was the main
  source of memory pressure on a long scan. A rotation of 90/270° is accounted
  for, because the retriever returns frames in display orientation.
- **Both OCR passes run in parallel.** The Latin (ML Kit) and Hebrew
  (Tesseract) engines read the same frames independently, so they now run as
  two coroutines and the wall time is the slower of the two. Both passes still
  always run: a shortcut that trusted a strong Latin score was wrong in
  practice, and the scores decide. A batch mutex keeps the shared ML Kit
  recognizer out of two concurrent identifications.
- **Text lookups are memoized.** `LookupCache` keeps successful, non-empty
  answers for ten minutes in a small access-order LRU. A retry, or two OCR
  readings that normalize to the same query, no longer costs another provider
  round trip. Failures and empty answers are deliberately not cached.
- **Every stage reports wall time.** `StageTimings` records frame extraction,
  each OCR pass, the cascade, and enrichment. Debug builds log one line under
  the `CueTimings` tag.

## Reading the numbers

```
adb logcat -s CueTimings:D
```

```
identify now-playing-en.mp4: frames=15418ms (8 frames, 1927ms/frame)
  ocr=8351ms (latin=8347ms, hebrew=4125ms) cascade=851ms
  enrichment=543ms total=25163ms
```

- `frames` is the whole extraction, including seeking and JPEG encoding, and
  `frames/frameCount` is the cost of one sampled frame.
- `ocr` is the wall time of the parallel section; `latin` and `hebrew` are the
  individual passes and can add up to more than `ocr`.
- `cascade` is the network identification (text → AcoustID → ACRCloud).
- `enrichment` is lyrics and Deezer, which only run after a confident match.
- `total` is the sum, so the four numbers add up to the wait the user sees
  (plus the conversion that follows).

## Measured on the emulator

`Medium_Phone_API_31` (1080×2400, 4 vCPU, software video decode), 18-second
recordings, default 2-second OCR interval:

| Recording | Frames | OCR (Latin / Hebrew) | Cascade | Enrichment | Total |
|---|---|---|---|---|---|
| `now-playing-en.mp4` | 15.4 s (8 frames) | 8.4 s (8.3 / 4.1) | 0.9 s | 0.5 s | 25.2 s |
| `now-playing-he.mp4` | 14.6 s (8 frames) | 6.8 s (6.8 / 1.9) | 0.8 s | 0.2 s | 22.4 s |

Both recordings identify exactly as before the change: *Believer — Imagine
Dragons* at 100% from iTunes, and the Hebrew recording at 75% with *Noa Kirel*
as the artist.

Frame extraction is more than half of the wait on the emulator, at roughly
1.9 s per frame. That number is not representative of a phone: the emulator
decodes H.264 in software, while a real device uses the hardware decoder and
typically needs tens of milliseconds per frame. A physical device is still
required before quoting a real-world time; the stage breakdown makes that
comparison a single log line.

## Where the remaining time goes

- **Frame extraction** scales with the number of sampled frames, the video
  resolution, and the keyframe interval. The OCR interval in Settings is the
  direct control: doubling it halves the frame count.
- **OCR** is bounded by the slower engine. ML Kit dominates; Tesseract Hebrew
  is cheap by comparison.
- **Cascade** is network latency. A text hit is one or two requests; the
  fingerprint and ACRCloud stages only run when text fails or the user asks
  for them.
- **Enrichment** runs lyrics and Deezer in parallel and never blocks the save.
