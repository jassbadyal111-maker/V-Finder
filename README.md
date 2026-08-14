# V-Finder

A modern native Android app for finding a person's matching data inside a selected local file.

## UI

The app follows the V-Finder visual direction: deep indigo background, cyan/purple logo, rounded Material 3 cards, prominent search action, loading state, and detailed result cards.

## Supported input

The first release supports delimiter-separated text data such as CSV/TSV/TXT. Each row is matched against the entered person name and displayed as a structured result.

## Build with GitHub Actions

Push to `main` or manually run **Build V-Finder APK** from the Actions tab. CI uses Gradle 8.10.2 directly (so a missing local Gradle wrapper cannot break the build), then uploads `app-debug.apk` as the `V-Finder-debug` artifact.

## Run locally

Open the repository in Android Studio and run the `app` module on an Android 8.0+ device/emulator.
