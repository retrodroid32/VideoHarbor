package com.example.videoharbor;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Pure Java URL/filename policy; no WebView bridge exposed to untrusted pages. */
public final class VideoUrlUtils {
    private VideoUrlUtils() {}

    public static boolean isHttpUrl(String candidate) {
        if (candidate == null) return false;
        try {
            URI uri = new URI(candidate.trim());
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null && !uri.getHost().isEmpty()
                    && uri.getUserInfo() == null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    public static boolean isHttpsUrl(String candidate) {
        return isHttpUrl(candidate) && candidate.trim().toLowerCase(Locale.ROOT).startsWith("https://");
    }

    public static boolean isStream(String candidate) {
        String path = pathOf(candidate);
        return path.endsWith(".m3u8") || path.endsWith(".mpd");
    }

    public static boolean isVideoFile(String candidate) {
        String path = pathOf(candidate);
        return path.endsWith(".mp4") || path.endsWith(".m4v") || path.endsWith(".webm")
                || path.endsWith(".mov") || path.endsWith(".3gp") || path.endsWith(".mkv")
                || path.endsWith(".ts");
    }

    public static String extensionForMime(String mime) {
        if (mime == null) return ".mp4";
        switch (mime.toLowerCase(Locale.ROOT).split(";")[0].trim()) {
            case "video/webm": return ".webm";
            case "video/quicktime": return ".mov";
            case "video/x-matroska": return ".mkv";
            case "video/3gpp": return ".3gp";
            case "video/mp2t": return ".ts";
            default: return ".mp4";
        }
    }

    public static String safeFileName(String input, String mime, long timestamp) {
        String raw = input == null ? "" : input;
        // Remove path components, Unicode control characters and unsafe FAT characters.
        raw = raw.replace('\\', '/');
        raw = raw.substring(raw.lastIndexOf('/') + 1);
        raw = raw.replaceAll("[\\p{Cntrl}<>:\"/\\\\|?*]", "_")
                .replaceAll("\\s+", " ").trim();
        while (raw.startsWith(".")) raw = raw.substring(1);
        while (raw.endsWith(".") || raw.endsWith(" ")) raw = raw.substring(0, raw.length() - 1);
        if (raw.length() > 75) raw = raw.substring(0, 75);
        if (raw.isEmpty()) raw = "video";
        int dot = raw.lastIndexOf('.');
        String ext = dot > 0 ? raw.substring(dot).toLowerCase(Locale.ROOT) : "";
        if (!ext.matches("\\.(mp4|m4v|webm|mov|3gp|mkv|ts)")) {
            raw = (dot > 0 ? raw.substring(0, dot) : raw) + extensionForMime(mime);
            dot = raw.lastIndexOf('.');
            ext = raw.substring(dot);
        }
        String stem = raw.substring(0, raw.length() - ext.length()).trim();
        return stem + "_" + timestamp + ext;
    }

    public static String pathOf(String candidate) {
        try {
            String path = new URI(candidate).getPath();
            return path == null ? "" : path.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "";
        }
    }

    public static String hostOf(String candidate) {
        try {
            URI uri = new URI(candidate);
            return uri.getHost() == null ? "unknown host" : uri.getHost();
        } catch (Exception e) {
            return "unknown host";
        }
    }

    /** Recognize actual YouTube video pages without matching unrelated domains. */
    public static boolean isYoutubeVideoPage(String candidate) {
        if (!isHttpsUrl(candidate)) return false;
        try {
            URI uri = new URI(candidate.trim());
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (host.equals("youtu.be") || host.equals("www.youtu.be")) {
                return path.matches("/[A-Za-z0-9_-]{11}/?");
            }
            boolean youtubeHost = host.equals("youtube.com")
                    || host.endsWith(".youtube.com")
                    || host.equals("youtube-nocookie.com")
                    || host.endsWith(".youtube-nocookie.com");
            if (!youtubeHost) return false;
            if (path.matches("/(shorts|live|embed)/[A-Za-z0-9_-]{11}/?")) return true;
            if (!path.equals("/watch")) return false;
            String rawQuery = uri.getRawQuery();
            if (rawQuery == null) return false;
            for (String pair : rawQuery.split("&")) {
                if (pair.startsWith("v=") && pair.substring(2).matches("[A-Za-z0-9_-]{11}")) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /** A media CDN URL is not the same thing as a downloadable complete file. */
    public static boolean isYoutubeStreamEndpoint(String candidate) {
        if (!isHttpsUrl(candidate)) return false;
        try {
            URI uri = new URI(candidate.trim());
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            return (host.equals("googlevideo.com") || host.endsWith(".googlevideo.com"))
                    && (uri.getPath() != null && uri.getPath().contains("videoplayback"));
        } catch (Exception e) {
            return false;
        }
    }
}
