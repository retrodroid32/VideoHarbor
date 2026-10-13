package com.example.videoharbor;

public final class VideoCandidate {
    public final String url;
    public final String label;
    public final String mimeType;
    public final String sourcePage;

    public VideoCandidate(String url, String label, String mimeType, String sourcePage) {
        this.url = url;
        this.label = label == null || label.trim().isEmpty() ? "Video file" : label.trim();
        this.mimeType = mimeType == null ? "" : mimeType;
        this.sourcePage = sourcePage == null ? "" : sourcePage;
    }

    public boolean isStream() {
        return VideoUrlUtils.isStream(url)
                || mimeType.toLowerCase(java.util.Locale.ROOT).contains("mpegurl")
                || mimeType.toLowerCase(java.util.Locale.ROOT).contains("dash+xml");
    }
}
