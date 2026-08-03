# GIF battery warning dismiss — Implementation Plan

> **For agentic workers:** Implement task-by-task. Steps use checkbox syntax.

**Goal:** Show the GIF battery warning once per widget until the user taps Accept/Got it.

**Architecture:** Persist `gifBatteryWarningDismissed` per `appWidgetId` in `PhotoWidgetSharedPreferences` (PreferencePrefix). Expose via `PhotoWidgetStorage`. Drive visibility from `PhotoWidgetConfigureState` + ViewModel dismiss action. Reuse `InformationalPanel` action button.

**Tech Stack:** Kotlin, Jetpack Compose, SharedPreferences, Hilt ViewModel

## Global Constraints

- Once per widget ID (not app-wide)
- Reuse `photo_widget_action_got_it` for the button
- Cleanup via existing PreferencePrefix delete/migrate

---

### Task 1: Persist dismiss flag

**Files:**
- Modify `PhotoWidgetSharedPreferences.kt`
- Modify `PhotoWidgetStorage.kt`

- [ ] Add `PreferencePrefix.GIF_BATTERY_WARNING_DISMISSED`
- [ ] Add get/save on SharedPreferences + Storage wrappers
- [ ] Confirm delete/migrate cover new prefix via `PreferencePrefix.entries`

### Task 2: Configure state + ViewModel

**Files:**
- Modify `PhotoWidgetConfigureState.kt`
- Modify `PhotoWidgetConfigureViewModel.kt`

- [ ] Add `showGifBatteryWarning: Boolean` to state (default true when unknown; load from storage in `updateState` / when source becomes GIF)
- [ ] Add `dismissGifBatteryWarning()` that saves true and sets `showGifBatteryWarning = false`
- [ ] When changing source to GIF, re-read flag for current widget

### Task 3: Wire UI

**Files:**
- Modify `PhotoWidgetConfigureContentTab.kt`
- Modify `PhotoWidgetConfigureScreen.kt` if callbacks need threading

- [ ] Show panel when `source == GIF && showGifBatteryWarning`
- [ ] `showActionButton = true`, onClick → ViewModel dismiss

### Task 4: Verify

- [ ] `installDebug` on device
- [ ] Manual: accept on widget A; create widget B still shows warning
