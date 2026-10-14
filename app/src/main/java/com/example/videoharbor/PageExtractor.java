package com.example.videoharbor;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.yausername.youtubedl_android.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Extractor fallback for video pages, including public YouTube videos.
 *
 * yt-dlp and FFmpeg operate on app-private temporary files; only completed
 * media is copied into public Downloads via MediaStore or legacy storage.
 * Unlike DownloadManager, adaptive audio/video streams can be joined.
 * The activity needs to stay open while a download is in progress.
 */
public final class PageExtractor {
    private static final String[] QUALITIES = {
            "720p (recommended)", "1080p", "360p"
    };
    private static final int[] HEIGHTS = {720, 1080, 360};
    private static final String TEMP_PREFIX = "job-";

    private final Activity activity;
    private final java.util.function.Consumer<String> statusCallback;
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final AtomicBoolean cancel = new AtomicBoolean(false);
    private volatile String processId;

    public PageExtractor(Activity activity, java.util.function.Consumer<String> statusCallback) {
        this.activity = activity;
        this.statusCallback = statusCallback;
    }

    public void prompt(String url) {
        if (!VideoUrlUtils.isHttpsUrl(url)) {
            notice("Unsupported page", "Open a valid HTTPS video page first.");
            return;
        }
        if (busy.get()) {
            notice("Download already running", "Wait for the current download to finish or cancel it.");
            return;
        }
        final int[] quality = {0};
        String message = VideoUrlUtils.isYoutubeVideoPage(url)
                ? "Use the video-page extractor for this YouTube link. Video and audio may be downloaded separately and then combined."
                : "Try extracting a video from this webpage. Not every website is supported.";
        new AlertDialog.Builder(activity)
                .setTitle("Extract a video")
                .setMessage(message + "\n\nChoose quality (where available).")
                .setSingleChoiceItems(QUALITIES, 0, (d, choice) -> quality[0] = choice)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Download", (d, which) -> start(url, HEIGHTS[quality[0]]))
                .show();
    }

