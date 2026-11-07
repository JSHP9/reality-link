package kr.realitylink.ai;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.UUID;
import javax.imageio.ImageIO;

/** A paired experiment on one immutable captured frame, entirely on the inference worker. */
public final class DiagnosticCapture {
    public record Pair(String id, Path directory, Rectangle roi, BufferedImage original,
                       Classifier.Detailed full, Classifier.Detailed region) { }
    private DiagnosticCapture() { }

    public static Rectangle bounds(int width, int height, int widthPercent, int heightPercent) {
        if (width <= 0 || height <= 0 || widthPercent < 10 || widthPercent > 100 || heightPercent < 10 || heightPercent > 100)
            throw new IllegalArgumentException("ROI 가로·세로는 원본의 10~100%여야 합니다.");
        int w = Math.max(1, (int)Math.round(width * widthPercent / 100.0));
        int h = Math.max(1, (int)Math.round(height * heightPercent / 100.0));
        return new Rectangle((width-w)/2, (height-h)/2, w, h);
    }

    public static Pair capture(Classifier classifier, BufferedImage original, int widthPercent, int heightPercent,
                               Path runDirectory) throws Exception {
        String id = UUID.randomUUID().toString();
        Path directory = runDirectory.resolve(id);
        Files.createDirectories(directory);
        try {
            Rectangle roi = bounds(original.getWidth(), original.getHeight(), widthPercent, heightPercent);
            writePng(original, directory.resolve("original.png"));
            BufferedImage cropped = original.getSubimage(roi.x, roi.y, roi.width, roi.height);
            writePng(cropped, directory.resolve("roi-source.png"));
            Classifier.Detailed full = classifier.classifyDetailed(original);
            writePng(full.inputRgb(), directory.resolve("full-input-224.png"));
            Classifier.Detailed region = classifier.classifyDetailed(cropped);
            writePng(region.inputRgb(), directory.resolve("roi-input-224.png"));
            Files.writeString(directory.resolve("capture.txt"), "time=" + LocalDateTime.now()
                    + "\nmodel=" + Classifier.MODEL_SHA256 + "\nframe=" + original.getWidth() + "x" + original.getHeight()
                    + "\nroi=" + roi + "\nFULL\n" + full.classification().format() + "\nROI\n" + region.classification().format());
            return new Pair(id, directory, roi, original, full, region);
        } catch (Exception | LinkageError e) {
            try { Files.writeString(directory.resolve("INCOMPLETE.txt"), "Capture/inference/save failed: " + e); }
            catch (IOException suppressed) { e.addSuppressed(suppressed); }
            throw e;
        }
    }

    private static void writePng(BufferedImage image, Path path) throws IOException {
        if (!ImageIO.write(image, "png", path.toFile())) throw new IOException("PNG 저장 실패: " + path);
    }
}
