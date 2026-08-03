# Multi-GIF as photos — Implementation Plan

> **For agentic workers:** Implement sequentially. No separate test module in repo — verify via compile + device install.

**Goal:** GIF source lists multiple GIFs like photos; animate current GIF; timer/shuffle/side taps change GIFs.

**Architecture:** Each playlist `LocalPhoto` is one GIF (`gifSourcePath` + frames under `gifs/{id}/`). `GifPlaybackController` loops frames of `currentPhoto` only. Cycle/tap reuse PHOTOS paths. Migrate legacy flat frames → one GIF.

**Tech Stack:** Kotlin, Compose, Room order/photos, KeepAliveService, GifDrawable extraction

## Global Constraints

- Home cannot natively play `.gif` in RemoteViews — keep frame extraction for current GIF only
- Behavior = tap actions + photo timer + shuffle (same as static)
- Cycle pause does not freeze current GIF animation
- Per-GIF frame interval from extraction metadata

---

### Task 1: Model + storage extract per GIF

**Files:** `LocalPhoto.kt`, `PhotoWidgetInternalFileStorage.kt`, `PhotoWidgetStorage.kt`, `GifFrames.kt`

- [ ] Add `gifSourcePath` (+ optional keep using widget `gifInterval` only as fallback; prefer per-item interval field `gifFrameInterval`)
- [ ] `newWidgetPhotoFromGif` → returns **one** LocalPhoto + writes frames under `gifs/{id}/`
- [ ] `getGifFrameUris(directoryName, gifId)` for playback
- [ ] `deleteWidgetPhoto` deletes whole `gifs/{id}/` when GIF item

### Task 2: Configure multi-pick + UI

**Files:** Configure Screen/ViewModel/ContentTab/BehaviorTab, strings

- [ ] `GetMultipleContents("image/gif")`; `gifPicked` appends like photos
- [ ] Remove wipe-all replace dialog
- [ ] Enable sort/remove like photos for GIF
- [ ] Behavior: show timer/shuffle/taps for GIF; hide global frame-interval picker
- [ ] Preview: animate via gifSourcePath or frame list of selected GIF only

### Task 3: Taps + playback + cycle

**Files:** `PhotoWidgetTapAction.kt`, `GifPlaybackController.kt`, Provider/Cycle paths, `PrepareGifPhotosUseCase.kt`

- [ ] Allow ViewNext/Previous/ToggleCycling/etc. on GIF
- [ ] Playback loads frames for current GIF only; restart on current change
- [ ] Prepare styles frames per GIF dir
- [ ] photoCycleEnabled for GIF when multiple / cycle mode

### Task 4: Legacy migration + install

**Files:** Storage load path or one-shot migrator

- [ ] If GIF source and no `gifs/` layout: wrap existing flat frames as one GIF item
- [ ] `installDebug` and smoke-check
