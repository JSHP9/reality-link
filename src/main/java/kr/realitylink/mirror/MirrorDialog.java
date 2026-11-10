package kr.realitylink.mirror;

import kr.realitylink.ai.CameraService;
import kr.realitylink.common.ItemType;
import kr.realitylink.common.ItemAcquiredListener;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import javax.swing.*;
import org.opencv.videoio.Videoio;

// 거울 화면에서 카메라, 촬영 판정, 아이템 가져오기를 연결
public final class MirrorDialog extends JDialog {
    // 화면 배경, 글자, 획득 카드 강조색
	private static final Color PANEL = new Color(33, 27, 29);
	private static final Color INK = new Color(232, 223, 217);
	private static final Color ACCENT = new Color(182, 77, 87);
    
    // 게임 창에서 받은 스캐너로 카메라와 스캔 작업을 관리
    private final MirrorScanner scanner;
    // 가져오기를 누르면 승인된 아이템을 게임 쪽에 넘기는 콜백
    private final ItemAcquiredListener listener;
    
    // 사용할 카메라 번호와 OpenCV 연결 방식을 저장
    private final int cameraIndex;
    private final int backend;
    
    // 가져올 아이템은 거울 창마다 따로 보관
    private final MirrorAcquisition acquisition = new MirrorAcquisition();
    // 카메라 영상과 획득 카드를 그리는 패널
    private final Preview preview = new Preview();
    // 새 영상을 화면에 반영할 수 있도록 80ms마다 확인
    private final Timer timer = new Timer(80, e -> refreshPreview());
    
    private final JLabel status = label("카메라를 연결하는 중입니다.");
    private final JLabel progress = label("대기");

    private final JButton captureButton = button("스캔하기");
    private final JButton claim = button("인벤토리에 가져오기");

    private boolean attached; // 이 창이 스캐너 사용권을 얻었는지
    private boolean waitingForCamera; // 카메라를 시작할 차례를 기다리는지
    private boolean ownsStream; // 이 창에서 카메라 시작을 요청했고 종료 통보를 아직 받지 않았는지
    private boolean busy; // 한 장의 추론 결과를 기다리는지
    private boolean closed; // 창 닫기 처리를 이미 시작했는지

    public MirrorDialog(Window owner, MirrorScanner scanner, ItemAcquiredListener listener) {
        this(owner, scanner, listener, Integer.getInteger("realitylink.cameraIndex", 0), Videoio.CAP_ANY);
    }

