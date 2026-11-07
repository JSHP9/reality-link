package kr.realitylink.ai;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Game policy only: never changes model scores or the original global ranking. */
public final class Allowlist {
    public static final int TOP_K = 3;
    private final Map<String, String> itemBySynset;
    private final Set<String> items;
    private final double minimumScore;
    public record Candidate(String item, int originalRank, Classifier.Prediction prediction) {
        public String format() {
            return String.format(Locale.ROOT, "%s ← 원본 %d위 %s (%s), %.4f%%",
                    item, originalRank, prediction.synset(), prediction.label(), prediction.probability() * 100);
        }
    }

    private Allowlist(Map<String, String> mapping, double minimumScore) {
        this.itemBySynset = Map.copyOf(mapping);
        this.items = Collections.unmodifiableSet(new TreeSet<>(mapping.values()));
        this.minimumScore = minimumScore;
    }

    public static Allowlist load(Path path) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { properties.load(reader); }
        Set<String> validSynsets = new HashSet<>();
        try (InputStream stream = Allowlist.class.getResourceAsStream("/synset.txt")) {
            if (stream == null) throw new IOException("synset.txt 없음");
            new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .forEach(line -> validSynsets.add(line.split(" ", 2)[0]));
        }
        Map<String, String> mapping = new LinkedHashMap<>();
        double minimum;
        try { minimum = Double.parseDouble(properties.getProperty("minimumScore", "0.15")); }
        catch (NumberFormatException e) { throw new IOException("minimumScore는 0~1 숫자여야 합니다.", e); }
        requireScore(minimum);
        for (String key : properties.stringPropertyNames()) {
            if (key.equals("minimumScore")) continue;
            if (!key.matches("item\\.[A-Z][A-Z0-9_]*")) throw new IOException("알 수 없는 설정 키: " + key);
            String item = key.substring(5);
            if (item.equals("PLAY")) throw new IOException("PLAY는 테스트 모드 예약어입니다.");
            String[] synsets = properties.getProperty(key).split(",", -1);
            for (String value : synsets) {
                String synset = value.trim();
                if (!validSynsets.contains(synset)) throw new IOException("ImageNet에 없는 synset: " + synset);
                if (mapping.putIfAbsent(synset, item) != null) throw new IOException("중복 synset 매핑: " + synset);
            }
        }
        if (mapping.isEmpty()) throw new IOException("허용 목록이 비어 있습니다.");
        return new Allowlist(mapping, minimum);
    }

    static void requireScore(double score) {
        if (!Double.isFinite(score) || score < 0 || score > 1)
            throw new IllegalArgumentException("최소 점수는 0~1이어야 합니다.");
    }
    public Set<String> items() { return items; }
    public double minimumScore() { return minimumScore; }
    public Map<String, String> mapping() { return itemBySynset; }

    /** Input is the unmodified Top-5 from Classifier, already ranked over all 1000 classes. */
    public List<Candidate> candidates(Classifier.Classification result, double threshold) {
        requireScore(threshold);
        Map<String, Candidate> best = new TreeMap<>();
        List<Classifier.Prediction> original = result.top5();
        for (int i = 0; i < Math.min(TOP_K, original.size()); i++) {
            Classifier.Prediction p = original.get(i);
            String item = itemBySynset.get(p.synset());
            if (item == null || !Double.isFinite(p.probability()) || p.probability() < threshold) continue;
            Candidate candidate = new Candidate(item, i + 1, p);
            best.merge(item, candidate, (a, b) -> a.prediction().probability() >= b.prediction().probability() ? a : b);
        }
        return List.copyOf(best.values());
    }
}
