package kr.realitylink.mirror;

import kr.realitylink.ai.Allowlist;
import kr.realitylink.ai.CameraService;
import kr.realitylink.ai.Classifier;
import kr.realitylink.ai.DiagnosticCapture;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One instance per game. Create dialogs and close this service on the Swing EDT. */
public final class MirrorScanner implements AutoCloseable {
    static final int ROI_PERCENT = 70;
    private final Path model;
    private final Path config;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "mirror-inference"));
    private final CameraService camera = new CameraService();
    private boolean attached;
    private boolean closed;
    // Worker-owned. Shutdown queues session disposal after pending inference.
    private Classifier classifier;
    private Allowlist policy;

    public MirrorScanner(Path model) {
        this(model, Path.of(System.getProperty("realitylink.allowlist", "config/allowlist.properties")));
    }

    public MirrorScanner(Path model, Path config) {
        this.model = model;
        this.config = config;
    }

    record Scan(Allowlist policy, Classifier.Classification result) { }

    boolean attach() {
        if (closed || attached) return false;
        attached = true;
        return true;
    }

    void detach() {
        camera.stop();
        attached = false;
    }

    CameraService camera() { return camera; }

    static Rectangle roi(BufferedImage image) {
        return DiagnosticCapture.bounds(image.getWidth(), image.getHeight(), ROI_PERCENT, ROI_PERCENT);
    }

    CompletableFuture<Scan> scan(BufferedImage frame) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("거울 스캐너가 종료되었습니다."));
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (policy == null) policy = Allowlist.load(config);
                if (classifier == null) classifier = new Classifier(model);
                Rectangle area = roi(frame);
                return new Scan(policy, classifier.classify(frame.getSubimage(area.x, area.y, area.width, area.height)));
            } catch (Exception | LinkageError failure) {
                throw new CompletionException(failure);
            }
        }, worker);
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        camera.stop();
        worker.execute(() -> {
            if (classifier != null) {
                try { classifier.close(); }
                catch (Exception failure) { System.err.println("거울 모델 종료 오류 " + failure.getMessage()); }
            }
        });
        worker.shutdown();
    }
}
