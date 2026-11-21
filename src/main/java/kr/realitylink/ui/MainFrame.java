package kr.realitylink.ui;

import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;

import javax.swing.JFrame;
import javax.swing.JPanel;

import kr.realitylink.game.*;
import kr.realitylink.mirror.MirrorDialog;
import kr.realitylink.mirror.MirrorScanner;

public class MainFrame extends JFrame {

	private MirrorScanner scanner;
	private InventoryPanel inventoryPanel;
	private GameState gameState;
	private InteractionManager interactionManager;

	public MainFrame() {
		setTitle("REALITY LINK");
		setSize(1200, 600);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setLayout(new BorderLayout());
		setLocationRelativeTo(null);

		// 인벤토리 및 게임 상태 초기화
		inventoryPanel = new InventoryPanel();
		gameState = new GameState();
		interactionManager = new InteractionManager(inventoryPanel, gameState);

		// 게임 화면 구성
		JPanel gamePanel = new JPanel(new BorderLayout());

		WallPanel wallPanel = new WallPanel(this::openMirror, inventoryPanel, inventoryPanel, gameState,
				interactionManager);

		gamePanel.add(wallPanel, BorderLayout.CENTER);
		gamePanel.add(inventoryPanel, BorderLayout.EAST);

		// 시작 화면과 게임 화면 전환
		CardLayout screenLayout = new CardLayout();
		JPanel screenPanel = new JPanel(screenLayout);

		StartPanel startPanel = new StartPanel(() -> {
			wallPanel.showWall(2);
			screenLayout.show(screenPanel, "game");
		});

		screenPanel.add(startPanel, "start");
		screenPanel.add(gamePanel, "game");

		add(screenPanel, BorderLayout.CENTER);

		// 시작 화면 표시
		screenLayout.show(screenPanel, "start");

		// 게임 종료 시 AI 자원 해제
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				if (scanner != null) {
					scanner.close();
				}
			}
		});
	}

	private void openMirror() {
		// 게임에서 거울을 처음 열 때 생성
		if (scanner == null) {
			scanner = new MirrorScanner(Path.of("models", "mobilenetv2-12.onnx"));
		}

		// 기존 AI 거울 창 실행
		new MirrorDialog(this, scanner, inventoryPanel).setVisible(true);
	}
}
