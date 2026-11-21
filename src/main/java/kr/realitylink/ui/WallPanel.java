package kr.realitylink.ui;

import java.awt.*;
import java.awt.event.*;

import javax.swing.*;

import kr.realitylink.common.*;
import kr.realitylink.game.*;

public class WallPanel extends JPanel {
	private CardLayout cardLayout;
	private JPanel[] walls, walls_dark;
	private int currentwall = 0;
	private final Runnable onMirrorClick;
	private final ItemAcquiredListener onItemAcquired;
	private final GameState gameState;
	private final InteractionManager interactionManager;
	private InventoryPanel inventoryPanel;
	private JPopupMenu dialoguePopup;

	private JPanel createWall(int index, boolean dark) {
		final int wallIndex = index;
		JPanel wall = new JPanel(new BorderLayout());
		String path;

	    if (dark) {
	        path = "/assets/room/wall" + (index + 1) + "_dark.png";
	    } else {
	        path = "/assets/room/wall" + (index + 1) + ".png";
	    }
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
				if (wallIndex == 0 && !gameState.hasCollected(ItemType.LADDER) && !gameState.isLadderPlaced()) {
					g.drawImage(ladder.getImage(), (int) (getWidth() * InteractionConfig.LADDER_PICKUP_X_MIN),
							(int) (getHeight() * InteractionConfig.LADDER_PICKUP_Y_MIN),
							(int) (getWidth()
									* (InteractionConfig.LADDER_PICKUP_X_MAX - InteractionConfig.LADDER_PICKUP_X_MIN)),
							(int) (getHeight()
									* (InteractionConfig.LADDER_PICKUP_Y_MAX - InteractionConfig.LADDER_PICKUP_Y_MIN)),
							this);
				}

				// 사다리(설치됨)
				if (wallIndex == 3 && gameState.isLadderPlaced()) {
					g.drawImage(ladder.getImage(), (int) (getWidth() * InteractionConfig.LADDER_PLACED_X_MIN),
							(int) (getHeight() * InteractionConfig.LADDER_PLACED_Y_MIN),
							(int) (getWidth()
									* (InteractionConfig.LADDER_PLACED_X_MAX - InteractionConfig.LADDER_PLACED_X_MIN)),
							(int) (getHeight()
									* (InteractionConfig.LADDER_PLACED_Y_MAX - InteractionConfig.LADDER_PLACED_Y_MIN)),
							this);
				}

				// 환풍구 덮개
				if (wallIndex == 3 && !gameState.isVentOpened()) {
					g.drawImage(vent.getImage(), (int) (getWidth() * InteractionConfig.VENT_X_MIN),
							(int) (getHeight() * InteractionConfig.VENT_Y_MIN),
							(int) (getWidth() * (InteractionConfig.VENT_X_MAX - InteractionConfig.VENT_X_MIN)),
							(int) (getHeight() * (InteractionConfig.VENT_Y_MAX - InteractionConfig.VENT_Y_MIN)), this);
				}

				// 케이블타이
				if (wallIndex == 3 && !gameState.isTieCut()) {
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
				if (wallIndex == 0 && gameState.isPlantPlanted()) {
					if (gameState.isPlantWatered()) {
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
				if (wallIndex == 0 && gameState.isPlantWatered() && !gameState.hasCollected(ItemType.SWITCH_HANDLE)) {
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

				if (gameState.isInputLocked()) {
					return;
				}

				// 우클릭: 아이템 선택 취소
				if (SwingUtilities.isRightMouseButton(e)) {
					inventoryPanel.clearSelection();
					return;
				}

				if (!SwingUtilities.isLeftMouseButton(e)) {
					return;
				}

				int x = e.getX();
				int width = label.getWidth();

				// 벽 전환
				if (x < width * InteractionConfig.PRVIOUS_WALL_X_MAX) {
					previousWall();
					return;
				}

				if (x > width * InteractionConfig.NEXT_WALL_X_MAX) {
					nextWall();
					return;
				}

				// 거울
				if (wallIndex == 0 && InteractionConfig.isInside(e, InteractionConfig.getBounds("MIRROR"))) {
					if (gameState.isFirstMirror()) {
						showDialogs(1500, "이건… 거울이 맞나?", "전혀 다른 곳이 비치고 있잖아.");
					}
					onMirrorClick.run();
					return;
				}

				// 사다리 획득
				if (interactionManager.getLadder(wallIndex == 0
						&& InteractionConfig.isInside(e, InteractionConfig.getBounds("LADDER_PICKUP")))) {
					label.repaint();
					return;
				}

				// 사다리 설치
				if (interactionManager.placeLadder(wallIndex == 3
						&& InteractionConfig.isInside(e, InteractionConfig.getBounds("LADDER_PLACED")))) {
					showDialog("사다리를 설치했다. 이젠 환풍구에도 손이 닿겠지.");
					label.repaint();
					return;
				}

				// 케이블타이 제거
				boolean tieClicked = wallIndex == 3
						&& (InteractionConfig.isInside(e, InteractionConfig.getBounds("TIE_UP"))
								|| InteractionConfig.isInside(e, InteractionConfig.getBounds("TIE_LOW")));

				if (interactionManager.cutTie(tieClicked)) {
					showDialog("가위로 환풍구를 막고있던 케이블타이를 제거했다.");
					label.repaint();
					return;
				}

				// 환풍구
				boolean ventClicked = wallIndex == 3
						&& InteractionConfig.isInside(e, InteractionConfig.getBounds("VENT"));

				if (ventClicked) {

					if (!gameState.isLadderPlaced()) {
						showDialog("너무 높아서 손이 안닿는다. 어릴때 우유 좀 많이 먹을껄.");

					} else if (!gameState.isTieCut()) {
						showDialog("케이블타이가 방해된다. 가위가 있었다면...");

					} else if (interactionManager.openVent(true)) {
						showDialog("열었다!");

					} else if (!gameState.isVentLightened()) {

						if (interactionManager.lightVent(true)) {
							showDialogs(1500, "휴대폰으로 안쪽을 비추었다.", "뭔가 달아나는 소리가 들리는 듯 하다.", "날 물던 그 녀석들인가?");
						} else {
							showDialogs(1500,
									"안쪽은 너무 어두워 보지는 못했지만 무엇인가에 공격당해 황급히 물러났다.", "휴대폰이라도 있으면 안쪽을 비출 수 있을 것 같은데...");
						}

					} else if (interactionManager.canEnterVent(true)) {
						showWall(4);
					}

					label.repaint();
					return;
				}

				// 화분에 식물 심기
				boolean potClicked = wallIndex == 0
						&& InteractionConfig.isInside(e, InteractionConfig.getBounds("POT"));

				if (potClicked && !gameState.isPlantPlanted()) {

					if (interactionManager.plantSeedling(true)) {
						showDialog("화분에 식물을 심었다.");
						label.repaint();
					} else {
						showDialog("뭔가 심을 수 있을 것 같다.");
					}
					return;
				}

				// 심은 식물에 물주기
				boolean plantedClicked = wallIndex == 0
						&& InteractionConfig.isInside(e, InteractionConfig.getBounds("PLANTED"));

				if (plantedClicked && gameState.isPlantPlanted() && !gameState.isPlantWatered()) {

					if (interactionManager.waterPlant(true)) {
						showDialogs(1500, "식물에 물을 주었다.", "반짝이던 무언가를 뱉어냈다.");
						label.repaint();
					} else {
						showDialog("입에 뭔가 있는거 같은데, 컵으로 물이라도 줘볼까?");
					}
					return;
				}

				// 세면대에서 물 받기
				if (wallIndex == 1 && InteractionConfig.isInside(e, InteractionConfig.getBounds("SINK"))) {

					if (interactionManager.fillCup(true)) {
						showDialog("컵에 물을 받았다.");
					} else {
						showDialog("세수라도 좀 할까...");
					}
					return;
				}

				// 스위치 손잡이 획득
				if (interactionManager.getSwitchHandle(
						wallIndex == 0 && InteractionConfig.isInside(e, InteractionConfig.getBounds("SWITCH")))) {
					showDialog("스위치 손잡이를 획득했다.");
					label.repaint();
					return;
				}
				
				if (wallIndex == 2 && InteractionConfig.isInside(
				        e, InteractionConfig.getBounds("LIGHT_SWITCH"))) {

				    // 손잡이가 설치되지 않은 경우
				    if (!gameState.isLeverPlaced()) {

				        if (interactionManager.placeLever(true)) {
				            showDialog("스위치 손잡이를 스위치에 부착했다.");
				            label.repaint();
				        } else {
				            showDialog("손잡이가 없네...? 씁...");
				        }

				        return;
				    }

				    // 손잡이가 설치된 경우: 조명 전환
				    gameState.setLightsOff(!gameState.isLightsOff());

				    showWall(currentwall);

				    if (gameState.isLightsOff()) {
				        showDialog("불이 꺼졌다.");
				    } else {
				        showDialog("불이 켜졌다.");
				    }

				    return;
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

			if (gameState.hasCollected(ItemType.PLANT)) {
				image = ventNone;
			} else {
				image = ventPlant;
			}

			g.drawImage(image.getImage(), 0, 0, getWidth(), getHeight(), this);
		}
	};

	public WallPanel(Runnable onMirrorClick, ItemAcquiredListener onItemAcquired, InventoryPanel inventoryPanel,
			GameState gameState, InteractionManager interactionManager) {
		this.onMirrorClick = onMirrorClick;
		this.onItemAcquired = onItemAcquired;
		this.inventoryPanel = inventoryPanel;
		this.gameState = gameState;
		this.interactionManager = new InteractionManager(inventoryPanel, gameState);
		cardLayout = new CardLayout();
		walls = new JPanel[4];
		walls_dark = new JPanel[4];

		setLayout(cardLayout);

		// 벽 이미지 cardLayout에 등록
		for (int i = 0; i < walls.length; ++i) {
			walls[i] = createWall(i, false);
			add(walls[i], "wall" + i);

			walls_dark[i] = createWall(i, true);
			add(walls_dark[i], "wall" + i + "_dark");
		}

		ventInside.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {

				if (gameState.isInputLocked()) {
					return;
				}

				// 우클릭: 아이템 선택 취소
				if (SwingUtilities.isRightMouseButton(e)) {
					inventoryPanel.clearSelection();
					return;
				}

				if (!SwingUtilities.isLeftMouseButton(e)) {
					return;
				}

				// 환풍구 나가기
				if (e.getY() >= ventInside.getHeight() * InteractionConfig.VENT_EXIT_Y_MIN) {
					showWall(3);
					return;
				}

				// 환풍구 내부 식물 획득
				if (interactionManager.getPlant(InteractionConfig.isInside(e, InteractionConfig.getBounds("PLANT")))) {

					showDialog("식물을 뜯어냈다.");
					ventInside.repaint();
					return;
				}
			}
		});

		add(ventInside, "wall4");
	}

	// 벽 보이기
	public void showWall(int index) {

		if (index ==4) {
			cardLayout.show(this, "wall4");
		} else if (gameState.isLightsOff()) {
			cardLayout.show(this, "wall" + index + "_dark");
		} else {
			cardLayout.show(this, "wall" + index);
		}

		currentwall = index;

		if (!gameState.isIntro1Shown() && index == 2) {
			gameState.setIntro1Shown(true);
			// 없으면 해당 패널 표시 이전에 출력되려해서 오류남
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					showDialogs(1500, "실종자의 마지막 행적이 확인된 곳이 여기인가…", "뭐라도 발견할 수 있으면 좋겠는데.", "......?",
							"이 문 뭔가 익숙한데? 왜지?");
				}
			});
		}

