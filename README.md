# VideoHarbor — Android Video Downloader (v1.1)

VideoHarbor is a native Android application for finding and downloading directly accessible videos from **general webpages** (not tied to any particular site).

## Features

- Built-in browser and address/search bar (HTTPS only).
- **Find videos** scans HTML5 video/source tags, links, metadata, and URLs of media resources seen by the browser.
- Recognizes direct MP4, WebM, M4V, MOV, MKV, 3GP and TS files.
- Select a result and confirm the download.
- **Try page extractor** for public YouTube videos and other sites supported by yt-dlp.
- Choose 360p / 720p / 1080p (where offered), download audio/video streams and merge with FFmpeg.
- Shows extractor download progress and a detailed failure message; cancel is supported.
- Saves completed extractor media with Android MediaStore to `Downloads/VideoHarbor`.
- Direct MP4/WebM downloads still use Android Download Manager in `Downloads/VideoHarbor`.
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
3. Tap **Find videos**. For direct MP4/WebM files select **Download**; for YouTube or other streaming pages, select **Try page extractor** or the extractor item.
4. Choose resolution in the page-extractor dialog. Leave the app open while the media downloads and audio/video merge finishes.
5. Check `Downloads/VideoHarbor` for the saved video.

## Limitations

Direct files are downloaded with Android Download Manager; video-page extraction uses yt-dlp + FFmpeg where those tools support a given page. YouTube availability varies by video, geography, website changes, and sign-in requirements. The new feature is **not guaranteed** to download all YouTube videos. No DRM, protected, private, paid, or restricted streams are bypassed. The extractor does not import browser cookies, and the app must stay open while extraction completes. Large videos need free temporary storage. Use only for videos you own or are authorized to save.

The extractor includes **GPL-3.0** `youtubedl-android` and its bundled yt-dlp runtime; see [upstream license](https://github.com/yausername/youtubedl-android/blob/master/LICENSE). FFmpeg and other bundled components carry their own third-party licenses. App distribution must respect the relevant licenses.

## Tests

From the repository root (with `javac` and Node.js installed):

```sh
mkdir -p /tmp/videoharbor-testclasses
javac -d /tmp/videoharbor-testclasses app/src/main/java/com/example/videoharbor/VideoUrlUtils.java tests/VideoUrlUtilsTest.java
java -cp /tmp/videoharbor-testclasses VideoUrlUtilsTest
node tests/video_detector.test.js
```

**Status:** v1.1 source staged for CI. Native Android build and actual YouTube download behavior must be verified separately.
