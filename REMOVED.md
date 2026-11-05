# Removed upstream extras

## Screens and navigation

- Removed the Ask a Question/contact flow, privacy and diagnostic data screen/toggle, guide/tutorial, What's New screen, donations screen, and rating/GitHub prompts.
- Removed the home-screen prompts, forced first-run guide, timer help link, and settings navigation entries that opened those screens.
- Removed the associated view models and Android guide screenshots/resources.

## Network and data collection

- Removed the startup ping, diagnostics upload implementation, donation activation request, and their author-server URLs.
- Removed Ktor client dependencies and Android's `INTERNET` permission, which were only needed by those requests.
- Removed the stored preference keys and helper code used only by diagnostics, changelog display, rating prompts, donations, feedback, and the guide.

## Examples and publishing assets

- Removed generated demo history support and its example screenshots, documentation source images, store screenshots, changelog files, and preset shortcut examples.
- Removed the prefilled starter activities/checklists and shortcut examples. A fresh install now creates only the required `Other` activity and initial interval, plus the structural Today/Tomorrow/Someday task folders.

## Apple platform code

- Removed the Apple app and watch project files/assets, Apple Kotlin source sets, Apple targets, and the native SQLDelight driver dependency.
- Removed Apple-only sorting/widget helpers and platform-specific startup workarounds from shared Kotlin code.
- Kept the shared database and Android resource initialization used by the Android app.

## Kept / uncertain

- `reportApi` remains as a local `zlog` compatibility helper for Android error paths. It no longer uploads anything; local error messages still go to logs.
- The Android OS version remains in backup metadata.
