# Spark Bible

A calm Android Bible app for days when focus is thin. Any amount counts. Missed days stay kind.

Spark Bible is a native Kotlin app (Jetpack Compose, Room, offline text). It is not a website.

## Install

Android 8 or newer.

The [v1.2.0 release](https://github.com/PhilboHackinz/spark-bible/releases/tag/v1.2.0) has a signed APK. Download it, open it, and allow install from your browser or files app if Android asks.

If you later install a build you made yourself, uninstall the release first. A build from source is signed with a different key.

## What it does

- Energy check: one verse, a short passage, or a longer stretch
- Read, listen, or both, with follow-along highlighting
- Listening keeps going if you leave the app
- A garden that only grows
- Daily spark widget and gentle reminders
- Five public-domain translations, switchable in Settings
- Phone voices, plus RHVoice, eSpeak NG, or SherpaTTS once those engines are installed

Your place, highlights, and garden stay on the phone. There is no account.

## Translations

All five ship inside the app and are public domain:

| Id | Text |
| --- | --- |
| web | World English Bible |
| kjv | King James Version |
| asv | American Standard Version |
| geneva | Geneva Bible (1599) |
| dra | Douay-Rheims American Edition (1899) |

Verse text is stored as JSON under `app/src/main/assets/bible/`. The files were prepared from the public-domain texts in [midvash/bible-data](https://github.com/midvash/bible-data).

## Build

You need JDK 17 and the Android SDK (compile SDK 35).

```sh
export ANDROID_HOME="$HOME/Android/Sdk"
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`.

A release build is signed only when these are set:

```sh
export SPARK_STORE_FILE=/path/to/release.jks
export SPARK_STORE_PASSWORD=...
export SPARK_KEY_ALIAS=spark
export SPARK_KEY_PASSWORD=...
./gradlew :app:assembleRelease
```

The signing key is not in this repository. Saved gardens, highlights, and places are kept when the database changes. Version 1 to 2 only adds an index.

## Fonts

Literata, Fraunces, and Lexend are used under the SIL Open Font License, Version 1.1. The license and copyright lines ship in `app/src/main/assets/licenses/`.

## License

The app code is under the MIT License. See [LICENSE](LICENSE).

The scripture texts are public domain and are not part of that grant. Fonts stay under the SIL Open Font License. See [NOTICE](NOTICE).
