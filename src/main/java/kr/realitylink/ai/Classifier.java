package kr.realitylink.ai;

import ai.onnxruntime.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.stream.IntStream;

/** A single-thread-owned CPU inference session; no OpenCV dependency in this path. */
public final class Classifier implements AutoCloseable {
    public static final String MODEL_SHA256 = "c0c3f76d93fa3fd6580652a45618618a220fced18babf65774ed169de0432ad5";
    private static final String LABEL_SHA256 = "acf75ef0abe89694b19056e0796401068b459c457baa30335f240c7692857355";
    private final OrtEnvironment environment = OrtEnvironment.getEnvironment();
    private final OrtSession session;
    private final List<String> labels;
    private final String inputName;
    private final String metadata;

    public record Prediction(int index, String synset, String label, float logit, double probability) { }
    public record Classification(List<Prediction> top5, double milliseconds) {
        public String format() {
            StringBuilder out = new StringBuilder(String.format(Locale.ROOT,
                    "실제 모델 추론 / 전처리 포함 %.1f ms%n점수 = 1000 클래스 softmax (정확도 보장값 아님)%n", milliseconds));
            for (int i = 0; i < top5.size(); i++) {
                Prediction p = top5.get(i);
                out.append(String.format(Locale.ROOT, "%d. [%d] %s %s | %.4f%% | logit=%.6f%n",
                        i + 1, p.index(), p.synset(), p.label(), p.probability() * 100, p.logit()));
            }
            return out.toString();
        }
    }

    public Classifier(Path model) throws Exception {
        if (!Files.isRegularFile(model)) throw new IOException("모델 파일 없음: " + model.toAbsolutePath());
        verify(Files.readAllBytes(model), MODEL_SHA256, "모델");
        try (InputStream stream = Classifier.class.getResourceAsStream("/synset.txt")) {
            if (stream == null) throw new IOException("synset.txt 리소스 없음");
            byte[] bytes = stream.readAllBytes();
            verify(bytes, LABEL_SHA256, "클래스명");
            labels = new String(bytes, StandardCharsets.UTF_8).lines().toList();
        }
        if (labels.size() != 1000) throw new IOException("ImageNet 클래스명은 정확히 1000개여야 합니다.");
        OrtSession created;
        try (var options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors())));
            created = environment.createSession(model.toAbsolutePath().toString(), options);
        }
        session = created;
        try {
            if (session.getInputInfo().size() != 1 || session.getOutputInfo().size() != 1)
                throw new IllegalArgumentException("예상과 다른 입출력 개수");
            inputName = session.getInputNames().iterator().next();
            requireTensor(session.getInputInfo().get(inputName), new long[]{1, 3, 224, 224});
            requireTensor(session.getOutputInfo().values().iterator().next(), new long[]{1, 1000});
            metadata = "ONNX Runtime " + environment.getVersion() + " / CPU\n모델 SHA-256 " + MODEL_SHA256
                    + "\n입력 " + session.getInputInfo() + "\n출력 " + session.getOutputInfo()
                    + "\n클래스 1000개 / 0 기반 인덱스 / logits → softmax\n";
        } catch (Exception e) { session.close(); throw e; }
    }

    private static void verify(byte[] bytes, String expected, String name) throws NoSuchAlgorithmException {
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        if (!actual.equals(expected)) throw new IllegalArgumentException(name + " SHA-256 불일치: " + actual);
    }

    private static void requireTensor(NodeInfo node, long[] expected) {
        if (!(node.getInfo() instanceof TensorInfo t) || t.type != OnnxJavaType.FLOAT)
            throw new IllegalArgumentException("예상 규격 " + Arrays.toString(expected) + " FLOAT, 실제 " + node);
        long[] shape = t.getShape();
        if (shape.length != expected.length) throw new IllegalArgumentException("예상과 다른 텐서 차원: " + node);
        for (int i = 0; i < shape.length; i++) {
            if (shape[i] != expected[i] && !(i == 0 && shape[i] == -1))
                throw new IllegalArgumentException("예상과 다른 텐서 크기: " + node);
        }
    }

    public String metadata() { return metadata; }

    public Classification classify(BufferedImage image) throws OrtException {
        return classifyDetailed(image).classification();
    }

    public record Detailed(BufferedImage inputRgb, Classification classification) { }

    /** No second resize: the displayed/saved RGB pixels build the very tensor used for this run. */
    public Detailed classifyDetailed(BufferedImage image) throws OrtException {
        long start = System.nanoTime();
        Preprocessor.Prepared prepared = Preprocessor.prepare(image);
        float[] data = prepared.tensor();
        try (OnnxTensor tensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(data), new long[]{1, 3, 224, 224});
             OrtSession.Result result = session.run(Map.of(inputName, tensor))) {
            Object value = result.get(0).getValue();
            if (!(value instanceof float[][] batch) || batch.length != 1 || batch[0].length != 1000)
                throw new IllegalStateException("실제 출력이 float[1][1000]이 아닙니다.");
            float[] logits = batch[0];
            double[] probabilities = softmax(logits);
            List<Prediction> predictions = IntStream.range(0, logits.length).boxed()
                    .sorted(Comparator.<Integer>comparingDouble(i -> probabilities[i]).reversed().thenComparingInt(i -> i))
                    .limit(5).map(i -> {
                        String[] label = labels.get(i).split(" ", 2);
                        return new Prediction(i, label[0], label[1], logits[i], probabilities[i]);
                    }).toList();
            return new Detailed(prepared.rgb(), new Classification(predictions, (System.nanoTime() - start) / 1_000_000.0));
        }
    }

    static double[] softmax(float[] logits) {
        if (logits.length == 0) throw new IllegalArgumentException("빈 출력");
        double max = Double.NEGATIVE_INFINITY;
        for (float value : logits) {
            if (!Float.isFinite(value)) throw new IllegalArgumentException("유한하지 않은 모델 출력");
            max = Math.max(max, value);
        }
        double[] p = new double[logits.length];
        double sum = 0;
        for (int i = 0; i < logits.length; i++) { p[i] = Math.exp(logits[i] - max); sum += p[i]; }
        for (int i = 0; i < p.length; i++) p[i] /= sum;
        return p;
    }

    @Override public void close() throws OrtException { session.close(); }
}
