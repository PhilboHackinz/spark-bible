# Spark Bible

A calm Android Bible app for days when focus is thin. Any amount counts. Missed days stay kind.

Spark Bible is a native Kotlin app (Jetpack Compose, Room, offline text). It is not a website.

## Install

Android 8 or newer.

The [v1.8.2 release](https://github.com/PhilboHackinz/spark-bible/releases/tag/v1.8.2) has a signed APK. Download it, open it, and allow install from your browser or files app if Android asks. Installing over 1.8.1 keeps your garden, highlights, and place.

If you later install a build you made yourself, uninstall the release first. A build from source is signed with a different key.

## What it does

- Energy check starts at one verse. A little more adds eight verses and, if you were listening, continues from there instead of starting over
- Read, listen, or both. Phone voices highlight the verse as it is spoken. Kokoro highlights the verse, not each word, and prepares the next few lines ahead
- Closing the app stops the voice
- The screen stays awake while you are reading
- A garden that sprouts and blooms, or a sky that forms shapes. Nothing resets. Finished books stand as mini books on a coffee table in the garden
- If you are already in today's spark, Today does not show that same passage a second time
- Daily spark widget opens today's verse. Reminders stay gentle
- Translations on the phone, switchable in Settings. No online Bible connection
- The phone's own voice is the default. Kokoro is optional: a full voice (about 300 MB) or a smaller voice, each downloaded only when you choose it. You can also choose the phone's main processor or its AI chip. RHVoice, eSpeak NG, or SherpaTTS still show up if they are installed
- Settings can save a copy of your garden, highlights, and place, and bring that file back. No account

Your place, highlights, and garden stay on the phone. There is no account.

## Translations

These ship inside the app:

| Id | Text |
| --- | --- |
| web | World English Bible (public domain) |
| kjv | King James Version (public domain) |
| asv | American Standard Version (public domain) |
| geneva | Geneva Bible, 1599 (public domain) |
| dra | Douay-Rheims American Edition, 1899 (public domain) |
| bsb | Berean Standard Bible, public domain April 30, 2023 |
| lsv | Literal Standard Version. Wording unchanged. Copyright © 2020 Covenant Press and the Covenant Christian Coalition. CC BY-SA 4.0 |

The older public-domain files were prepared from [midvash/bible-data](https://github.com/midvash/bible-data). LSV was prepared from the eBible.org USFM release. Credits are in `app/src/main/assets/licenses/`.

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

The signing key is not in this repository. Saved gardens, highlights, and places are kept when the database changes. Version 1 to 2 adds an index. Version 2 to 3 adds the list of finished books.

## Fonts

Literata, Fraunces, and Lexend are used under the SIL Open Font License, Version 1.1. The license and copyright lines ship in `app/src/main/assets/licenses/`.

## License

The app code is under the MIT License. See [LICENSE](LICENSE).

Public-domain scripture is not part of that grant. The Literal Standard Version stays under CC BY-SA 4.0; the wording is unchanged. Fonts stay under the SIL Open Font License. The optional Kokoro voice uses sherpa-onnx (Apache 2.0) and ONNX Runtime (MIT). See [NOTICE](NOTICE).
