# GIF battery warning — per-widget dismiss

**Date:** 2026-08-02  
**Status:** Implemented

## Problem

The configure screen always shows `warning_gif_widget_battery_usage` whenever the widget source is GIF. Users want to dismiss it with an Accept button, and only see it the first time for **each** GIF widget.

## Decision

- **Scope:** Once per `appWidgetId` (not app-wide).
- **Storage:** Boolean in `PhotoWidgetSharedPreferences`, keyed by widget ID (same pattern as other widget prefs).
- **UI:** Existing `InformationalPanel` with `showActionButton = true` and `photo_widget_action_got_it` (localized “Got it” / equivalent).

## Behavior

1. Show the warning when `source == GIF` **and** `gifBatteryWarningDismissed` is `false` for that widget.
2. Tapping the action button:
   - Persists `gifBatteryWarningDismissed = true` for `effectiveWidgetId`.
   - Hides the panel immediately (state update).
3. Creating or configuring a **different** GIF widget shows the warning again until accepted on that widget.
4. Re-opening configure for a widget that already dismissed does **not** show the warning.
5. Deleting a widget removes the flag via existing `PreferencePrefix` cleanup in `deleteWidgetData` / draft→widget `migrateWidgetData`.

## Implementation sketch

| Area | Change |
|------|--------|
| `PhotoWidgetSharedPreferences` | Add `PreferencePrefix.GIF_BATTERY_WARNING_DISMISSED`; get/save helpers |
| `PhotoWidgetStorage` | Thin wrappers (if other widget prefs go through storage) |
| `PhotoWidgetConfigureState` | `showGifBatteryWarning: Boolean` (or derive from photoWidget + flag) |
| `PhotoWidgetConfigureViewModel` | Load flag on state load; `dismissGifBatteryWarning()` saves + updates state |
| `PhotoWidgetConfigureContentTab` | Gate visibility; wire `showActionButton` / `onActionButtonClick` |

## Out of scope

- App-wide “never show again”
- Changing warning copy
- Separate Spanish-only “Aceptar” string (reuse existing got-it action unless product wants a new key)

## Test plan

- New GIF widget: warning visible; Accept hides it; reopen configure → still hidden.
- Second GIF widget: warning visible again until Accept.
- Non-GIF source: warning never shown.
- Delete widget / recreate: warning can show again for the new ID.