    private void start(String url, int height) {
        if (!busy.compareAndSet(false, true)) return;
        cancel.set(false);

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * activity.getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        TextView message = new TextView(activity);
        message.setText("Preparing extractor…\nKeep VideoHarbor open while downloading.");
        message.setTextSize(14);
        message.setGravity(Gravity.CENTER_VERTICAL);
        layout.addView(message);
        ProgressBar progress = new ProgressBar(activity, null,
                android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setIndeterminate(true);
        layout.addView(progress);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Downloading video")
                .setView(layout)
                .setCancelable(false)
                .setNegativeButton("Cancel download", (d, w) -> {
                    cancel.set(true);
                    stopProcess();
                    showStatus("Download cancelled");
                })
                .create();
        dialog.show();

        new Thread(() -> download(url, height, message, progress, dialog),
                "VideoHarbor-extractor").start();
    }

    private void download(String url, int height, TextView message,
                          ProgressBar progress, AlertDialog dialog) {
        File jobDir = null;
        try {
            showStatus("Preparing video extractor");
            YoutubeDL.getInstance().init(activity.getApplicationContext());
            FFmpeg.getInstance().init(activity.getApplicationContext());
            if (cancel.get()) return;

            File tempParent = activity.getExternalFilesDir(Environment.DIRECTORY_MOVIES);
            if (tempParent == null) tempParent = activity.getFilesDir();
            jobDir = new File(tempParent, TEMP_PREFIX + System.currentTimeMillis());
            if (!jobDir.mkdirs()) throw new IllegalStateException("Cannot create temporary storage");
            String jobId = jobDir.getName();
            processId = jobId;

            YoutubeDLRequest request = new YoutubeDLRequest(url);
            request.addOption("--ignore-config");
            request.addOption("--no-playlist");
            request.addOption("--max-downloads", 1);
            request.addOption("--no-mtime");
            request.addOption("--restrict-filenames");
            request.addOption("-f", formatForHeight(height));
            request.addOption("--merge-output-format", "mp4");
            request.addOption("-o",
                    new File(jobDir, "%(title).75B_[%(id)s].%(ext)s").getAbsolutePath());

            updateUi(message, progress, "Connecting to video source…", -1);
            YoutubeDL.getInstance().execute(request, processId, (percentage, etaSeconds) -> {
                if (cancel.get()) return;
                int p = Math.min(100, Math.max(0, Math.round(percentage)));
                updateUi(message, progress,
                        "Downloading: " + p + "% | ETA " + Math.max(0, etaSeconds) + " sec", p);
            });

            if (cancel.get()) return;
            updateUi(message, progress, "Saving to Downloads/VideoHarbor…", -1);
            File completed = findCompletedVideo(jobDir);
            if (completed == null) {
                throw new IllegalStateException("No finished video file was produced. The site may only offer audio, a protected stream, or an unsupported format.");
            }
            Uri saved = saveToDownloads(completed);
            if (cancel.get()) return;
            String location = "Downloads/VideoHarbor/" + VideoUrlUtils.safeFileName(
                    completed.getName(), mimeFor(completed), 0);
            showStatus("Video saved to Downloads/VideoHarbor");
            post(() -> {
                if (!activity.isFinishing() && !activity.isDestroyed()) {
                    dialog.dismiss();
                    new AlertDialog.Builder(activity)
                            .setTitle("Video saved")
                            .setMessage("The completed video was saved to Downloads/VideoHarbor.\n\n"
                                    + "Open your Files app to watch it.")
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
        } catch (Exception e) {
            if (!cancel.get()) {
                String error = meaningfulError(e);
                showStatus("Video extraction failed");
                post(() -> {
                    dialog.dismiss();
                    notice("Download failed",
                            error + "\n\nSome sites require sign-in, reject extraction, or use DRM. "
                            + "Direct MP4/WebM downloads are still available for other websites.");
                });
            }
        } finally {
            processId = null;
            if (jobDir != null) deleteTree(jobDir);
            busy.set(false);
            post(dialog::dismiss);
        }
    }

    /** Prefer MP4+M4A, then other compatible video+audio pairs or a single video file. */
    static String formatForHeight(int height) {
        if (height != 360 && height != 720 && height != 1080) height = 720;
        return "bv[height<=" + height + "][ext=mp4]+ba[ext=m4a]"
                + "/b[height<=" + height + "][ext=mp4]"
                + "/bv[height<=" + height + "]+ba"
                + "/b[height<=" + height + "]"
                + "/b";
    }

    private void stopProcess() {
        String current = processId;
        if (current == null) return;
        new Thread(() -> {
            try {
                YoutubeDL.getInstance().destroyProcessById(current);
            } catch (Exception ignored) {
                // It may have already stopped.
            }
        }, "VideoHarbor-cancel").start();
    }

    private File findCompletedVideo(File folder) {
        File[] files = folder.listFiles();
        if (files == null) return null;
        File largest = null;
        for (File f : files) {
            if (!f.isFile()) continue;
            String name = f.getName().toLowerCase(Locale.ROOT);
            if (!(name.endsWith(".mp4") || name.endsWith(".webm")
                    || name.endsWith(".mkv") || name.endsWith(".mov"))) continue;
            if (largest == null || f.length() > largest.length()) largest = f;
        }
        return largest;
    }

    private String mimeFor(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".webm")) return "video/webm";
        if (name.endsWith(".mkv")) return "video/x-matroska";
        if (name.endsWith(".mov")) return "video/quicktime";
        return "video/mp4";
    }

    private Uri saveToDownloads(File source) throws Exception {
        String mime = mimeFor(source);
        String filename = VideoUrlUtils.safeFileName(
                source.getName(), mime, System.currentTimeMillis());
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/VideoHarbor");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = activity.getContentResolver().insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("Cannot create Downloads entry");
            try {
                try (InputStream in = new FileInputStream(source);
                     OutputStream out = activity.getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IllegalStateException("Cannot open Downloads entry");
                    copy(in, out);
                }
                ContentValues ready = new ContentValues();
                ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
                activity.getContentResolver().update(uri, ready, null, null);
                return uri;
            } catch (Exception e) {
                activity.getContentResolver().delete(uri, null, null);
                throw e;
            }
        }
        if (activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            throw new IllegalStateException("Storage permission is required for Android 8/9");
        }
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "VideoHarbor");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Cannot create Downloads/VideoHarbor");
        }
        File outFile = new File(dir, filename);
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(outFile)) {
            copy(in, out);
        }
        android.media.MediaScannerConnection.scanFile(
                activity, new String[]{outFile.getAbsolutePath()}, new String[]{mime}, null);
        return Uri.fromFile(outFile);
    }

    private void copy(InputStream in, OutputStream out) throws Exception {
        byte[] chunk = new byte[64 * 1024];
        int n;
        while ((n = in.read(chunk)) != -1) {
            if (cancel.get()) throw new IllegalStateException("Download cancelled");
            out.write(chunk, 0, n);
        }
        out.flush();
    }

    private void deleteTree(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        file.delete();
    }

    private void post(Runnable work) {
        activity.runOnUiThread(work);
    }

    private void updateUi(TextView label, ProgressBar bar, String msg, int percent) {
        showStatus(msg);
        post(() -> {
            if (activity.isDestroyed()) return;
            label.setText(msg + "\nKeep VideoHarbor open while downloading.");
            bar.setIndeterminate(percent < 0);
            if (percent >= 0) bar.setProgress(percent);
        });
    }

    private void showStatus(String status) {
        post(() -> {
            if (!activity.isDestroyed()) statusCallback.accept(status);
        });
    }

    private void notice(String title, String message) {
        if (activity.isDestroyed() || activity.isFinishing()) return;
        new AlertDialog.Builder(activity).setTitle(title)
                .setMessage(message).setPositiveButton("OK", null).show();
    }

    private String meaningfulError(Exception failure) {
        String text = failure.getMessage();
        if (text == null || text.trim().isEmpty()) return failure.getClass().getSimpleName();
        text = text.replaceAll("(?i)(cookie|authorization|token|sig|signature)=[^\\s&]+", "$1=[hidden]");
        if (text.length() > 900) text = text.substring(0, 900) + "...";
        return text;
    }
}
