package kr.realitylink.diagnostics;

import kr.realitylink.ai.Allowlist;
import kr.realitylink.ai.Classifier;
import kr.realitylink.ai.DiagnosticCapture;
import kr.realitylink.ai.VerificationSession;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Long-form paired evidence: one FULL row and one ROI row for each captured frame. */
public final class DiagnosticJournal {
    public static final String HEADER = "time\tcapture_id\tgroup_session\tlane_session\tshot\tactual\tcondition\texperiment\tnotes\tsource_type\tsource\tgame_input\troi_width_percent\troi_height_percent\tframe_width\tframe_height\troi_x\troi_y\troi_width\troi_height\tthreshold\tmapping\tmodel\tlane\toriginal_png\tinput_png\toriginal_top5\tcandidates\tvotes\tstate\tapproved\tcandidate_assessment\tfinal_assessment";
    private final Path directory;
    public DiagnosticJournal(Path directory) { this.directory = directory; }
    public Path directory() { return directory; }
    public record Context(String actual, String condition, String experiment, String notes, String sourceType,
                          String gameInput, int roiWidth, int roiHeight, double threshold) { }

    public void append(DiagnosticCapture.Pair pair, Context context, String source, Allowlist policy,
                       VerificationSession.Decision full, VerificationSession.Decision roi, String groupSession) throws IOException {
        Path path = directory.resolve("pairs.tsv");
        Files.createDirectories(directory);
        if (!Files.exists(path)) Files.writeString(path, HEADER + "\n", StandardOpenOption.CREATE_NEW);
        // Append both lanes in one write, so ordinary write failures cannot leave just one lane in memory buffers.
        String rows = row(pair, context, source, policy, full, groupSession, "FULL", pair.full())
                + row(pair, context, source, policy, roi, groupSession, "ROI", pair.region());
        Files.writeString(path, rows, StandardOpenOption.APPEND);
    }

    private String row(DiagnosticCapture.Pair pair, Context c, String source, Allowlist policy,
                       VerificationSession.Decision d, String group, String lane, Classifier.Detailed inference) {
        String top5 = java.util.stream.IntStream.range(0, inference.classification().top5().size()).mapToObj(i -> {
            var p = inference.classification().top5().get(i);
            return String.format(Locale.ROOT, "%d:%d:%s:%s:%.8f:%.8f", i+1, p.index(), p.synset(), p.label(), p.probability(), p.logit());
        }).collect(Collectors.joining(" | "));
        Set<String> candidates = d.candidates().stream().map(Allowlist.Candidate::item).collect(Collectors.toCollection(TreeSet::new));
        String detailedCandidates = d.candidates().stream().map(Allowlist.Candidate::format).collect(Collectors.joining(" | "));
        List<String> columns = List.of(LocalDateTime.now().toString(), pair.id(), group, d.sessionId(), Integer.toString(d.shot()),
                c.actual(), c.condition(), c.experiment(), c.notes(), c.sourceType(), source, c.gameInput(),
                Integer.toString(c.roiWidth()), Integer.toString(c.roiHeight()), Integer.toString(pair.original().getWidth()),
                Integer.toString(pair.original().getHeight()), Integer.toString(pair.roi().x), Integer.toString(pair.roi().y),
                Integer.toString(pair.roi().width), Integer.toString(pair.roi().height), Double.toString(c.threshold()),
                new TreeMap<>(policy.mapping()).toString(), Classifier.MODEL_SHA256, lane,
                pair.directory().resolve("original.png").toAbsolutePath().toString(),
                pair.directory().resolve(lane.equals("FULL") ? "full-input-224.png" : "roi-input-224.png").toAbsolutePath().toString(),
                top5, detailedCandidates, d.votes().toString(), d.complete() ? "COMPLETE" : "PENDING",
                String.join(";", new TreeSet<>(d.approved())), RecognitionJournal.assessment(c.actual(), candidates),
                d.complete() ? RecognitionJournal.assessment(c.actual(), d.approved()) : "PENDING");
        return columns.stream().map(DiagnosticJournal::clean).collect(Collectors.joining("\t")) + "\n";
    }
    static String clean(String text) { return text.replace('\t', ' ').replace('\r', ' ').replace('\n', ' '); }
}
