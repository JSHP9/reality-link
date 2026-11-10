
package kr.realitylink.mirror;

import kr.realitylink.common.ItemType;
import kr.realitylink.common.ItemAcquiredListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// 인식된 아이템을 임시 저장하고, 가져오기 버튼을 누르면 게임에 전달하는 클래스
final class MirrorAcquisition {

    // 아직 인벤토리로 가져오지 않은 아이템 목록
    private List<ItemType> pending = new ArrayList<>();

    // 현재 대기 중인 아이템 목록 반환
    List<ItemType> pending() {
        return new ArrayList<>(pending); // 리스트의 복사본을 반환하므로 내부 pending 안바뀜.
    }

    // YOLO가 인식한 아이템을 대기 목록에 저장
    void setDetectedItems(Set<ItemType> items) {
        pending = new ArrayList<>(items);
    }

    // 가져오기 버튼을 누르면 아이템을 게임으로 전달
    void claim(ItemAcquiredListener listener) {

        for (ItemType item : pending) {
            listener.onItemAcquired(item);
        }

        // 전달이 끝나면 대기 목록 비우기
        pending.clear();
    }
}