		if (!gameState.isIntro2Shown() && index == 0) {
			gameState.setIntro2Shown(true);
			showDialogs(1500, "잠깐만.", "내가 들어온 문은 어디 가고 웬 거울이 여기…?", "분명 이쪽으로 들어왔는데...");
		}
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

	// 대사 출력
	public void showDialog(String message) {

		if (dialoguePopup != null) {
			dialoguePopup.setVisible(false);
		}

		dialoguePopup = new JPopupMenu();
		dialoguePopup.setOpaque(false);
		dialoguePopup.setBorder(null);
		dialoguePopup.setBackground(new Color(0, 0, 0, 0));
		dialoguePopup.setLightWeightPopupEnabled(true);
		dialoguePopup.setBorderPainted(false);

		JLabel text = new JLabel(message);
		text.setOpaque(false);
		text.setForeground(Color.BLACK);
		text.setBorder(null);

		dialoguePopup.add(text);

		int x = (getWidth() - dialoguePopup.getPreferredSize().width) / 2;
		int y = getHeight() - dialoguePopup.getPreferredSize().height - 40;

		dialoguePopup.show(this, x, y);
	}

	// 연속되는 대사 출력
	public void showDialogs(int delay, String... messages) {

		if (messages.length == 0) {
			return;
		}

		gameState.setInputLocked(true);

		final int[] index = { 0 };

		showDialog(messages[index[0]++]);

		Timer timer = new Timer(delay, null);

		timer.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (index[0] < messages.length) {
					showDialog(messages[index[0]++]);
				} else {
					timer.stop();

					if (dialoguePopup != null) {
						dialoguePopup.setVisible(false);
					}

					gameState.setInputLocked(false);
				}
			}
		});

		timer.start();
	}
}
