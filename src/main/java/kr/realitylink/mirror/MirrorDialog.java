package kr.realitylink.mirror;

import kr.realitylink.ai.CameraService;
import kr.realitylink.common.ItemType;
import kr.realitylink.common.ItemAcquiredListener;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import javax.swing.*;
import org.opencv.videoio.Videoio;

// 거울 화면에서 카메라, 촬영 판정, 아이템 가져오기를 연결
public final class MirrorDialog extends JDialog {
	private static final Color PANEL = new Color(33, 27, 29);
	private static final Color INK = new Color(232, 223, 217);

	private final MirrorScanner scanner;
	// 가져오기를 누르면 승인된 아이템을 게임 쪽에 넘기는 콜백
	private final ItemAcquiredListener listener;

	// 아이템 임시 저장 변수
	// 원래 ArrayList로 만드려했는데, 아이템 그냥 하나만 가져오기로 해서 ItemType으로 변경함.
	private ItemType pendingItem;

	// 카메라 영상과 획득 카드를 그리는 패널
	private final MirrorPanel mirrorPanel = new MirrorPanel();
	// 80ms마다 최신 영상 표시
	private final Timer timer = new Timer(80, e -> updateCameraView()); 

	private final JLabel status = label("카메라를 연결하는 중입니다.");
	private final JLabel progress = label("대기");

	private final JButton captureButton = button("스캔하기");
	private final JButton claim = button("인벤토리에 가져오기");
	private final JButton discard = button("버리기");
	private final JButton closeButton = button("닫기");

	private boolean attached; // 스캐너 연결 여부
	private boolean waitingForCamera; // 카메라 시작 대기
	private boolean cameraStarted; // 카메라 시작 요청 여부
	private boolean scanning; // YOLO 스캔 중
	private boolean closed; // 창 종료 여부


	public MirrorDialog(Window owner, MirrorScanner scanner, ItemAcquiredListener listener) {
		
		// 부모 클래스 JDialog의 생성자를 호출해 거울 창 생성
		// JDialog: 새로운 창 만드는 class
		// ModalityType.APPLICATION_MODAL: 거울이 열리면 다른 게임 창 입력 차단
		super(owner, "거울", ModalityType.APPLICATION_MODAL);

	    this.scanner = scanner;
	    this.listener = listener;

	    setDefaultCloseOperation(DISPOSE_ON_CLOSE);

	    // 거울 화면
	    createScreen();

	    claim.setVisible(false);
	    discard.setVisible(false);
	    captureButton.setEnabled(false);

	    // 버튼 클릭시 실행할 메소드
	    captureButton.addActionListener(e -> capture());
	    claim.addActionListener(e -> claimItem());
	    discard.addActionListener(e -> discardItem());
	    closeButton.addActionListener(e -> dispose());
	    
		// 거울 열면 카메라 준비
	    // WindowAdapter: 창에서 발생하는 이벤트를 처리하는 클래스
		addWindowListener(new WindowAdapter() {
		    @Override
		    public void windowOpened(WindowEvent event) {
		    	 onWindowOpened();
		    }
		});

		setSize(780, 665);
		setMinimumSize(new Dimension(640, 490)); // 화면 최소 크기 지정
		setLocationRelativeTo(owner); // 부모 기준 거울 가운데 배치
	}
	
