package kr.realitylink.common;

@FunctionalInterface
public interface ItemAcquiredListener {
    /** Called on the Swing EDT when the player claims an approved item. */
    void onItemAcquired(ItemType item);
}
