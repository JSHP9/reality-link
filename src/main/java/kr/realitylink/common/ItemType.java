package kr.realitylink.common;

public enum ItemType {
    PEN("펜", "pen.png"),
    CUP("컵", "cup.png");

    private final String displayName;
    private final String imageFile;

    ItemType(String displayName, String imageFile) {
        this.displayName = displayName;
        this.imageFile = imageFile;
    }

    public String displayName() { return displayName; }
    public String imageFile() { return imageFile; }
}