	private void createScreen() {
		JPanel body = new JPanel(new BorderLayout(0, 12));
		body.setBackground(PANEL);
		body.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20)); // 테두리 설정, 내부에 여백 설정
		
		JLabel instruction = label("물체를 화면에 보이도록 놓고 스캔하기를 누르세요.");
		body.add(instruction, BorderLayout.NORTH);
		body.add(mirrorPanel, BorderLayout.CENTER);
		
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
		buttons.setOpaque(false);
		
		buttons.add(progress);
		buttons.add(captureButton);
		buttons.add(claim);
		buttons.add(discard);     
		buttons.add(closeButton); 
		
		JPanel footer = new JPanel(new BorderLayout(0, 4));  
		footer.setOpaque(false);
		
		footer.add(status, BorderLayout.NORTH);
		footer.add(buttons, BorderLayout.SOUTH);
		body.add(footer, BorderLayout.SOUTH);
		
		setContentPane(body);
	}
	
	// 창 열리면 카메라 사용 준비
	private void onWindowOpened() {
		if (closed)
			return;
		// 카메라 사용 상태 등록
		attached = scanner.attach();
		
		if (!attached) {
			status.setText("거울을 열 수 없습니다.");
			return;
		}
		
		// 카메라 시작 상태
		waitingForCamera = true; 
		
		if (scanner.camera().isRunning())
			status.setText("이전 카메라 연결을 정리하는 중입니다.");
		
		// 주기적으로 카메라 상태 확인하기 위해 타이머 설정
		timer.start();
		// 카메라 상태 확인
		updateCameraView();
			
	}

	// 최신 영상을 보여 주고 스캔할 수 있는 상태인지 확인함.
	private void updateCameraView() {
		if (closed)
			return;

		CameraService camera = scanner.camera();

		// 창을 바로 다시 열어도 이전 카메라 스레드가 장치를 놓을 때까지 기다림.
		if (waitingForCamera && !camera.isRunning())
			startCamera();

		CameraService.Frame frame = null;
		if (cameraStarted) {
			frame = camera.latest();
		}
		if (frame == null) {
			mirrorPanel.setFrame(null);
		} else {
			mirrorPanel.setFrame(frame.image());
		}

		if (frame != null && status.getText().equals("카메라를 연결하는 중입니다.")) {
			status.setText("물체를 화면에 올리고 스캔하기를 누르세요.");
		}
		// 스캔 중이거나 가져올 아이템이 있으면 촬영과 재연결을 막음
		captureButton.setEnabled(!scanning && pendingItem == null && (frame != null || (!cameraStarted && !waitingForCamera)));
	}

	private void startCamera() {
		waitingForCamera = false;
		cameraStarted = true;
		status.setText("카메라를 연결하는 중입니다.");
		captureButton.setText("스캔하기");
		// num, 실행방식, 상태메시지, 종료시 실행함수
		scanner.camera().start(0, Videoio.CAP_ANY,
			    e -> {}, () -> onEdt(() -> { // 카메라가 별도 스레드에서 실행하고 종료할떄 콜백 호출해서 함수 유지해야함
			        cameraStarted = false;
			        status.setText("카메라 연결을 확인하고 다시 연결해 주세요.");
			        captureButton.setText("카메라 다시 연결");
			    })
			);
	}

	// 카메라가 연결돼 있으면 프레임 한 장을 YOLO에 보냄.
	private void capture() {
		// 스캔중 || 거울 닫음 || 아이템 획득중이면 return
		if (scanning || closed || pendingItem != null)
			return;
		// 카메라가 끊긴 상태에서는 이 버튼을 재연결에 쓴다.
		if (!cameraStarted) {
			waitingForCamera = true;
			updateCameraView();
			return;
		}
		
		BufferedImage frame;
		
		try {
			// 영상이 없거나 오래됐으면 예외 발생
			frame = scanner.camera().freshFrame().image();
		} catch (IllegalStateException failure) {
			status.setText("최근 카메라 영상이 없습니다. 잠시 후 다시 촬영하세요.");
			return;
		}
		scanning = true;
		captureButton.setEnabled(false);
		progress.setText("탐지 중");
		status.setText("물체를 찾는 중입니다.");
		// 스캔 결과를 받아 화면에 반영. 카메라 이미지, 스캔 결과
		scanner.scanYolo(frame, item -> onEdt(() -> {
		    scanning = false;
		    showDetectedItem(item);
		    updateCameraView();
		}));
		
	}

	// YOLO에서 아이템 가져오고 전달
	private void showDetectedItem(ItemType item) {
		progress.setText("완료");
		pendingItem = item;
		if (pendingItem == null) {
			status.setText("물체를 찾지 못했습니다. 다시 스캔하세요.");
			return;
		}
		mirrorPanel.showAcquired(pendingItem);
		status.setText("아이템을 발견했습니다.");
		claim.setVisible(true);
		discard.setVisible(true); 
	}

	// 가져오기 버튼을 누르면 아이템을 게임에 전달하고 거울을 닫음
	private void claimItem() {

		if (closed || pendingItem == null)
			return;

		// 아이템 게임에 전달
		listener.onItemAcquired(pendingItem);

		dispose();
	}
	
	// 아이템 가져오지 않고 버림
	private void discardItem() {
	    pendingItem = null;
	    mirrorPanel.card.setVisible(false);
	    claim.setVisible(false);
	    discard.setVisible(false);

	    progress.setText("대기");
	    status.setText("다시 스캔하세요.");
	}

	// 카메라나 YOLO에서 받은 결과는 화면 스레드에서 반영.
	private void onEdt(Runnable task) {
		SwingUtilities.invokeLater(() -> { // UI 변경 작업을 Swing 스레드에 예약
			if (!closed && isDisplayable())
				task.run();
		});
	}

	@Override
	public void dispose() {
		if (closed)
			return;
		closed = true;
		timer.stop();
		// 이 창이 사용권을 얻었으면 진행 중인 스캔과 카메라를 정리함.
		if (attached)
			scanner.detach();
		super.dispose();
	}

	private static JLabel label(String text) {
		JLabel label = new JLabel(text, SwingConstants.CENTER);
		label.setForeground(INK);
		return label;
	}

	private static JButton button(String text) {
		JButton button = new JButton(text);
		button.setBackground(new Color(33, 27, 29));
		button.setForeground(INK);
		return button;
	}

	private static final class MirrorPanel extends JPanel {
		// 화면에 마지막으로 넘긴 프레임.
		private BufferedImage frame;
		JPanel card = new JPanel(new BorderLayout(0, 8));
		private JLabel itemName = label("");
		private JLabel itemPicture = label("");

		MirrorPanel() {
			super(new GridLayout(1, 1));
			setBackground(PANEL);

			// 미리 화면만 만들어둠
			card.setBackground(PANEL);

			card.add(label("아이템 발견"), BorderLayout.NORTH);
			card.add(itemPicture, BorderLayout.CENTER);
			card.add(itemName, BorderLayout.SOUTH);

			add(card);
			card.setVisible(false);
		}

		void setFrame(BufferedImage image) {
			frame = image;
			repaint();
		}

		void showAcquired(ItemType item) {
		    // 실제 이미지 경로 확인
		    String path = item.imageFile();

		    // 아이템 이미지 불러오기
		    ImageIcon icon = new ImageIcon(MirrorDialog.class.getResource(path));
		    itemPicture.setIcon(icon);

		    itemName.setText("발견한 아이템: " + item.displayName());
		    card.setVisible(true);
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			super.paintComponent(graphics);
			if (frame == null)
				return;
			// 영상 비율을 유지한 채 패널 안에 들어갈 크기를 구한다.
			double scale = Math.min((double) getWidth() / frame.getWidth(), (double) getHeight() / frame.getHeight());
			int width = (int) (frame.getWidth() * scale);
			int height = (int) (frame.getHeight() * scale);
			int x = (getWidth() - width) / 2;
			int y = (getHeight() - height) / 2;
			graphics.drawImage(frame, x, y, width, height, null);
		}
	}
}
