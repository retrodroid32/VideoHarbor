import com.example.videoharbor.VideoUrlUtils;

public class VideoUrlUtilsTest {
    private static void ok(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        ok(VideoUrlUtils.isHttpUrl("https://cdn.example.com/a.mp4?x=1"), "valid HTTPS");
        ok(!VideoUrlUtils.isHttpUrl("file:///etc/passwd"), "reject file scheme");
        ok(!VideoUrlUtils.isHttpUrl("javascript:alert(1)"), "reject script scheme");
        ok(!VideoUrlUtils.isHttpUrl("https://user:pw@example.org/file.mp4"), "reject embedded credentials");
        ok(VideoUrlUtils.isVideoFile("https://cdn.example.com/file.MP4?token=123"), "MP4 detection");
        ok(VideoUrlUtils.isStream("https://example.org/live/playlist.M3U8?key=123"), "HLS detection");
        ok(!VideoUrlUtils.isVideoFile("https://example.org/other.mp4.txt"), "no false extension");
        String name = VideoUrlUtils.safeFileName("../unsafe:movie?name.mp4", "video/mp4", 12345);
        ok(name.equals("unsafe_movie_name_12345.mp4"), "filename: " + name);
        ok(VideoUrlUtils.safeFileName("", "video/webm", 777).equals("video_777.webm"), "empty filename");
        System.out.println("PASS: 9 pure-Java video URL / filename checks");
    }
}
