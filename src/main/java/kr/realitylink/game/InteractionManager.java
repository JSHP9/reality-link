package kr.realitylink.game;

import kr.realitylink.common.ItemType;
import kr.realitylink.ui.InventoryPanel;

public class InteractionManager {

    private final InventoryPanel inventoryPanel;
    private final GameState gameState;

    public InteractionManager(InventoryPanel inventoryPanel, GameState gameState) {
        this.inventoryPanel = inventoryPanel;
        this.gameState = gameState;
    }

    // 아이템 사용: 기본적으로 소모
    public boolean useItem(boolean condition, ItemType item) {
        return useItem(condition, item, true);
    }

    // 아이템 사용: 소모 여부 선택
    public boolean useItem(boolean condition, ItemType item, boolean consume) {
        if (!condition || inventoryPanel.getSelectedItem() != item) {
            return false;
        }

        if (consume) {
            inventoryPanel.removeSelectedItem();
        }

        return true;
    }

    // 아이템 획득
    public boolean getItem(boolean condition, ItemType item) {
        if (!condition || item == null
                || !inventoryPanel.hasSpace()
                || gameState.hasCollected(item)) {
            return false;
        }

        inventoryPanel.onItemAcquired(item);
        gameState.collectItem(item);
        return true;
    }

    // 사다리 획득
    public boolean getLadder(boolean condition) {
        return getItem(
                condition && !gameState.isLadderPlaced(),
                ItemType.LADDER
        );
    }

    // 사다리 설치
    public boolean placeLadder(boolean condition) {
        if (!useItem(
                condition && !gameState.isLadderPlaced(),
                ItemType.LADDER)) {
            return false;
        }

        gameState.setLadderPlaced(true);
        return true;
    }

    // 케이블타이 제거
    public boolean cutTie(boolean condition) {
        if (!useItem(
                condition
                && gameState.isLadderPlaced()
                && !gameState.isTieCut(),
                ItemType.SCISSORS)) {
            return false;
        }

        gameState.setTieCut(true);
        return true;
    }

    // 환풍구 덮개 개방
    public boolean openVent(boolean condition) {
        if (!condition
                || !gameState.isLadderPlaced()
                || !gameState.isTieCut()
                || gameState.isVentOpened()) {
            return false;
        }

        gameState.setVentOpened(true);
        return true;
    }

    // 스마트폰으로 환풍구 비추기
    public boolean lightVent(boolean condition) {
        if (!useItem(
                condition
                && gameState.isVentOpened()
                && !gameState.isVentLightened(),
                ItemType.PHONE)) {
            return false;
        }

        gameState.setVentLightened(true);
        return true;
    }

    // 환풍구 내부 진입 가능 여부
    public boolean canEnterVent(boolean condition) {
        return condition
                && gameState.isLadderPlaced()
                && gameState.isTieCut()
                && gameState.isVentOpened()
                && gameState.isVentLightened();
    }

    // 환풍구 내부 식물 획득
    public boolean getPlant(boolean condition) {
        return getItem(condition, ItemType.PLANT);
    }

    // 식물 심기
    public boolean plantSeedling(boolean condition) {
        if (!useItem(
                condition && !gameState.isPlantPlanted(),
                ItemType.PLANT)) {
            return false;
        }

        gameState.setPlantPlanted(true);
        return true;
    }

    // 컵에 물 받기
    public boolean fillCup(boolean condition) {
        if (!condition
                || inventoryPanel.getSelectedItem() != ItemType.CUP
                || !inventoryPanel.hasSpace()) {
            return false;
        }

        inventoryPanel.removeSelectedItem();
        inventoryPanel.onItemAcquired(ItemType.WATER_CUP);
        return true;
    }

    // 식물에 물주기
    public boolean waterPlant(boolean condition) {
        if (!useItem(
                condition
                && gameState.isPlantPlanted()
                && !gameState.isPlantWatered(),
                ItemType.WATER_CUP)) {
            return false;
        }

        gameState.setPlantWatered(true);
        return true;
    }

    // 스위치 손잡이 획득
    public boolean getSwitchHandle(boolean condition) {
        return getItem(
                condition && gameState.isPlantWatered(),
                ItemType.SWITCH_HANDLE
        );
    }

    // 스위치 손잡이 설치
    public boolean placeLever(boolean condition) {
        if (!useItem(
                condition && !gameState.isLeverPlaced(),
                ItemType.SWITCH_HANDLE)) {
            return false;
        }

        gameState.setLeverPlaced(true);
        return true;
    }
}