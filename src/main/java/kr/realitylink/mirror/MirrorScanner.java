package kr.realitylink.mirror;

import kr.realitylink.ai.CameraService;
import kr.realitylink.common.ItemType;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;

/* 추론은 MirrorScanner에 요청함 */
public final class MirrorScanner implements AutoCloseable {
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "mirror-inference"));
    private final CameraService camera = new CameraService();
    private final YoloClient yolo = new YoloClient();
    private Future<?> yoloTask;
    private boolean attached;
    private boolean closed;
    // 팀의 MainFrame이 넘기는 기존 생성자 인자는 호환을 위해 받는다.
    public MirrorScanner(Path ignoredLegacyModel) { }

    boolean attach() {
        if (closed || attached) return false;
        attached = true;
        return true;
    }

    void detach() {
        cancelYolo();
        camera.stop();
        attached = false;
    }

    CameraService camera() { return camera; }

    // 기존 추론 작업 스레드에서 Python 실행과 응답 대기를 처리한다.
    void scanYolo(BufferedImage frame, BiConsumer<ItemType, Exception> completed) {
        if (closed) {
            completed.accept(null, new IllegalStateException("거울 스캐너가 종료되었습니다."));
            return;
        }
        yoloTask = worker.submit(() -> {
            ItemType item;
            try {
                item = yolo.detect(frame);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception failure) {
                completed.accept(null, failure);
                return;
            }
            completed.accept(item, null);
        });
    }

    private void cancelYolo() {
        // cancel(true)로 프로세스를 기다리던 작업을 깨워 정리 코드로 보낸다.
        if (yoloTask != null) yoloTask.cancel(true);
        yoloTask = null;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        cancelYolo();
        camera.stop();
        worker.shutdown();
    }
}
