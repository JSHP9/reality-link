package kr.realitylink.common;

@FunctionalInterface
public interface ItemAcquiredListener {
    void onItemAcquired(ItemType item);
}
