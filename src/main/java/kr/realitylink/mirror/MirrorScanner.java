
package kr.realitylink.mirror;

import kr.realitylink.ai.CameraService;
import kr.realitylink.common.ItemType;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

//YOLO 탐지 결과를 MirrorDialog에 전달
interface ScanListener {
    void onResult(ItemType item);
}

public final class MirrorScanner implements AutoCloseable {

    private final CameraService camera = new CameraService();
    private final YoloClient yolo = new YoloClient();

    private ScanThread scanThread;
    private boolean attached; // 스캐너를 사용 중인지
    private boolean closed; // 스캐너 완전 종료 여부

    public MirrorScanner(Path unusedModel) { }

    // 스캐너 등록
    boolean attach() {
        if (closed || attached)
            return false;

        attached = true;
        return true;
    }

    // 스캐너 정리
    void detach() {
        cancelYolo();
        camera.stop();
        attached = false;
    }

    // 카메라 객체 리턴
    CameraService camera() {
        return camera;
    }

    // YOLO 스레드 시작
    void scanYolo(BufferedImage frame, ScanListener listener) {
    	// 스캐너가 종료됐거나 스캔 중이면 실행하지 않음
    	if (closed || (scanThread != null && scanThread.isAlive())) {
    	    listener.onResult(null);
    	    return;
    	}

        scanThread = new ScanThread(frame, listener);
        scanThread.start();
    }

    private void cancelYolo() {
        if (scanThread != null)
            scanThread.interrupt();

        scanThread = null;
    }

    @Override
    public void close() {
        if (closed)
            return;

        closed = true;
        cancelYolo();
        camera.stop();
    }

    // YOLO 탐지용 스레드
    private class ScanThread extends Thread {

        private final BufferedImage frame;
        private final ScanListener listener;

        ScanThread(BufferedImage frame, ScanListener listener) {
            this.frame = frame;
            this.listener = listener;
        }

        @Override
        public void run() {
            try {
                ItemType item = yolo.detect(frame); // YOLO 실행

                if (!isInterrupted()) // 중단 요청이 없으면 결과 전달
                    listener.onResult(item); // 탐지 결과 전달

            } catch (InterruptedException e) {
                return;

            } catch (Exception e) {
                if (!isInterrupted())
                    listener.onResult(null);  // 오류 또는 미탐지
            }
        }
    }
}
