package kr.realitylink.ai;

import java.awt.image.BufferedImage;

/** ONNX Model Zoo imagenet_preprocess.py: RGB, short side 256, center 224, NCHW. */
public final class Preprocessor {
    public static final int SIZE = 224;
    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD = {0.229f, 0.224f, 0.225f};
    private Preprocessor() { }

    /** RGB pixels before normalization, paired with the exact tensor built from those pixels. */
    public record Prepared(BufferedImage rgb, float[] tensor) { }

    public static float[] tensor(BufferedImage image) {
        return prepare(image).tensor();
    }

    public static Prepared prepare(BufferedImage image) {
        if (image == null) throw new IllegalArgumentException("이미지를 읽을 수 없습니다.");
        double ratio = 256.0 / Math.min(image.getWidth(), image.getHeight());
        // Python round uses ties-to-even.
        int width = (int) Math.rint(image.getWidth() * ratio);
        int height = (int) Math.rint(image.getHeight() * ratio);
        BufferedImage resized = resize(image, width, height);
        int left = width / 2 - SIZE / 2, top = height / 2 - SIZE / 2;
        BufferedImage input = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        float[] data = new float[3 * SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int rgb = resized.getRGB(left + x, top + y);
                input.setRGB(x, y, rgb);
                for (int c = 0; c < 3; c++) {
                    float pixel = ((rgb >>> (16 - c * 8)) & 255) / 255.0f;
                    data[c * SIZE * SIZE + y * SIZE + x] = (pixel - MEAN[c]) / STD[c];
                }
            }
        }
        return new Prepared(input, data);
    }

    /** Separable bilinear with scale-aware downsampling, matching Pillow's RGB resize semantics.
     * Java2D bilinear downsampling uses a different filter footprint. Use 22-bit coefficients
     * and round after each axis, as in Pillow's 8-bit RGB path; see THIRD_PARTY.md.
     */
    private static BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage horizontal = resampleAxis(source, width, true);
        return resampleAxis(horizontal, height, false);
    }

    private static BufferedImage resampleAxis(BufferedImage source, int length, boolean horizontal) {
        int oldLength = horizontal ? source.getWidth() : source.getHeight();
        if (oldLength == length) return source;
        BufferedImage result = new BufferedImage(horizontal ? length : source.getWidth(),
                horizontal ? source.getHeight() : length, BufferedImage.TYPE_INT_RGB);
        double scale = (double) oldLength / length;
        double filterScale = Math.max(1.0, scale);
        int crossLength = horizontal ? source.getHeight() : source.getWidth();
        for (int dst = 0; dst < length; dst++) {
            double center = (dst + 0.5) * scale;
            int first = Math.max(0, (int)(center - filterScale + 0.5));
            int end = Math.min(oldLength, (int)(center + filterScale + 0.5));
            double[] weights = new double[end - first];
            double sum = 0;
            for (int i = 0; i < weights.length; i++) {
                weights[i] = Math.max(0.0, 1.0 - Math.abs((first + i + 0.5 - center) / filterScale));
                sum += weights[i];
            }
            int[] coefficients = new int[weights.length];
            for (int i = 0; i < weights.length; i++) coefficients[i] = (int)(weights[i] / sum * (1 << 22) + 0.5);
            for (int cross = 0; cross < crossLength; cross++) {
                int red = 1 << 21, green = 1 << 21, blue = 1 << 21;
                for (int i = 0; i < coefficients.length; i++) {
                    int rgb = horizontal ? source.getRGB(first + i, cross) : source.getRGB(cross, first + i);
                    red += ((rgb >>> 16) & 255) * coefficients[i];
                    green += ((rgb >>> 8) & 255) * coefficients[i];
                    blue += (rgb & 255) * coefficients[i];
                }
                int pixel = (clip(red >> 22) << 16) | (clip(green >> 22) << 8) | clip(blue >> 22);
                if (horizontal) result.setRGB(dst, cross, pixel); else result.setRGB(cross, dst, pixel);
            }
        }
        return result;
    }

    private static int clip(int value) { return Math.max(0, Math.min(255, value)); }
}
