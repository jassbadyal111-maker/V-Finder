# V-Finder

A native Android app for finding matching person records inside a selected local data file.

## Supported input

- CSV
- TSV
- TXT
- JSON (JSON arrays of objects and objects containing a `data` array)

Search runs locally on the selected file. No network permission is required by the app.

## Build

The repository includes a portable Gradle launcher targeting Gradle 8.10.2.

### Android Studio

Open the repository and run the `app` configuration.

### Command line

Linux/macOS:

```bash
chmod +x gradlew
./gradlew testDebugUnitTest assembleDebug
```

Windows:

```bat
gradlew.bat testDebugUnitTest assembleDebug
```

## GitHub Actions

Every push to `main` runs parser unit tests and builds `app-debug.apk`. The APK is uploaded as the `V-Finder-debug` workflow artifact.

## Notes

XLSX is intentionally not advertised as supported until a dedicated spreadsheet parser is added. The current implementation avoids shipping a large spreadsheet dependency for a feature that was not previously functional.
