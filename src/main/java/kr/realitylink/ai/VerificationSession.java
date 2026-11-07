package kr.realitylink.ai;

import java.util.*;

/** Exactly three successful classifications, each contributing at most one vote per item. */
public final class VerificationSession {
    public static final int SHOTS = 3, REQUIRED_HITS = 2;
    private final Allowlist policy;
    private final double threshold;
    private final String id = UUID.randomUUID().toString();
    private final Map<String, Integer> votes = new TreeMap<>();
    private int count;
    public record Decision(String sessionId, int shot, List<Allowlist.Candidate> candidates,
                           Map<String, Integer> votes, boolean complete, Set<String> approved) {
        public String format() {
            StringBuilder out = new StringBuilder("후보 판정 [세션 " + sessionId + ", " + shot + "/3]\n");
            if (candidates.isEmpty()) out.append("후보 없음: 원본 Top-3 허용 클래스 중 최소 점수를 충족한 항목 없음\n");
            for (var c : candidates) out.append("후보: ").append(c.format()).append('\n');
            out.append("누적 촬영별 투표: ").append(votes).append(" (같은 아이템은 촬영당 최대 1표)\n");
            if (!complete) out.append("최종 획득 판정: 대기 — 3회가 끝나야 승인합니다.\n");
            else out.append("최종 획득 판정: ").append(approved.isEmpty() ? "승인 없음" : "승인 " + approved)
                    .append("\n세션 종료: 촬영 결과 초기화. 다음 촬영은 새 세션 1/3입니다.\n");
            return out.toString();
        }
    }
    public VerificationSession(Allowlist policy, double threshold) {
        Allowlist.requireScore(threshold);
        this.policy = Objects.requireNonNull(policy);
        this.threshold = threshold;
    }
    public Decision accept(Classifier.Classification result) {
        if (count == SHOTS) throw new IllegalStateException("종료된 세션입니다. 새 세션을 만드세요.");
        List<Allowlist.Candidate> candidates = policy.candidates(result, threshold);
        for (var candidate : candidates) votes.merge(candidate.item(), 1, Integer::sum);
        count++;
        Set<String> approved = new TreeSet<>();
        if (count == SHOTS) votes.forEach((item, hits) -> { if (hits >= REQUIRED_HITS) approved.add(item); });
        return new Decision(id, count, candidates, Collections.unmodifiableMap(new TreeMap<>(votes)),
                count == SHOTS, Collections.unmodifiableSet(approved));
    }
}
