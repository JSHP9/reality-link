package kr.realitylink.diagnostics;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Reports observed labelled paired data, never generates recognition results or causal claims. */
public final class ExperimentReport {
    private ExperimentReport() { }
    private record Settings(String experiment, String source, String roi, String frame, String threshold, String mapping, String model) { }
    private record Cohort(String actual, String condition, String lane) { }
    private static final class Stats {
        int frames, candidateMatch, candidateFp, complete, match, miss, fp;
        void add(Map<String, String> row) {
            frames++;
            if (row.get("candidate_assessment").equals("MATCH")) candidateMatch++;
            if (row.get("candidate_assessment").equals("FALSE_POSITIVE")) candidateFp++;
            if (!row.get("state").equals("COMPLETE")) return;
            complete++;
            switch (row.get("final_assessment")) {
                case "MATCH" -> match++;
                case "MISS" -> miss++;
                case "FALSE_POSITIVE" -> fp++;
                default -> { }
            }
        }
    }

    public static Path generate(Path root) throws IOException {
        Files.createDirectories(root);
        List<Path> files;
        try (var walk = Files.walk(root)) { files = walk.filter(p -> p.getFileName().toString().equals("pairs.tsv")).sorted().toList(); }
        Map<String, Map<String, Map<String, String>>> pairs = new LinkedHashMap<>();
        int malformed = 0;
        String[] expected = DiagnosticJournal.HEADER.split("\t", -1);
        for (Path file : files) {
            List<String> lines = Files.readAllLines(file);
            if (lines.isEmpty() || !lines.get(0).equals(DiagnosticJournal.HEADER)) { malformed++; continue; }
            for (int i = 1; i < lines.size(); i++) {
                String[] values = lines.get(i).split("\t", -1);
                if (values.length != expected.length) { malformed++; continue; }
                Map<String, String> row = new HashMap<>();
                for (int c = 0; c < expected.length; c++) row.put(expected[c], values[c]);
                if (!Set.of("FULL", "ROI").contains(row.get("lane"))) { malformed++; continue; }
                String key = row.get("capture_id");
                Map<String, Map<String, String>> lanes = pairs.computeIfAbsent(key, k -> new HashMap<>());
                if (lanes.putIfAbsent(row.get("lane"), row) != null) malformed++;
            }
        }
        Map<Settings, Map<Cohort, Stats>> groups = new LinkedHashMap<>();
        int incomplete = 0, unlabelled = 0, pairedFrames = 0;
        for (var pair : pairs.values()) {
            if (pair.size() != 2) { incomplete++; continue; }
            pairedFrames++;
            for (var row : pair.values()) {
                if (row.get("actual").equals("PLAY")) { unlabelled++; continue; }
                Settings settings = new Settings(row.get("experiment"), row.get("source_type"),
                        row.get("roi_width_percent") + "×" + row.get("roi_height_percent") + "%",
                        row.get("frame_width") + "×" + row.get("frame_height"), row.get("threshold"), row.get("mapping"), row.get("model"));
                Cohort cohort = new Cohort(row.get("actual"), row.get("condition"), row.get("lane"));
                groups.computeIfAbsent(settings, k -> new LinkedHashMap<>()).computeIfAbsent(cohort, k -> new Stats()).add(row);
            }
        }
        StringBuilder out = new StringBuilder("# REALITY LINK 촬영 진단 보고서\n\n생성: " + LocalDateTime.now()
                + "\n\n동일 촬영 FULL/ROI 쌍 " + pairedFrames + "개. 정답 미지정 PLAY 행 " + unlabelled
                + "개는 성능 통계에서 제외. 불완전 쌍 " + incomplete + "개, 형식 오류/중복 " + malformed + "개.\n\n"
                + "촬영별 후보와 3회 세션의 최종 획득은 별도 지표입니다. 미완료·취소 세션은 최종 판정 분모에 포함하지 않습니다. "
                + "같은 사진 반복 입력과 연속 프레임은 독립적인 다양한 표본이 아닙니다.\n\n");
        if (groups.isEmpty()) out.append("**판단 보류: 정답을 지정한 실제 촬영 비교 데이터가 없습니다. 실물 검증 미완료입니다.**\n\n");
        int number = 0;
        for (var entry : groups.entrySet()) {
            Settings s = entry.getKey(); Map<Cohort, Stats> stats = entry.getValue();
            out.append("## 설정 묶음 ").append(++number).append("\n\n실험: ").append(s.experiment())
                    .append(" / 입력: ").append(s.source()).append(" / ROI: ").append(s.roi()).append(" / 원본: ")
                    .append(s.frame()).append(" / 최소 점수: ").append(s.threshold()).append("\n\n모델: `")
                    .append(s.model()).append("`\n\n허용 목록: `").append(s.mapping()).append("`\n\n")
                    .append("| 실제 물체 | 조건 | 경로 | 촬영 수 | 후보 정답 일치 | 후보 오인식 | 완료 세션 | 최종 정답 일치 | 미획득 | 오승인 |\n")
                    .append("|---|---|---|---:|---:|---:|---:|---:|---:|---:|\n");
            stats.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().toString())).forEach(e -> {
                Cohort c = e.getKey(); Stats v = e.getValue();
                out.append('|').append(c.actual()).append('|').append(c.condition()).append('|').append(c.lane()).append('|')
                        .append(v.frames).append('|').append(v.candidateMatch).append('|').append(v.candidateFp).append('|')
                        .append(v.complete).append('|').append(v.match).append('|').append(v.miss).append('|').append(v.fp).append("|\n");
            });
            out.append("\n음성 대조군(NONE:*)의 정답 일치는 ‘획득 없음’입니다. PEN/CUP은 해당 아이템만 승인되어야 정답 일치입니다.\n\n");
            for (String lane : List.of("FULL", "ROI")) {
                List<Cohort> required = List.of(new Cohort("PEN", "HANDHELD", lane), new Cohort("PEN", "ON_DESK", lane),
                        new Cohort("CUP", "HANDHELD", lane), new Cohort("CUP", "ON_DESK", lane),
                        new Cohort("NONE:DRIVER", "UNSPECIFIED", lane), new Cohort("NONE:SPEAKER", "UNSPECIFIED", lane),
                        new Cohort("NONE:EMPTY", "EMPTY_BACKGROUND", lane));
                List<String> missing = required.stream().filter(c -> !stats.containsKey(c) || stats.get(c).complete < 3)
                        .map(c -> c.actual() + "/" + c.condition()).toList();
                out.append("- ").append(lane).append(": ");
                if (!missing.isEmpty()) out.append("판단 보류 — 각 조건 완료 세션 3개 미만: ").append(String.join(", ", missing)).append(".\n");
                else {
                    boolean fitsExample = required.stream().allMatch(c -> {
                        Stats v = stats.get(c);
                        return c.actual().startsWith("NONE:") ? v.fp == 0 : (double)v.match / v.complete >= .9;
                    });
                    out.append(fitsExample ? "이 표본에서는 기존 모델을 계속 검토할 수 있습니다." : "예시 운영 기준을 충족하지 못했습니다. 입력 PNG를 점검하고 전이학습을 검토하세요.")
                            .append(" 기준 예시: 양성 조건별 세션 정답 일치 ≥90%, 음성 조건별 관측 오승인 0회. 일반화 성능 보장이나 통계적 인증이 아닙니다.\n");
                }
            }
            out.append('\n');
        }
        out.append("## 해석과 다음 판단\n\n손에 든 펜과 책상 위 펜은 동일 설정 묶음의 HANDHELD/ON_DESK 행에서 비교하세요. "
                + "조명·거리·펜 크기·방향·노출·배경도 같이 달라질 수 있어 차이만으로 손이 원인이라고 단정할 수 없습니다. "
                + "ROI도 배경과 물체의 상대 크기를 함께 바꿉니다. HAND_ONLY 음성 대조군과 저장 PNG를 함께 검토하세요.\n\n"
                + "FULL보다 ROI가 일관되게 좋고 음성 대조군 오승인이 낮다면 기존 모델+고정 ROI 운용을 추가 검증할 수 있습니다. "
                + "잘린 입력·색상·물체 크기 문제를 점검한 뒤에도 손 유무·펜 종류에 따라 실패하거나 음성 대조군이 승인되면 "
                + "실제 환경의 펜·컵·기타(손만/배경/유사 도구 포함) 데이터를 모아 전이학습하고 별도 테스트 세트로 비교하세요. "
                + "학습용과 평가용에는 같은 촬영 세션·연속 프레임을 나누어 섞지 마세요.\n\n"
                + "원시 자료: 각 실행 폴더 pairs.tsv 및 capture_id 폴더의 original.png, roi-source.png, full-input-224.png, roi-input-224.png. "
                + "실제 물체/조건은 사용자가 지정한 값이며 프로그램이 정답을 독립적으로 확인한 것이 아닙니다.\n");
        Path report = root.resolve("report.md");
        Files.writeString(report, out.toString());
        return report;
    }

    public static void main(String[] args) throws IOException {
        if (args.length > 1) throw new IllegalArgumentException("사용법: ExperimentReport [diagnostics 폴더]");
        System.out.println(generate(Path.of(args.length == 0 ? "diagnostics" : args[0])).toAbsolutePath());
    }
}
