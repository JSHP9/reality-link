package kr.realitylink.item;

import javax.swing.ImageIcon;

public enum GameItem {
	 CUP("컵", "/assets/items/cup.png");

	 private final String displayName;
	 private final String imageFile;
	 private final ImageIcon image;

	 GameItem(String displayName, String imageFile) {
	     this.displayName = displayName;
	     this.imageFile = imageFile;
	     this.image = new ImageIcon(GameItem.class.getResource(imageFile));
	 }

	 public String displayName() { return displayName; }
	 public String imageFile() { return imageFile; }
	 public ImageIcon image() { return image; }
}
