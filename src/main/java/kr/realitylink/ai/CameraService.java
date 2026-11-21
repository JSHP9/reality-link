
package kr.realitylink.ai;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

// OpenCV 카메라 영상 관리
public final class CameraService {

    private volatile boolean running;
    private volatile boolean stop;
    private volatile BufferedImage latest;

    public boolean isRunning() {
        return running;
    }

    public BufferedImage latest() {
        return latest;
    }

    // 카메라 실행
    public void start(int index, int backend, Runnable finished) {
        if (running) return;

        running = true;
        stop = false;
        latest = null;

        Thread thread = new Thread(() -> {
            VideoCapture capture = null;
            Mat mat = null;

            try {
                nu.pattern.OpenCV.loadLocally();

                capture = new VideoCapture();
                mat = new Mat();

                if (!capture.open(index, backend))
                    return;

                capture.set(Videoio.CAP_PROP_FRAME_WIDTH, 640);
                capture.set(Videoio.CAP_PROP_FRAME_HEIGHT, 480);

                while (!stop) {
                    if (!capture.read(mat) || mat.empty())
                        break;

                    latest = toImage(mat);
                }

            } finally {
                if (mat != null) mat.release();
                if (capture != null) capture.release();

                latest = null;
                running = false;
                finished.run();
            }
        }, "camera");

        thread.setDaemon(true);
        thread.start();
    }

    // 카메라 종료 요청
    public void stop() {
        stop = true;
    }

    // OpenCV 이미지를 Java 이미지로 변환
    private static BufferedImage toImage(Mat mat) {
        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), BufferedImage.TYPE_3BYTE_BGR);

        byte[] data = ((DataBufferByte)
                image.getRaster().getDataBuffer()).getData();

        mat.get(0, 0, data);
        return image;
    }
}
