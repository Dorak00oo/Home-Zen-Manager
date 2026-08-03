# Multi-GIF widgets (GIF-as-photos) — Design

**Date:** 2026-08-02  
**Status:** Implemented (initial)  
**Approach:** A — keep `PhotoWidgetSource.GIF`, remodel playlist to GIFs (not frames)

## Problem

Today a GIF widget picks **one** GIF, extracts **all frames** into the photo list, and animates by cycling those frames. Users cannot:

- Select multiple GIFs
- Navigate GIFs like photos (side taps / timer / shuffle)
- See a GIF as a single item in configure (they see every frame)

They want GIFs to behave like static photo widgets for browsing/cycling, while the **current** GIF stays animated (in-app and on the home screen).

## Goals

1. Multi-select GIFs; list/reorder/remove like photos.
2. In-app: show **GIFs** (animated preview of the selected one), not a grid of frames.
3. Home screen: **animate the current GIF**; left/right (and cycle mode) move between GIFs.
4. Behavior tab same as static widgets: tap actions, photo timer, shuffle.
5. KeepAlive / frame extraction remain an implementation detail for home-screen animation (Android RemoteViews cannot play `.gif` natively).

## Non-goals

- Native animated GIF decode inside AppWidget `RemoteViews`
- Merging GIF into `PhotoWidgetSource.PHOTOS`
- Video support
- Changing battery-warning dismiss behavior (already per-widget)

## Product decisions (locked)

| Topic | Decision |
|-------|----------|
| Current GIF on home | Animated (frame loop of that GIF only) |
| Side taps | Previous / next **GIF** |
| Behavior section | Same as static: tap actions, photo timer, shuffle |
| Pause cycling | Stops advancing between GIFs; **current GIF keeps animating** |
| Optional pause-animation tap | Deferred unless requested later |
| Frame rate | From each GIF’s own timing (stored per GIF); not the photo timer |

## Data model

### Playlist item = one GIF

Each entry in `photoWidget.photos` for source `GIF` represents **one GIF**, not one frame.

Suggested shape (names flexible in implementation):

```text
LocalPhoto (GIF item)
  photoId          // stable id for the GIF
  originalGifPath  // or path under widgetDir/gifs/{id}/source.gif
  croppedPhotoPath // poster / first prepared frame for thumbs & still fallback
  frameIntervalMs  // from GIF metadata, clamped
  // frames live on disk under widgetDir/gifs/{id}/frames/*.png
```

`currentPhoto` = current **GIF** in the playlist.

### Storage layout (example)

```text
widgetDir/
  gifs/
    {gifId}/
      source.gif          // optional retain for in-app Coil/GifDrawable
      original/           // unstyled frame PNGs
      {frame}.png         // prepared frames for launcher URIs
  current/                // existing current-photo cache if still used for stills
```

### Room / order

- Order table and `local_widget_photos` list **GIF ids** (same machinery as PHOTOS).
- Do **not** put every frame id in the playlist order.

## UX — Configure

### Picker

- `GetMultipleContents("image/gif")` (append like `photoPicked`).
- Replacing: add GIFs without wiping the whole playlist (delete only when user removes items).
- Remove “replace entire GIF” dialog that clears everything; use per-item remove like photos.

### Content tab

- Grid of **GIF thumbnails** (poster frame or animated if cheap).
- Enable `canSort` / reorder when >1 GIF (same rules as photos where applicable).
- Count copy: “N GIFs” not “N frames”.
- Main preview: **animate the selected GIF** (in-app: prefer original GIF via decoder; fallback frame cycle).

### Behavior tab

Mirror PHOTOS for GIF source:

1. Tap action picker — allow `ViewPreviousPhoto`, `ViewNextPhoto`, `ViewFullScreen`, `ToggleCycling`, shortcuts, etc.
2. Photo timer / cycle mode — intervals, schedule, etc. between GIFs.
3. Shuffle — between GIFs.

Remove or demote the old **global “GIF frame interval”** UI that treated frames as the playlist. Per-GIF interval stays internal (from extraction).

### Tap action coercion

Update `coerceTapActions` / `PhotoWidgetTapAction` so GIF allows the same navigation/cycling actions as PHOTOS. Keep `ToggleGifPlayback` only if still useful for “pause animation of current GIF”; otherwise leave unused or map carefully so it does not replace `ToggleCycling`.

**Default taps for new GIF widgets:** Previous / FullScreen / Next (same as photos).

## Runtime — Home screen

```text
PhotoWidgetProvider.update
  → prepare poster / first frame of current GIF for initial RemoteViews
  → KeepAliveService setup GIF playback for CURRENT gifId only

GifPlaybackController
  → load frame URIs for current GIF only
  → loop at that GIF’s interval
  → on CyclePhotoUseCase (next/prev/timer/shuffle): tear down, switch current GIF, setup new loop

Screen off: pause animation (existing)
Cycle paused: do not advance GIF playlist; animation of current may continue (per product decision)
```

## Runtime — In-app

- Configure preview / My Widgets detail: animate selected/current GIF.
- Prefer decoding the stored `.gif` (Coil + GIF decoder or existing gif drawable) so UX feels “direct”.
- Home still uses extracted frames.

## Migration

Existing GIF widgets store **frames as photos**. On load/save when source is GIF:

1. Detect legacy layout (no `gifs/` folder / frames look like flat playlist with no gif grouping).
2. Treat as **one** GIF: group existing frames under a single `gifId`, set as sole playlist item.
3. If original GIF file is missing, keep frame-based animation only for that item.
4. One-time; after migration, playlist length = 1 GIF until user adds more.

## Battery / KeepAlive

- Unchanged requirement: GIF animation needs Keep Alive.
- Battery warning dismiss remains per widget.
- Cost scales with: animation of one GIF + occasional playlist updates (much better than treating hundreds of frames as navigable photos).

## Key files (expected)

| Area | Files |
|------|--------|
| Model / taps | `PhotoWidget.kt`, `PhotoWidgetTapAction.kt`, `PhotoWidgetSource.kt` |
| Pick / configure | `PhotoWidgetConfigureScreen.kt`, `…ViewModel.kt`, `…ContentTab.kt`, `…BehaviorTab.kt` |
| Storage | `PhotoWidgetInternalFileStorage.kt`, `PhotoWidgetStorage.kt` |
| Prepare / play | `PrepareGifPhotosUseCase.kt`, `GifPlaybackController.kt`, `KeepAliveService.kt` |
| Cycle | `CyclePhotoUseCase.kt`, `PhotoWidgetProvider.kt`, tap factory |
| Save | `SavePhotoWidgetUseCase.kt` |

## Test plan

1. Add 3 GIFs → content shows 3 items; preview animates selected.
2. Home: current GIF animates; left/right switches GIF; new one animates.
3. Timer + shuffle advance between GIFs without breaking animation restart.
4. Toggle cycling pauses playlist advances; current GIF still animates.
5. Legacy single-GIF widget opens as 1 GIF and still animates.
6. Delete one GIF from playlist; order and current index stay valid.
7. Battery warning + Keep Alive prompts still work when enabling GIF source.

## Open points (non-blocking)

- Whether `ToggleGifPlayback` remains as a separate tap action or is hidden once playlist cycling exists.
- Exact thumbnail strategy (static poster vs tiny in-app animation in the grid).
