# Wallpaper hub — Implementation Plan

> Implement sequentially. Verify with compile + installDebug.

**Goal:** Left navbar “Fondos de pantalla” with Home/Lock configs, live wallpaper, photos/videos/GIFs/folders, double-tap next, timer, shuffle, linked mode.

**Architecture:** New `wallpaper` package: UI screen, prefs/storage, `ZenWallpaperService` (WallpaperService), scheduler. Reuse UI components and cycle-mode sheets where practical.

---

### Task 1: Navbar + empty Wallpaper screen
- [x] Icon + nav destination leftmost
- [x] Screen scaffold with Inicio/Bloqueo tabs + linked toggle

### Task 2: Storage model
- [x] WallpaperConfig prefs/files
- [x] Media import (photo/video/gif) + folder URIs

### Task 3: Live wallpaper service
- [x] Manifest service + picker intent
- [x] Draw current media; double-tap next
- [x] Apply / activate from UI

### Task 4: Behavior + scheduling
- [x] Cycle mode + shuffle UI
- [x] Short interval Keep Alive; else WorkManager
- [x] Lock static fallback + OEM hint

### Task 5: Install and smoke test
- [ ] installDebug + smoke test
