# Streamify Android

Native Android player for `https://streamify-fixed.onrender.com`.

## What this build fixes

- Background / screen-off audio playback
- Android system media notification
- Lock-screen Play / Pause / Previous / Next
- Automatic next-track playback through Media3 queue
- Album art, title, artist metadata in system controls
- Foreground media playback service
- Audio focus / Media3 player behavior

Android's recommended architecture for persistent background playback is a `MediaSessionService`
hosting the player and media session.

## Build

Open this folder in Android Studio, let Gradle sync, then:

Build > Build APK(s)

or run:

`./gradlew assembleDebug`

## MP3 downloads

This project does **not** fake MP3 by renaming M4A/MP4 audio.

JioSaavn-compatible sources can return AAC/M4A or other source formats.
To guarantee true MP3 files, the backend needs an actual audio transcoder (for example FFmpeg/LAME)
and must only convert/download media when the source/provider permits it.

The native player and lock-screen controls are fully separated from that download/transcoding concern.