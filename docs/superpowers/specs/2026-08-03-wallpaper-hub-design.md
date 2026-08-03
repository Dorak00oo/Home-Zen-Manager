# Wallpaper hub (Fondos de pantalla) — Design

**Date:** 2026-08-03  
**Status:** Approved — implement now; iterate on feedback later  
**Approach:** A — one Live Wallpaper engine + Home/Lock configs; lock falls back to static cycle when OEM blocks live

## Decisions (locked)

| Topic | Choice |
|-------|--------|
| Engine | Live wallpaper |
| Double tap | Next media |
| Timer | Same model as widgets (intervals / disabled / etc.) |
| Home vs Lock | Independent configs + **“Usar lo mismo en ambas”** (linked) |
| Media | Photos, videos, GIFs; single picks and/or folders |
| Background | Keep Alive only for short intervals; else WorkManager/alarms |
| OEM | Support home+lock where possible; warn when lock limited |
| UI | Tab/sections **Inicio \| Bloqueo** |
| Scope | Full feature (not phased) |
| Widgets | Fully separate (no shared library with widgets) |

## Navbar

Add leftmost item: **Fondos de pantalla** (same NavigationBar patterns/icons style as existing tabs).

Order: **Fondos** · Nuevo widget · Mis widgets · Ajustes

## Screen structure

```
WallpaperScreen
  [Toggle] Usar lo mismo en ambas
  Tabs: Inicio | Bloqueo   (Bloqueo disabled/synced UI when linked)
  Content: media grid + pickers (photo/video/gif/folder)
  Behavior: double-tap info (fixed = next), cycle mode picker, shuffle
  CTA: Activar fondo / Aplicar
```

## Data model (separate from widgets)

```
WallpaperSurface = HOME | LOCK
WallpaperConfig
  linked: Boolean
  home: WallpaperSurfaceConfig
  lock: WallpaperSurfaceConfig

WallpaperSurfaceConfig
  media: List<WallpaperMedia>  // ordered playlist
  folders: Set<Uri>
  shuffle: Boolean
  cycleMode: PhotoWidgetCycleMode (reuse type) or dedicated WallpaperCycleMode
  currentId: String?
```

Media kinds: PHOTO, VIDEO, GIF (path/uri + id).

## Runtime

### Live wallpaper (`WallpaperService`)
- Renders current item (still / GIF frames or drawable / video player)
- Double-tap → next (respect shuffle)
- Timer advances playlist
- When linked, both surfaces share home config visually if system uses one engine

### Lock screen
- Prefer same live wallpaper when OEM applies it to lock
- Else set static bitmaps on `FLAG_LOCK` on each cycle + show OEM limitation hint

### Scheduling
- Interval &lt; threshold (e.g. &lt; 15 min) → Keep Alive / foreground path
- Else → WorkManager / existing alarm patterns

## Out of scope / honesty
- True independent live engines on lock+home on all OEMs is not guaranteed
- Video/GIF battery cost similar to animated widgets when Keep Alive is on
