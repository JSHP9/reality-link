package kr.realitylink.mirror;

import kr.realitylink.ai.CameraService;
import kr.realitylink.ai.VerificationSession;
import kr.realitylink.common.ItemType;
import kr.realitylink.common.ItemAcquiredListener;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import javax.swing.*;
import org.opencv.videoio.Videoio;

/** Modal game interaction. Construct and show on the Swing EDT. */
public final class MirrorDialog extends JDialog {
    private static final Color PANEL = new Color(22, 29, 40);
    private static final Color INK = new Color(230, 239, 250);
    private static final Color ACCENT = new Color(126, 239, 215);
    private final MirrorScanner scanner;
    private final ItemAcquiredListener listener;
    private final int cameraIndex;
    private final int backend;
    private final MirrorAcquisition acquisition = new MirrorAcquisition();
    private final Preview preview = new Preview();
    private final JLabel status = label("카메라를 연결하는 중입니다.");
    private final JLabel progress = label("0/3");
    private final JButton capture = button("촬영");
    private final JButton claim = button("인벤토리에 가져오기");
    private final Timer timer = new Timer(80, e -> refreshPreview());
    private boolean attached;
    private boolean waitingForCamera;
    private boolean ownsStream;
    private boolean busy;
    private boolean closed;

    public MirrorDialog(Window owner, MirrorScanner scanner, ItemAcquiredListener listener) {
        this(owner, scanner, listener, Integer.getInteger("realitylink.cameraIndex", 0), Videoio.CAP_ANY);
    }

