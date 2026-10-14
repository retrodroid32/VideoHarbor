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
        ok(VideoUrlUtils.isYoutubeVideoPage("https://www.youtube.com/watch?v=jfKfPfyJRdk"), "YouTube watch");
        ok(VideoUrlUtils.isYoutubeVideoPage("https://m.youtube.com/shorts/jfKfPfyJRdk"), "YouTube shorts");
        ok(VideoUrlUtils.isYoutubeVideoPage("https://youtu.be/jfKfPfyJRdk?t=3"), "YouTube short link");
        ok(VideoUrlUtils.isYoutubeVideoPage("https://www.youtube-nocookie.com/embed/jfKfPfyJRdk"), "YouTube embedded");
        ok(!VideoUrlUtils.isYoutubeVideoPage("https://youtube.com/@channel"), "channel not a video");
        ok(!VideoUrlUtils.isYoutubeVideoPage("https://notyoutube.com/watch?v=jfKfPfyJRdk"), "unrelated host rejected");
        ok(!VideoUrlUtils.isYoutubeVideoPage("https://youtube.com/watch?v=bad"), "invalid video identifier");
        ok(VideoUrlUtils.isYoutubeStreamEndpoint("https://rr1---sn.googlevideo.com/videoplayback?itag=137"),
                "adaptive YouTube media endpoint");
        ok(!VideoUrlUtils.isYoutubeStreamEndpoint("https://googlevideo.com.evil.net/videoplayback"),
                "reject lookalike Googlevideo domain");
        System.out.println("PASS: 18 pure-Java video URL / filename checks");
    }
}
