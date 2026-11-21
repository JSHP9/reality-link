package kr.realitylink.game;

import java.util.*;

import kr.realitylink.common.ItemType;

public class GameState {
	
	// 입력 방지 상태
	private boolean inputLocked = false;
	
	// 인트로 출력 상태
	private boolean intro1Shown = false;
	private boolean intro2Shown = false;
	
	//최초 상호작용 상태
	private boolean firstMirror = false;
	private boolean firstDoor = false;

	// 퍼즐 진행 상태
	private boolean ladderPlaced = false;
	private boolean tieCut = false;
	private boolean ventOpened = false;
	private boolean ventLightened = false;
	private boolean plantPlanted = false;
	private boolean plantWatered = false;
	private boolean leverPlaced = false;

	// 조명 상태
	private boolean lightsOff = false;

	// 한 번 획득한 아이템 기록
	private final ArrayList<ItemType> collectedItems = new ArrayList<>();
	
	//최초 상호작용 관련 메서드
	public boolean isFirstMirror() {
		return firstMirror;
	}
	
	public void setFirstMirror(boolean firstMirror) {
		this.firstMirror = firstMirror;
	}
	
	public boolean isFirstDoor() {
		return firstDoor;
	}
	
	public void setFirstDoor(boolean firstDoor) {
		this.firstDoor = firstDoor;
	}
	
	// 조작 잠금 관련 메서드
	public boolean isInputLocked() {
	    return inputLocked;
	}

	public void setInputLocked(boolean inputLocked) {
	    this.inputLocked = inputLocked;
	}
	
	// 인트로 관련 메서드
	public boolean isIntro1Shown() {
	    return intro1Shown;
	}

	public void setIntro1Shown(boolean intro1Shown) {
	    this.intro1Shown = intro1Shown;
	}
	
	public boolean isIntro2Shown() {
	    return intro2Shown;
	}

	public void setIntro2Shown(boolean intro2Shown) {
	    this.intro2Shown = intro2Shown;
	}

	// 사다리 관련 메서드
	public boolean isLadderPlaced() {
		return ladderPlaced;
	}

	public void setLadderPlaced(boolean ladderPlaced) {
		this.ladderPlaced = ladderPlaced;
	}

	// 환풍구 관련 메서드
	public boolean isTieCut() {
		return tieCut;
	}

	public void setTieCut(boolean tieCut) {
		this.tieCut = tieCut;
	}

	public boolean isVentOpened() {
		return ventOpened;
	}

	public void setVentOpened(boolean ventOpened) {
		this.ventOpened = ventOpened;
	}

	public boolean isVentLightened() {
		return ventLightened;
	}

	public void setVentLightened(boolean ventLightened) {
		this.ventLightened = ventLightened;
	}

	// 식물 관련 메서드
	public boolean isPlantPlanted() {
		return plantPlanted;
	}

	public void setPlantPlanted(boolean plantPlanted) {
		this.plantPlanted = plantPlanted;
	}

	public boolean isPlantWatered() {
		return plantWatered;
	}

	public void setPlantWatered(boolean plantWatered) {
		this.plantWatered = plantWatered;
	}

	// 스위치 관련 메서드
	public boolean isLeverPlaced() {
		return leverPlaced;
	}

	public void setLeverPlaced(boolean leverPlaced) {
		this.leverPlaced = leverPlaced;
	}

	public boolean isLightsOff() {
		return lightsOff;
	}

	public void setLightsOff(boolean lightsOff) {
		this.lightsOff = lightsOff;
	}

	// 아이템 획득 이력 관련 메서드
	public boolean hasCollected(ItemType item) {
		return collectedItems.contains(item);
	}

	public void collectItem(ItemType item) {
		if (item != null && !collectedItems.contains(item)) {
			collectedItems.add(item);
		}
	}
}