    public MirrorDialog(Window owner, MirrorScanner scanner, ItemAcquiredListener listener, int cameraIndex, int backend) {
        super(owner, "REALITY LINK  |  거울", ModalityType.APPLICATION_MODAL);
        this.scanner = Objects.requireNonNull(scanner);
        this.listener = Objects.requireNonNull(listener);
        this.cameraIndex = cameraIndex;
        this.backend = backend;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setBackground(PANEL);
        body.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        JLabel instruction = label("펜이나 컵 전체를 스캔 영역 안에 놓고 같은 물체를 세 번 촬영하세요.");
        body.add(instruction, BorderLayout.NORTH);
        body.add(preview, BorderLayout.CENTER);

        JButton close = button("닫기");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
        buttons.setOpaque(false);
        buttons.add(progress);
        buttons.add(capture);
        buttons.add(claim);
        buttons.add(close);
        JPanel footer = new JPanel(new BorderLayout(0, 4));
        footer.setOpaque(false);
        footer.add(status, BorderLayout.NORTH);
        footer.add(buttons, BorderLayout.SOUTH);
        body.add(footer, BorderLayout.SOUTH);

        claim.setVisible(false);
        capture.setEnabled(false);
        capture.addActionListener(e -> capture());
        claim.addActionListener(e -> claimItems());
        close.addActionListener(e -> dispose());
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent event) {
                if (closed) return;
                attached = scanner.attach();
                if (!attached) {
                    status.setText("거울 카메라를 열 수 없습니다. 열린 거울 창을 먼저 닫으세요.");
                    return;
                }
                waitingForCamera = true;
                if (scanner.camera().isRunning()) status.setText("이전 카메라 연결을 정리하는 중입니다.");
                timer.start();
                refreshPreview();
            }
            @Override public void windowClosed(WindowEvent event) { release(); }
        });
        setContentPane(body);
        setSize(780, 665);
        setMinimumSize(new Dimension(640, 490));
        setLocationRelativeTo(owner);
    }

    private void refreshPreview() {
        if (closed) return;
        CameraService camera = scanner.camera();
        if (waitingForCamera && !camera.isRunning()) startCamera();
        CameraService.Frame frame = ownsStream ? camera.latest() : null;
        preview.setFrame(frame == null ? null : frame.image());
        if (frame != null && status.getText().equals("카메라를 연결하는 중입니다.")) {
            status.setText("스캔 영역에 물체를 놓고 촬영하세요.");
        }
        capture.setEnabled(!busy && acquisition.pending().isEmpty()
                && (frame != null || (!ownsStream && !waitingForCamera)));
    }

    private void startCamera() {
        waitingForCamera = false;
        ownsStream = true;
        status.setText("카메라를 연결하는 중입니다.");
        capture.setText("촬영");
        scanner.camera().start(cameraIndex, backend, message -> onEdt(() -> {
            if (message.startsWith("카메라 오류")) {
                status.setText("카메라 오류가 발생했습니다. 장치 연결과 다른 앱의 사용 여부를 확인하세요.");
            }
        }), () -> onEdt(() -> {
            ownsStream = false;
            capture.setText("카메라 다시 연결");
            refreshPreview();
        }));
    }

    private void capture() {
        if (busy || closed || !acquisition.pending().isEmpty()) return;
        if (!ownsStream) {
            waitingForCamera = true;
            refreshPreview();
            return;
        }
        BufferedImage frame;
        try {
            frame = scanner.camera().freshFrame().image();
        } catch (IllegalStateException failure) {
            status.setText("최근 카메라 영상이 없습니다. 잠시 후 다시 촬영하세요.");
            return;
        }
        if (acquisition.progress() == VerificationSession.SHOTS) {
            acquisition.retry();
            progress.setText("0/" + VerificationSession.SHOTS);
        }
        busy = true;
        capture.setEnabled(false);
        capture.setText("촬영");
        status.setText("물체를 확인하는 중입니다.");
        scanner.scan(frame).whenComplete((scan, failure) -> onEdt(() -> {
            busy = false;
            if (failure != null) {
                status.setText("인식 오류가 발생했습니다. 모델과 허용 목록 파일을 확인한 뒤 다시 촬영하세요.");
                Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                System.err.println("거울 인식 오류 " + cause.getMessage());
            } else {
                showDecision(acquisition.accept(scan));
            }
            refreshPreview();
        }));
    }

    private void showDecision(VerificationSession.Decision decision) {
        progress.setText(decision.shot() + "/" + VerificationSession.SHOTS);
        List<ItemType> items = acquisition.pending();
        if (!items.isEmpty()) {
            preview.showAcquired(items);
            claim.setVisible(true);
            status.setText("가져오기를 누르면 인벤토리에 담깁니다.");
        } else if (decision.complete()) {
            status.setText("아이템을 확인하지 못했습니다. 물체 위치를 조정하고 다시 스캔하세요.");
            capture.setText("다시 스캔");
        } else if (decision.candidates().isEmpty()) {
            status.setText("이번 촬영에서는 물체를 확인하지 못했습니다. 위치를 조정하고 다시 촬영하세요.");
        } else {
            status.setText("물체를 확인했습니다. 같은 물체로 다음 촬영을 진행하세요.");
        }
    }

    private void claimItems() {
        if (closed || acquisition.pending().isEmpty()) return;
        claim.setEnabled(false);
        try {
            acquisition.claim(listener);
        } catch (RuntimeException failure) {
            JOptionPane.showMessageDialog(this, "인벤토리에 아이템을 전달하는 중 오류가 발생했습니다.",
                    "아이템 전달 오류", JOptionPane.ERROR_MESSAGE);
            System.err.println("아이템 전달 오류 " + failure.getMessage());
        } finally {
            dispose();
        }
    }

    private void onEdt(Runnable task) {
        SwingUtilities.invokeLater(() -> {
            if (!closed && isDisplayable()) task.run();
        });
    }

    private void release() {
        if (closed) return;
        closed = true;
        timer.stop();
        if (attached) scanner.detach();
    }

    @Override public void dispose() {
        release();
        super.dispose();
    }

    private static JLabel label(String text) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setForeground(INK);
        return label;
    }

    private static JButton button(String text) {
        JButton button = new JButton(text);
        button.setBackground(new Color(48, 65, 83));
        button.setForeground(INK);
        return button;
    }

    private static final class Preview extends JPanel {
        private BufferedImage frame;

        Preview() {
            super(new GridBagLayout());
            setBackground(new Color(9, 14, 20));
        }

        void setFrame(BufferedImage image) {
            if (frame == image) return;
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
            Path imagePath = Path.of("assets", "items", item.imageFile());
            JLabel badge = label(item.displayName());
            var resource = MirrorDialog.class.getResource("/assets/items/" + item.imageFile());
            ImageIcon icon = Files.isRegularFile(imagePath) ? new ImageIcon(imagePath.toString())
                    : resource == null ? null : new ImageIcon(resource);
            if (icon != null) {
                if (icon.getIconWidth() > 0 && icon.getIconHeight() > 0) {
                    double scale = Math.min(96.0 / icon.getIconWidth(), 96.0 / icon.getIconHeight());
                    badge.setIcon(new ImageIcon(icon.getImage().getScaledInstance(
                            Math.max(1, (int)(icon.getIconWidth() * scale)),
                            Math.max(1, (int)(icon.getIconHeight() * scale)), Image.SCALE_SMOOTH)));
                    badge.setText("");
                    return badge;
                }
            }
            badge.setPreferredSize(new Dimension(96, 72));
            badge.setOpaque(true);
            badge.setBackground(new Color(41, 65, 76));
            badge.setForeground(ACCENT);
            badge.setFont(badge.getFont().deriveFont(Font.BOLD, 24f));
            return badge;
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (frame == null) return;
            double scale = Math.min((double)getWidth() / frame.getWidth(), (double)getHeight() / frame.getHeight());
            int width = (int)(frame.getWidth() * scale);
            int height = (int)(frame.getHeight() * scale);
            int x = (getWidth() - width) / 2;
            int y = (getHeight() - height) / 2;
            Graphics2D g = (Graphics2D)graphics.create();
            try {
                g.drawImage(frame, x, y, width, height, null);
                Rectangle area = MirrorScanner.roi(frame);
                int left = x + (int)(area.x * scale);
                int top = y + (int)(area.y * scale);
                int right = x + (int)((area.x + area.width) * scale);
                int bottom = y + (int)((area.y + area.height) * scale);
                g.setColor(new Color(0, 0, 0, 145));
                g.fillRect(x, y, width, top - y);
                g.fillRect(x, bottom, width, y + height - bottom);
                g.fillRect(x, top, left - x, bottom - top);
                g.fillRect(right, top, x + width - right, bottom - top);
                g.setColor(ACCENT);
                g.setStroke(new BasicStroke(3f));
                g.drawRect(left, top, right - left, bottom - top);
            } finally {
                g.dispose();
            }
        }
    }
}
