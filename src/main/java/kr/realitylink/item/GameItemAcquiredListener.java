package kr.realitylink.item;

@FunctionalInterface
public interface GameItemAcquiredListener {
    void onGameItemAcquired(GameItem item);
}