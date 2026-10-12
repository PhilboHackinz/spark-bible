# Spark Bible

A calm Android Bible app for days when focus is thin. Any amount counts. Missed days stay kind.

Spark Bible is a native Kotlin app (Jetpack Compose, Room, offline text). It is not a website.

## Install

Android 8 or newer.

The [v1.1.0 release](https://github.com/PhilboHackinz/spark-bible/releases/tag/v1.1.0) has a signed APK. Download it, open it, and allow install from your browser or files app if Android asks.

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
gradle :app:assembleDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`.

A release build is signed only when these are set:

```sh
export SPARK_STORE_FILE=/path/to/release.jks
export SPARK_STORE_PASSWORD=...
export SPARK_KEY_ALIAS=spark
export SPARK_KEY_PASSWORD=...
gradle :app:assembleRelease
```

The signing key is not in this repository.

## Fonts

Literata, Fraunces, and Lexend are used under the SIL Open Font License.

## License

The scripture texts above are in the public domain.

The app code in this repository does not have an open-source license yet. All rights stay with the repository owner unless a license is added later.
