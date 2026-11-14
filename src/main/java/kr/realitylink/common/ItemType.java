package kr.realitylink.common;

import javax.swing.ImageIcon;

public enum ItemType {
    BOOK("책", "/assets/items/book.png", true),
    LADDER("사다리", "/assets/items/ladder.png", false),
    SCISSORS("가위", "/assets/items/scizzors.png", false),
    PHONE("휴대전화", "/assets/items/phone.png", false),
    CUP("컵", "/assets/items/cup.png", false),
    WATER_CUP("물이 담긴 컵", "/assets/items/water_cup.png", false),
    PLANT("식물", "/assets/items/plant.png", false),
    SWITCH_HANDLE("스위치 손잡이","/assets/items/switch_handle.png", false),
    KEY("열쇠", "/assets/items/key.png", true);

    private final String displayName;
    private final String imageFile;
    private final ImageIcon image;
    private final boolean hasPopup;

    ItemType(String displayName, String imageFile, boolean hasPopup) {
        this.displayName = displayName;
        this.imageFile = imageFile;
        this.image = new ImageIcon(ItemType.class.getResource(imageFile));
        this.hasPopup = hasPopup;
    }

    public String displayName() { return displayName; }
    public String imageFile() { return imageFile; }
    public ImageIcon image() { return image; }
    public boolean hasPopup() { return hasPopup; }
}