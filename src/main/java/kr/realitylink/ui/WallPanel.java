package kr.realitylink.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import kr.realitylink.common.ItemAcquiredListener;
import kr.realitylink.common.ItemType;

public class WallPanel extends JPanel {
	private CardLayout cardLayout;
	private JPanel[] walls;
	private int currentwall = 0;
	private ArrayList<ItemType> collectedItems = new ArrayList<>();
	private final Runnable onMirrorClick;
	private final ItemAcquiredListener onItemAcquired;
	private InventoryPanel inventoryPanel;
	private boolean ladderPlaced = false;
	private boolean tieCut = false;
	private boolean ventOpened = false;
	private boolean ventLightened = false;
	private boolean plantPlanted = false;
	private boolean plantWatered = false;
	private boolean leverPlaced = false;

	private JPanel createWall(int index) {
		final int wallIndex = index;
		JPanel wall = new JPanel(new BorderLayout());
		String path = "/assets/room/wall" + (index + 1) + ".png";
		ImageIcon image = new ImageIcon(getClass().getResource(path));
		ImageIcon ladder = new ImageIcon(getClass().getResource("/assets/objects/ladder.png"));
		ImageIcon tie = new ImageIcon(getClass().getResource("/assets/objects/cable_tie.png"));
		ImageIcon vent = new ImageIcon(getClass().getResource("/assets/objects/vent.png"));
		ImageIcon plant1 = new ImageIcon(getClass().getResource("/assets/objects/planted_plant.png"));
		ImageIcon plant2 = new ImageIcon(getClass().getResource("/assets/objects/planted_plant2.png"));
		ImageIcon switchHandle = new ImageIcon(getClass().getResource("/assets/objects/switch_handle.png"));

		// 벽 이미지 그리기 & 아이템 그리기
		JLabel label = new JLabel() {
			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);

				g.drawImage(image.getImage(), 0, 0, getWidth(), getHeight(), this);

				// 사다리(획득 전)
				if (wallIndex == 0 && !collectedItems.contains(ItemType.LADDER) && !ladderPlaced) {
					g.drawImage(ladder.getImage(), (int) (getWidth() * InteractionConfig.LADDER_PICKUP_X_MIN),
							(int) (getHeight() * InteractionConfig.LADDER_PICKUP_Y_MIN),
							(int) (getWidth()
									* (InteractionConfig.LADDER_PICKUP_X_MAX - InteractionConfig.LADDER_PICKUP_X_MIN)),
							(int) (getHeight()
									* (InteractionConfig.LADDER_PICKUP_Y_MAX - InteractionConfig.LADDER_PICKUP_Y_MIN)),
							this);
				}

				// 사다리(설치됨)
				if (wallIndex == 3 && ladderPlaced) {
					g.drawImage(ladder.getImage(), (int) (getWidth() * InteractionConfig.LADDER_PLACED_X_MIN),
							(int) (getHeight() * InteractionConfig.LADDER_PLACED_Y_MIN),
							(int) (getWidth()
									* (InteractionConfig.LADDER_PLACED_X_MAX - InteractionConfig.LADDER_PLACED_X_MIN)),
							(int) (getHeight()
									* (InteractionConfig.LADDER_PLACED_Y_MAX - InteractionConfig.LADDER_PLACED_Y_MIN)),
							this);
				}

				// 환풍구 덮개
				if (wallIndex == 3 && !ventOpened) {
					g.drawImage(vent.getImage(), (int) (getWidth() * InteractionConfig.VENT_X_MIN),
							(int) (getHeight() * InteractionConfig.VENT_Y_MIN),
							(int) (getWidth() * (InteractionConfig.VENT_X_MAX - InteractionConfig.VENT_X_MIN)),
							(int) (getHeight() * (InteractionConfig.VENT_Y_MAX - InteractionConfig.VENT_Y_MIN)), this);
				}

				// 케이블타이
				if (wallIndex == 3 && !tieCut) {
					// 위쪽 케이블 타이
					g.drawImage(tie.getImage(), (int) (getWidth() * InteractionConfig.TIE_UP_X_MIN),
							(int) (getHeight() * InteractionConfig.TIE_UP_Y_MIN),
							(int) (getWidth() * (InteractionConfig.TIE_UP_X_MAX - InteractionConfig.TIE_UP_X_MIN)),
							(int) (getHeight() * (InteractionConfig.TIE_UP_Y_MAX - InteractionConfig.TIE_UP_Y_MIN)),
							this);

					// 아래쪽 케이블 타이
					g.drawImage(tie.getImage(), (int) (getWidth() * InteractionConfig.TIE_LOW_X_MIN),
							(int) (getHeight() * InteractionConfig.TIE_LOW_Y_MIN),
							(int) (getWidth() * (InteractionConfig.TIE_LOW_X_MAX - InteractionConfig.TIE_LOW_X_MIN)),
							(int) (getHeight() * (InteractionConfig.TIE_LOW_Y_MAX - InteractionConfig.TIE_LOW_Y_MIN)),
							this);
				}

				// 화분 - 식물
				if (wallIndex == 0 && plantPlanted) {
					if (plantWatered) {
						g.drawImage(plant2.getImage(), (int) (getWidth() * InteractionConfig.PLANTED_X_MIN),
								(int) (getHeight() * InteractionConfig.PLANTED_Y_MIN),
								(int) (getWidth()
										* (InteractionConfig.PLANTED_X_MAX - InteractionConfig.PLANTED_X_MIN)),
								(int) (getHeight()
										* (InteractionConfig.PLANTED_Y_MAX - InteractionConfig.PLANTED_Y_MIN)),
								this);
					} else {
						g.drawImage(plant1.getImage(), (int) (getWidth() * InteractionConfig.PLANTED_X_MIN),
								(int) (getHeight() * InteractionConfig.PLANTED_Y_MIN),
								(int) (getWidth()
										* (InteractionConfig.PLANTED_X_MAX - InteractionConfig.PLANTED_X_MIN)),
								(int) (getHeight()
										* (InteractionConfig.PLANTED_Y_MAX - InteractionConfig.PLANTED_Y_MIN)),
								this);
					}
				}

				// 식물이 뱉은 스위치 손잡이
				if (wallIndex == 0 && plantWatered && !collectedItems.contains(ItemType.SWITCH_HANDLE)) {
					g.drawImage(switchHandle.getImage(), (int) (getWidth() * InteractionConfig.SWITCH_X_MIN),
							(int) (getHeight() * InteractionConfig.SWITCH_Y_MIN),
							(int) (getWidth() * (InteractionConfig.SWITCH_X_MAX - InteractionConfig.SWITCH_X_MIN)),
							(int) (getHeight() * (InteractionConfig.SWITCH_Y_MAX - InteractionConfig.SWITCH_Y_MIN)),
							this);
				}
			}
		};

		// 아이템 & 맵 상호작용 배치
		label.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				int x = e.getX();
				int y = e.getY();
				int width = label.getWidth();
				int height = label.getHeight();

				// 선택한 아이템 취소
				if (SwingUtilities.isRightMouseButton(e)) {
					inventoryPanel.clearSelection();
				}

				if (SwingUtilities.isLeftMouseButton(e)) {
					// 벽 전환 상호작용 배치
					if (x < width * InteractionConfig.PRVIOUS_WALL_X_MAX) {
						previousWall();
						return;
					} else if (x > width * InteractionConfig.NEXT_WALL_X_MAX) {
						nextWall();
						return;
					}

					// 거울 상호작용 배치
					boolean mirrorClicked = x >= width * InteractionConfig.MIRROR_X_MIN
							&& x <= width * InteractionConfig.MIRROR_X_MAX
							&& y >= height * InteractionConfig.MIRROR_Y_MIN
							&& y <= height * InteractionConfig.MIRROR_Y_MAX;

					if (wallIndex == 0 && mirrorClicked) {
						onMirrorClick.run();
						return;
					}

					// 사다리 획득 상호작용 배치
					boolean ladderClicked = x >= width * InteractionConfig.LADDER_PICKUP_X_MIN
							&& x <= width * InteractionConfig.LADDER_PICKUP_X_MAX
							&& y >= height * InteractionConfig.LADDER_PICKUP_Y_MIN
							&& y <= height * InteractionConfig.LADDER_PICKUP_Y_MAX;

					if (wallIndex == 0 && ladderClicked && !collectedItems.contains(ItemType.LADDER) && !ladderPlaced
							&& inventoryPanel.hasSpace()) {
						onItemAcquired.onItemAcquired(ItemType.LADDER);
						collectedItems.add(ItemType.LADDER);
						label.repaint();
						return;
					}

					// 사다리 사용 상호작용 배치
					boolean ladderPlace = x >= width * InteractionConfig.LADDER_PLACED_X_MIN
							&& x <= width * InteractionConfig.LADDER_PLACED_X_MAX
							&& y >= height * InteractionConfig.LADDER_PLACED_Y_MIN
							&& y <= height * InteractionConfig.LADDER_PLACED_Y_MAX;

					if (wallIndex == 3 && ladderPlace && collectedItems.contains(ItemType.LADDER) && !ladderPlaced
							&& inventoryPanel.getSelectedItem() == ItemType.LADDER && inventoryPanel.hasSpace()) {
						ladderPlaced = true;
						inventoryPanel.removeSelectedItem();
						System.out.println("사다리를 설치했다. 이젠 환풍구에도 손이 닿겠지.");
						label.repaint();
						return;
					}

					// 케이블타이 상호작용 배치
					boolean upperTieClicked = x >= width * InteractionConfig.TIE_UP_X_MIN
							&& x <= width * InteractionConfig.TIE_UP_X_MAX
							&& y >= height * InteractionConfig.TIE_UP_Y_MIN
							&& y <= height * InteractionConfig.TIE_UP_Y_MAX;

					boolean lowerTieClicked = x >= width * InteractionConfig.TIE_LOW_X_MIN
							&& x <= width * InteractionConfig.TIE_LOW_X_MAX
							&& y >= height * InteractionConfig.TIE_LOW_Y_MIN
							&& y <= height * InteractionConfig.TIE_LOW_Y_MAX;

					if (wallIndex == 3 && (upperTieClicked || lowerTieClicked) && ladderPlaced && !tieCut
							&& inventoryPanel.getSelectedItem() == ItemType.SCISSORS && inventoryPanel.hasSpace()) {
						tieCut = true;
						inventoryPanel.removeSelectedItem();
						System.out.println("가위로 환풍구를 막고있던 케이블타이를 제거했다.");
						label.repaint();
						return;
					}

					// 환풍구 덮개 상호작용 배치
					boolean ventInteraction = x >= width * InteractionConfig.VENT_X_MIN
							&& x <= width * InteractionConfig.VENT_X_MAX && y >= height * InteractionConfig.VENT_Y_MIN
							&& y <= height * InteractionConfig.VENT_Y_MAX;

					// 사다리 설치 전
					if (wallIndex == 3 && ventInteraction && !ladderPlaced && !tieCut && !ventOpened
							&& inventoryPanel.hasSpace()) {
						System.out.println("너무 높아서 손이 안닿는다. 어릴때 우유 좀 많이 먹을껄.");
						label.repaint();
						return;
					}

					// 사다리 설치 후(케이블타이 있음)
					if (wallIndex == 3 && ventInteraction && ladderPlaced && !tieCut && !ventOpened
							&& inventoryPanel.hasSpace()) {
						System.out.println("케이블타이가 방해된다. 가위가 있었다면...");
						label.repaint();
						return;
					}

					// 사다리 설치 후(케이블타이 없음)
					if (wallIndex == 3 && ventInteraction && ladderPlaced && tieCut && !ventOpened
							&& inventoryPanel.hasSpace()) {
						ventOpened = true;
						System.out.println("열었다!");
						label.repaint();
						return;
					}

					// 환풍구 입장(휴대폰 사용 전) 상호작용 배치
					if (wallIndex == 3 && ventInteraction && ladderPlaced && tieCut && ventOpened && !ventLightened
							&& inventoryPanel.hasSpace()) {
						if (inventoryPanel.getSelectedItem() == ItemType.PHONE) {
							ventLightened = true;
							inventoryPanel.removeSelectedItem();
							System.out.println("휴대폰으로 안쪽을 비추었다.\n뭔가 달아나는 소리가 들리는 듯 하다.\n날 물던 그 녀석들인가?");
						} else {
							System.out.println(
									"안쪽은 너무 어두워 보지는 못했지만 무엇인가에 공격당해 황급히 물러났다.\n휴대폰이라도 있으면 안쪽을 비출 수 있을 것 같은데...");
						}
						label.repaint();
						return;
					}

					// 환풍구 입장(휴대폰 사용 후) 상호작용 배치
					if (wallIndex == 3 && ventInteraction && ladderPlaced && tieCut && ventOpened && ventLightened
							&& inventoryPanel.hasSpace()) {
						showWall(4);
						label.repaint();
						return;
					}

					// 화분 상호작용 배치
					boolean potClicked = x >= width * InteractionConfig.POT_X_MIN
							&& x <= width * InteractionConfig.POT_X_MAX && y >= height * InteractionConfig.POT_Y_MIN
							&& y <= height * InteractionConfig.POT_Y_MAX;

					if (wallIndex == 0 && !plantPlanted && !plantWatered && inventoryPanel.hasSpace()) {
						if (inventoryPanel.getSelectedItem() == ItemType.PLANT) {
							plantPlanted = true;
							inventoryPanel.removeSelectedItem();
							System.out.println("화분에 식물을 심었다.");
							label.repaint();
							return;
						}
						System.out.println("뭔가 심을 수 있을 것 같다.");
						return;
					}

					// 화분 - 식물 상호작용 배치
					boolean plantedClicked = x >= width * InteractionConfig.PLANTED_X_MIN
							&& x <= width * InteractionConfig.PLANTED_X_MAX
							&& y >= height * InteractionConfig.PLANTED_Y_MIN
							&& y <= height * InteractionConfig.PLANTED_Y_MAX;

					if (wallIndex == 0 && plantPlanted && !plantWatered && inventoryPanel.hasSpace()) {
						if (inventoryPanel.getSelectedItem() == ItemType.WATER_CUP) {
							plantWatered = true;
							inventoryPanel.removeSelectedItem();
							System.out.println("식물에 물을 주었다.\n반짝이던 무언가를 뱉어냈다.");
							label.repaint();
							return;
						}
						System.out.println("입에 뭔가 있는거 같은데, 컵으로 물이라도 줘볼까?");
						return;
					}

					// 세면대에서 물 받기
					boolean sinkClicked = x >= width * InteractionConfig.SINK_X_MIN
							&& x <= width * InteractionConfig.SINK_X_MAX && y >= height * InteractionConfig.SINK_Y_MIN
							&& y <= height * InteractionConfig.SINK_Y_MAX;

					if (wallIndex == 1 && sinkClicked) {
						if (inventoryPanel.getSelectedItem() == ItemType.CUP) {
							inventoryPanel.removeSelectedItem();
							onItemAcquired.onItemAcquired(ItemType.WATER_CUP);
							collectedItems.add(ItemType.WATER_CUP);
							System.out.println("컵에 물을 받았다.");
							return;
						}
						System.out.println("세수라도 좀 할까...");
						return;
					}

					// 스위치 손잡이 상호작용 배치
					boolean switchClicked = x >= width * InteractionConfig.SWITCH_X_MIN
							&& x <= width * InteractionConfig.SWITCH_X_MAX
							&& y >= height * InteractionConfig.SWITCH_Y_MIN
							&& y <= height * InteractionConfig.SWITCH_Y_MAX;

					if (wallIndex == 0 && switchClicked && plantWatered
							&& !collectedItems.contains(ItemType.SWITCH_HANDLE) && inventoryPanel.hasSpace()) {
						onItemAcquired.onItemAcquired(ItemType.SWITCH_HANDLE);
					    collectedItems.add(ItemType.SWITCH_HANDLE);
					    System.out.println("스위치 손잡이를 획득했다.");
					    label.repaint();
					    return;
					}
				}
			}
		});
		wall.add(label, BorderLayout.CENTER);
		return wall;
	}

	// 환풍구 내부
	ImageIcon ventPlant = new ImageIcon(getClass().getResource("/assets/room/vent_plant.png"));
	ImageIcon ventNone = new ImageIcon(getClass().getResource("/assets/room/vent.png"));

	JLabel ventInside = new JLabel() {
		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);

			ImageIcon image;

			if (collectedItems.contains(ItemType.PLANT)) {
				image = ventNone;
			} else {
				image = ventPlant;
			}

			g.drawImage(image.getImage(), 0, 0, getWidth(), getHeight(), this);
		}
	};

	public WallPanel(Runnable onMirrorClick, ItemAcquiredListener onItemAcquired, InventoryPanel inventoryPanel) {
		this.onMirrorClick = onMirrorClick;
		this.onItemAcquired = onItemAcquired;
		this.inventoryPanel = inventoryPanel;
		cardLayout = new CardLayout();
		walls = new JPanel[4];

		setLayout(cardLayout);

		// 벽 이미지 cardLayout에 등록
		for (int i = 0; i < walls.length; ++i) {
			walls[i] = createWall(i);
			add(walls[i], "wall" + i);
		}

		ventInside.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				int x = e.getX();
				int y = e.getY();
				int width = ventInside.getWidth();
				int height = ventInside.getHeight();

				// 선택한 아이템 취소
				if (SwingUtilities.isRightMouseButton(e)) {
					inventoryPanel.clearSelection();
					return;
				}

				if (SwingUtilities.isLeftMouseButton(e)) {
					// 환풍구 나가기 상호작용 배치
					if (y >= ventInside.getHeight() * InteractionConfig.VENT_EXIT_Y_MIN) {
						showWall(3);
						return;
					}

					// 식물 제거
					boolean plantTaken = x >= width * InteractionConfig.PLANT_X_MIN
							&& x <= width * InteractionConfig.PLANT_X_MAX && y >= height * InteractionConfig.PLANT_Y_MIN
							&& y <= height * InteractionConfig.PLANT_Y_MAX;
					if (plantTaken && !collectedItems.contains(ItemType.PLANT)) {
						onItemAcquired.onItemAcquired(ItemType.PLANT);
						collectedItems.add(ItemType.PLANT);
						System.out.println("식물을 뜯어냈다.");
						ventInside.repaint();
						return;
					}
				}
			}
		});

		add(ventInside, "wall4");
	}

	// 벽 보이기
	public void showWall(int index) {
		cardLayout.show(this, "wall" + index);
		currentwall = index;
	}

	// 다음 벽으로 넘기기
	public void nextWall() {
		if (currentwall != 4) {
			showWall((currentwall + 1) % walls.length);
		}
	}

	// 이전 벽으로 넘기기
	public void previousWall() {
		if (currentwall != 4) {
			showWall((currentwall - 1 + walls.length) % walls.length);
		}
	}
}
