package kr.realitylink.mirror;

import kr.realitylink.ai.VerificationSession;
import kr.realitylink.common.ItemType;
import kr.realitylink.common.ItemAcquiredListener;

import java.util.Arrays;
import java.util.List;

/** A dialog-local verification session. Access from the EDT. */
final class MirrorAcquisition {
    private VerificationSession session;
    private List<ItemType> pending = List.of();
    private int progress;

    VerificationSession.Decision accept(MirrorScanner.Scan scan) {
        if (progress == VerificationSession.SHOTS) throw new IllegalStateException("새 촬영 세션을 시작하세요.");
        if (session == null) session = new VerificationSession(scan.policy(), scan.policy().minimumScore());
        VerificationSession.Decision decision = session.accept(scan.result());
        progress = decision.shot();
        if (decision.complete()) {
            pending = Arrays.stream(ItemType.values())
                    .filter(item -> decision.approved().contains(item.name()))
                    .toList();
        }
        return decision;
    }

    int progress() { return progress; }
    List<ItemType> pending() { return pending; }

    void retry() {
        session = null;
        progress = 0;
        pending = List.of();
    }

    void claim(ItemAcquiredListener listener) {
        List<ItemType> items = pending;
        pending = List.of();
        items.forEach(listener::onItemAcquired);
    }
}
