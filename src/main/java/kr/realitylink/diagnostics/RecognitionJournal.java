package kr.realitylink.diagnostics;

import kr.realitylink.ai.Allowlist;
import kr.realitylink.ai.Classifier;
import kr.realitylink.ai.VerificationSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Local TSV evidence; no photo, server or DB. Ground truth is provided by the user. */
public final class RecognitionJournal {
    public static final String UNLABELLED = "PLAY";
    private final Path path;
    public RecognitionJournal(Path path) { this.path = path; }
    public Path path() { return path; }
    public static String assessment(String actual, Set<String> predicted) {
        if (actual.equals(UNLABELLED)) return "UNLABELLED";
        Set<String> expected = actual.startsWith("NONE:") ? Set.of() : Set.of(actual);
        boolean extra = predicted.stream().anyMatch(item -> !expected.contains(item));
        if (extra) return "FALSE_POSITIVE";
        if (!predicted.containsAll(expected)) return "MISS";
        return "MATCH";
    }
    public void append(String actual, String source, double threshold, Classifier.Classification result,
                       VerificationSession.Decision decision) throws IOException {
        String top5 = java.util.stream.IntStream.range(0, result.top5().size()).mapToObj(i -> {
            var p = result.top5().get(i);
            return String.format(Locale.ROOT, "%d:%d:%s:%s:%.8f:%.8f", i+1, p.index(), p.synset(), p.label(), p.probability(), p.logit());
        }).collect(Collectors.joining(" | "));
        String candidates = decision.candidates().stream().map(c -> c.item() + "@" + c.originalRank() + ":"
                + c.prediction().synset() + ":" + c.prediction().probability()).collect(Collectors.joining(" | "));
        Set<String> candidateItems = decision.candidates().stream().map(Allowlist.Candidate::item).collect(Collectors.toSet());
        write(List.of(LocalDateTime.now().toString(), decision.sessionId(), Integer.toString(decision.shot()),
                actual, source, Double.toString(threshold), top5, candidates, decision.votes().toString(),
                decision.complete() ? "COMPLETE" : "PENDING", decision.approved().toString(),
                assessment(actual, candidateItems), decision.complete() ? assessment(actual, decision.approved()) : "PENDING"));
    }
    public void event(String sessionId, String actual, String message) throws IOException {
        write(List.of(LocalDateTime.now().toString(), sessionId, "", actual, message, "", "", "", "", "EVENT", "", "", ""));
    }
    private void write(List<String> columns) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        if (!Files.exists(path)) Files.writeString(path,
                "time\tsession\tshot\tactual\tsource\tthreshold\toriginal_top5\tcandidates\tvotes\tstate\tapproved\tcandidate_assessment\tfinal_assessment\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        String line = columns.stream().map(s -> s.replace('\t',' ').replace('\r',' ').replace('\n',' ')).collect(Collectors.joining("\t"));
        Files.writeString(path, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }
}
