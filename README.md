# VideoHarbor — Android Video Downloader

VideoHarbor is a native Android application for finding and downloading directly accessible videos from **general webpages** (not tied to any particular site).

## Features

- Built-in browser and address/search bar (HTTPS only).
- **Find videos** scans HTML5 video/source tags, links, metadata, and URLs of media resources seen by the browser.
- Recognizes direct MP4, WebM, M4V, MOV, MKV, 3GP and TS files.
- Select a result and confirm the download.
- Saves using Android Download Manager to `Downloads/VideoHarbor`.
- Supports cookies, user-agent and referrer for compatible source servers.
- Android 8.0+ (minSdk 26); no analytics or JS-to-native interfaces.

## Get the APK from GitHub

1. Open **Actions → Build Android APK**.
2. Choose **Run workflow** on `main`.
3. After a successful build, download the **VideoHarbor-debug-apk** artifact from its run page.
4. Extract the artifact and install `app-debug.apk` on your Android phone.

The action uses Java 17, Gradle 8.11.1, AGP 8.9.2 and Android SDK 35. APKs are debug builds for testing.

## Build locally

Open the repository in Android Studio with SDK 35 and JDK 17. Use **Build → Build Bundle(s) / APK(s) → Build APK(s)**. The output is `app/build/outputs/apk/debug/app-debug.apk`.

## How to use

1. Paste a webpage URL and tap **GO**.
2. Start/play the video if needed.
3. Tap **Find videos**, select a direct file and choose **Download**.
4. Check `Downloads/VideoHarbor` for the saved video.

## Limitations

This first version downloads direct files only. It does not assemble HLS (`.m3u8`) or DASH (`.mpd`) playlists, extract `blob:`/MSE playback, or bypass DRM, access restrictions, or paywalls. Some websites reject downloads even if the video plays normally. Only download content you own or are authorized to save.

## Tests

From the repository root (with `javac` and Node.js installed):

```sh
mkdir -p /tmp/videoharbor-testclasses
javac -d /tmp/videoharbor-testclasses app/src/main/java/com/example/videoharbor/VideoUrlUtils.java tests/VideoUrlUtilsTest.java
java -cp /tmp/videoharbor-testclasses VideoUrlUtilsTest
node tests/video_detector.test.js
```

**Status:** Source uploaded; basic Java and JavaScript tests passed locally. An Android APK build and on-device operation require separate validation.
