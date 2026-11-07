package kr.realitylink.ai;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

/** The capture thread alone opens, reads and releases native camera resources. */
public final class CameraService {
    public record Frame(BufferedImage image, long receivedNanos) { }
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicReference<Frame> latest = new AtomicReference<>();
    private volatile boolean stop;

    public boolean isRunning() { return running.get(); }
    public Frame latest() { return latest.get(); }
    public Frame freshFrame() {
        Frame frame = latest.get();
        if (!running.get() || frame == null || System.nanoTime() - frame.receivedNanos() > 2_000_000_000L)
            throw new IllegalStateException("촬영 실패: 2초 이내 수신한 카메라 프레임이 없습니다. 다시 연결하세요.");
        return frame;
    }

    public void start(int index, int backend, Consumer<String> status, Runnable finished) {
        if (!running.compareAndSet(false, true)) throw new IllegalStateException("이미 카메라가 실행 중입니다.");
        stop = false;
        latest.set(null);
        Thread thread = new Thread(() -> {
            VideoCapture capture = null;
            Mat mat = null;
            try {
                nu.pattern.OpenCV.loadLocally();
                capture = new VideoCapture();
                if (!capture.open(index, backend) || !capture.isOpened())
                    throw new IllegalStateException("카메라 연결 실패. 인덱스/백엔드/Windows 카메라 권한/다른 앱 점유를 확인하세요.");
                capture.set(Videoio.CAP_PROP_FRAME_WIDTH, 640);
                capture.set(Videoio.CAP_PROP_FRAME_HEIGHT, 480);
                mat = new Mat();
                int failures = 0;
                boolean announced = false;
                while (!stop) {
                    if (!capture.read(mat) || mat.empty()) {
                        latest.set(null);
                        if (++failures >= 10) throw new IllegalStateException("카메라 프레임 읽기 10회 연속 실패. 재연결하세요.");
                        Thread.sleep(100);
                        continue;
                    }
                    failures = 0;
                    latest.set(new Frame(toImage(mat), System.nanoTime()));
                    if (!announced) { status.accept("카메라 프레임 수신 중. 물체를 중앙에 놓고 촬영하세요."); announced = true; }
                    Thread.sleep(15);
                }
            } catch (Exception | LinkageError e) {
                if (!stop) status.accept("카메라 오류: " + e.getMessage());
            } finally {
                if (mat != null) mat.release();
                if (capture != null) capture.release();
                latest.set(null);
                running.set(false);
                finished.run();
            }
        }, "opencv-camera");
        // A driver may block indefinitely in native open/read; it must not keep a closed app alive.
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() { stop = true; }

    static BufferedImage toImage(Mat mat) {
        if (mat.channels() != 3 || mat.depth() != org.opencv.core.CvType.CV_8U)
            throw new IllegalArgumentException("카메라 영상은 8비트 BGR 3채널이어야 합니다.");
        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), BufferedImage.TYPE_3BYTE_BGR);
        byte[] bytes = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        if (mat.get(0, 0, bytes) != bytes.length) throw new IllegalStateException("프레임 복사 실패");
        return image;
    }
}
