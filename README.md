# Metaverse (Android gallery app)

Kotlin + Jetpack Compose. Dark "personal museum" gallery: onboarding, stamp calendar, polaroid pop-up, Today's Board, glass search bar.

## Build the APK
1. Open the `Metaverse` folder in Android Studio (Koala or newer). It syncs Gradle and creates the wrapper.
2. Build > Build Bundle(s) / APK(s) > Build APK(s), or press Run on a device.
   APK path: `app/build/outputs/apk/debug/app-debug.apk`

## Features
- Haptics: `util/Haptics.kt` (tick on month change / pager, click on taps, heavy on Enter and +, confirm on playback end and picks).
- Playable animation: tap the play or mic buttons on the Board; waveform animates and fills with progress.
- Auto-detect photos: `data/MediaRepository.kt` reads MediaStore and listens with a ContentObserver, so new photos appear live.
  Google Photos items backed up or synced to the phone appear there. Google's Photos Library API no longer lets
  third-party apps read a user's whole library, so cloud-only items are not available; use the + button (system photo picker) to add specific ones.
- Tap a day with photos for the polaroid pop-up, then "View Full Museum" for the grid. Tap an empty day to cycle its mood icon.

## Placeholders to replace
- "Lagos" and "A Rainy Afternoon" in `MuseumScreen.kt` (`FooterRow`) are static text from the design.
- Handwritten font is `FontFamily.Cursive`; drop "Patrick Hand" into `res/font` and set `Hand` in `Theme.kt` for a closer match.
- Playback is visual only; wire `PlayPill` to MediaPlayer/ExoPlayer for real audio.
# Metaverse