    public MirrorDialog(Window owner, MirrorScanner scanner, ItemAcquiredListener listener, int cameraIndex, int backend) {
        super(owner, "거울", ModalityType.APPLICATION_MODAL);
        this.scanner = scanner;
        this.listener = listener;
        this.cameraIndex = cameraIndex;
        this.backend = backend;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setBackground(PANEL);
        body.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        JLabel instruction = label("물체가 카메라에 보이도록 놓고 스캔하기를 누르세요.");
        body.add(instruction, BorderLayout.NORTH);
        body.add(preview, BorderLayout.CENTER);

        JButton close = button("닫기");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
        buttons.setOpaque(false);
        buttons.add(progress);
        buttons.add(captureButton);
        buttons.add(claim);
        buttons.add(close);
        JPanel footer = new JPanel(new BorderLayout(0, 4));
        footer.setOpaque(false);
        footer.add(status, BorderLayout.NORTH);
        footer.add(buttons, BorderLayout.SOUTH);
        body.add(footer, BorderLayout.SOUTH);

        // 승인되기 전에는 가져오기를 숨기고, 영상이 들어오기 전에는 촬영을 막음.
        claim.setVisible(false);
        captureButton.setEnabled(false);
        captureButton.addActionListener(e -> capture());
        claim.addActionListener(e -> claimItems());
        close.addActionListener(e -> dispose());
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent event) {
                if (closed) return;
                // 창을 만들 때가 아니라 실제로 열릴 때 스캐너 사용권을 얻음.
                attached = scanner.attach();
                if (!attached) {
                    status.setText("거울을 열 수 없습니다.");
                    return;
                }
                waitingForCamera = true;
                if (scanner.camera().isRunning()) status.setText("이전 카메라 연결을 정리하는 중입니다.");
                timer.start();
                refreshPreview();
            }
        });
        setContentPane(body);
        setSize(780, 665);
        setMinimumSize(new Dimension(640, 490));
        setLocationRelativeTo(owner);
    }

    // 최신 영상을 보여 주고 스캔할 수 있는 상태인지 확인함.
    private void refreshPreview() {
        if (closed) return;
        
        CameraService camera = scanner.camera();
        
        // 창을 바로 다시 열어도 이전 카메라 스레드가 장치를 놓을 때까지 기다림.
        if (waitingForCamera && !camera.isRunning()) startCamera();
        
        CameraService.Frame frame = null;
        if (ownsStream) {
            frame = camera.latest();
        }
        if (frame == null) {
            preview.setFrame(null);
        } else {
            preview.setFrame(frame.image());
        }
        
        if (frame != null && status.getText().equals("카메라를 연결하는 중입니다.")) {
            status.setText("물체가 화면에 올리고 스캔하기를 누르세요.");
        }
        // 추론 중이거나 가져올 아이템이 있으면 촬영과 재연결을 막는다.
        captureButton.setEnabled(!busy && acquisition.pending().isEmpty()
                && (frame != null || (!ownsStream && !waitingForCamera)));
    }

    private void startCamera() {
        waitingForCamera = false;
        ownsStream = true;
        status.setText("카메라를 연결하는 중입니다.");
        captureButton.setText("스캔하기");
        // 세부 메시지 대신 스레드가 끝났을 때 재연결 안내를 한 번 표시.
        scanner.camera().start(cameraIndex, backend, message -> {}, () -> onEdt(() -> {
            ownsStream = false;
            status.setText("카메라 연결을 확인하고 다시 연결해 주세요.");
            captureButton.setText("카메라 다시 연결");
        }));
    }

    // 카메라가 연결돼 있으면 프레임 한 장을 YOLO에 보냄.
    private void capture() {
        if (busy || closed || !acquisition.pending().isEmpty()) return;
        // 카메라가 끊긴 상태에서는 이 버튼을 재연결에 쓴다.
        if (!ownsStream) {
            waitingForCamera = true;
            refreshPreview();
            return;
        }
        BufferedImage frame;
        try {
            // 오래됐거나 아직 수신하지 못한 프레임은 판정에 넣지 않음.
            frame = scanner.camera().freshFrame().image();
        } catch (IllegalStateException failure) {
            status.setText("최근 카메라 영상이 없습니다. 잠시 후 다시 촬영하세요.");
            return;
        }
        busy = true;
        captureButton.setEnabled(false);
        progress.setText("탐지 중");
        status.setText("물체를 찾는 중입니다.");
        // 촬영한 전체 프레임을 보내고, 화면을 바꿀 때만 EDT로 돌아온다.
        scanner.scanYolo(frame, (items, failure) -> onEdt(() -> {
            busy = false;
            if (failure != null) {
                progress.setText("실패");
                status.setText(failure.getMessage());
            } else {
                showDetectedItems(items);
            }
            refreshPreview();
        }));
    }

    private void showDetectedItems(Set<ItemType> items) {
        progress.setText("완료");
        acquisition.setDetectedItems(items);
        if (items.isEmpty()) {
            status.setText("물체를 찾지 못했습니다. 위치를 조정하고 다시 스캔하세요.");
            return;
        }
        preview.showAcquired(acquisition.pending());
        claim.setVisible(true);
        status.setText("가져오기를 누르면 발견한 아이템을 게임에 전달합니다.");
    }

    // 승인과 게임 전달은 별개라서 가져오기를 눌렀을 때만 콜백을 호출.
    private void claimItems() {
        if (closed || acquisition.pending().isEmpty()) return;
        claim.setEnabled(false);
        try {
            acquisition.claim(listener);
        } finally {
            dispose();
        }
    }

    // 카메라나 YOLO에서 받은 결과는 화면 스레드에서 반영.
    private void onEdt(Runnable task) {
        SwingUtilities.invokeLater(() -> {
            if (!closed && isDisplayable()) task.run();
        });
    }

    @Override
    public void dispose() {
        if (closed) return;
        closed = true;
        timer.stop();
        // 이 창이 사용권을 얻었으면 진행 중인 스캔과 카메라를 정리함.
        if (attached) scanner.detach();
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

    private static final class Preview extends JPanel {
        // 화면에 마지막으로 넘긴 프레임.
        private BufferedImage frame;

        Preview() {
            super(new GridBagLayout());
            setBackground(new Color(33, 27, 29));
        }

        void setFrame(BufferedImage image) {
            frame = image;
            repaint();
        }

        void showAcquired(List<ItemType> items) {
            JPanel card = new JPanel(new GridLayout(1, items.size(), 12, 0));
            card.setBackground(PANEL);
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(ACCENT),
                    BorderFactory.createEmptyBorder(16, 16, 16, 16)));
            for (ItemType item : items) {
                JPanel itemPanel = new JPanel(new BorderLayout(0, 8));
                itemPanel.setOpaque(false);
                itemPanel.add(label("획득 성공"), BorderLayout.NORTH);
                itemPanel.add(itemImage(item), BorderLayout.CENTER);
                itemPanel.add(label("현실의 " + item.displayName() + "이 게임 세계로 들어왔습니다."), BorderLayout.SOUTH);
                card.add(itemPanel);
            }
            add(card);
            revalidate();
            repaint();
        }

        private JLabel itemImage(ItemType item) {
            // 외부 PNG를 먼저 찾고, 없으면 앱에 포함된 리소스를 찾음.
            Path imagePath = Path.of("assets", "items", item.imageFile());
            JLabel badge = label(item.displayName());
            ImageIcon icon = null;
            if (Files.isRegularFile(imagePath)) {
                icon = new ImageIcon(imagePath.toString());
            } else {
                URL resource = MirrorDialog.class.getResource("/assets/items/" + item.imageFile());
                if (resource != null) {
                    icon = new ImageIcon(resource);
                }
            }
            if (icon != null && icon.getIconWidth() > 0 && icon.getIconHeight() > 0) {
                double scale = Math.min(96.0 / icon.getIconWidth(), 96.0 / icon.getIconHeight());
                badge.setIcon(new ImageIcon(icon.getImage().getScaledInstance(
                        Math.max(1, (int)(icon.getIconWidth() * scale)),
                        Math.max(1, (int)(icon.getIconHeight() * scale)), Image.SCALE_SMOOTH)));
                badge.setText("");
                return badge;
            }
            // 쓸 수 있는 이미지가 없으면 아이템 이름을 보여 줌.
            badge.setPreferredSize(new Dimension(96, 72));
            badge.setOpaque(true);
            badge.setBackground(new Color(33, 27, 29));
            badge.setForeground(ACCENT);
            badge.setFont(badge.getFont().deriveFont(Font.BOLD, 24f));
            return badge;
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (frame == null) return;
            // 영상 비율을 유지한 채 패널 안에 들어갈 크기를 구한다.
            double scale = Math.min((double)getWidth() / frame.getWidth(), (double)getHeight() / frame.getHeight());
            int width = (int)(frame.getWidth() * scale);
            int height = (int)(frame.getHeight() * scale);
            int x = (getWidth() - width) / 2;
            int y = (getHeight() - height) / 2;
            graphics.drawImage(frame, x, y, width, height, null);
        }
    }
}
