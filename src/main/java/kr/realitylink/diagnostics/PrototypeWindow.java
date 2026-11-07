package kr.realitylink.diagnostics;

import kr.realitylink.ai.Allowlist;
import kr.realitylink.ai.CameraService;
import kr.realitylink.ai.Classifier;
import kr.realitylink.ai.DiagnosticCapture;
import kr.realitylink.ai.VerificationSession;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.opencv.videoio.Videoio;

/** Original classifier UI plus an independent allowlist/session game policy. */
public final class PrototypeWindow extends JFrame {
    private final CameraService camera = new CameraService();
    private final ExecutorService inference = Executors.newSingleThreadExecutor(r -> {
        return new Thread(r, "onnx-inference");
    });
    private final ImagePanel live = new ImagePanel("카메라 미연결");
    private final ImagePanel captured = new ImagePanel("마지막 추론 입력");
    private final ImagePanel fullInput = new ImagePanel("전체 프레임의 실제 224×224 입력");
    private final ImagePanel roiInput = new ImagePanel("ROI의 실제 224×224 입력");
    private final JTextArea results = new JTextArea();
    private final JLabel cameraStatus = new JLabel("카메라를 연결하거나 이미지 파일을 선택하세요.");
    private final JLabel modelStatus = new JLabel("모델 로딩 중...");
    private final JButton open = new JButton("카메라 연결");
    private final JButton stop = new JButton("카메라 중지");
    private final JButton shoot = new JButton("촬영 / 분류");
    private final JButton file = new JButton("이미지 파일 분류");
    private final JSpinner index = new JSpinner(new SpinnerNumberModel(0, 0, 20, 1));
    private final JComboBox<String> backend = new JComboBox<>(new String[]{"자동", "DirectShow (Windows)", "MSMF (Windows)"});
    private final JSpinner minimum = new JSpinner(new SpinnerNumberModel(15.0, 0.0, 100.0, 1.0));
    private final JComboBox<String> actual = new JComboBox<>(new String[]{"PLAY", "PEN", "CUP", "NONE:DRIVER", "NONE:SPEAKER", "NONE:HAND", "NONE:EMPTY", "NONE:OTHER"});
    private final JSpinner roiWidth = new JSpinner(new SpinnerNumberModel(70, 10, 100, 5));
    private final JSpinner roiHeight = new JSpinner(new SpinnerNumberModel(70, 10, 100, 5));
    private final JComboBox<String> gameInput = new JComboBox<>(new String[]{"ROI", "FULL"});
    private final JComboBox<String> condition = new JComboBox<>(new String[]{"UNSPECIFIED", "HANDHELD", "ON_DESK", "HAND_ONLY", "EMPTY_BACKGROUND"});
    private final JTextField experiment = new JTextField("experiment-1", 14);
    private final JTextField notes = new JTextField(32);
    private final JButton report = new JButton("실험 보고서 생성");
    private final JButton resetSession = new JButton("세션 취소 / 초기화");
    private final JButton reload = new JButton("허용 목록 다시 읽기");
    private final JButton clearInventory = new JButton("인벤토리 비우기");
    private final JLabel sessionStatus = new JLabel("새 세션: 0/3 — 같은 물체를 3회 촬영하세요.");
    private final JLabel inventoryStatus = new JLabel("인벤토리: []");
    private final Set<String> inventory = new LinkedHashSet<>();
    private final Path config = Path.of(System.getProperty("realitylink.allowlist", "config/allowlist.properties"));
    private final RecognitionJournal journal = new RecognitionJournal(Path.of("logs", "recognition-"
            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"))
            + "-" + java.util.UUID.randomUUID().toString().substring(0, 8) + ".tsv"));
    private final DiagnosticJournal diagnostic = new DiagnosticJournal(Path.of("diagnostics",
            journal.path().getFileName().toString().replace(".tsv", "")));
    private final Timer preview;
    // Classifier is used/closed only on the inference executor. UI flags belong to the EDT.
    private Classifier classifier;
    private Allowlist policy;
    private VerificationSession session;
    private VerificationSession comparisonSession;
    private AttemptContext sessionContext;
    private String lastSessionId = "";
    private record AttemptContext(double threshold, String actual, String sourceType, String gameInput,
                                  int roiWidth, int roiHeight, String condition, String experiment, String notes) {
        DiagnosticJournal.Context diagnosticContext() {
            return new DiagnosticJournal.Context(actual, condition, experiment, notes, sourceType, gameInput, roiWidth, roiHeight, threshold);
        }
    }
    // Mirrored from completed worker callbacks, used only to lock session settings on the EDT.
    private int sessionShots;
    private String sourceLock = "";
    private boolean ready, busy, closing;

    public PrototypeWindow(Path model) {
        super("REALITY LINK — 원본·224 입력 / FULL·ROI 비교 진단");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(1180, 920);
        setMinimumSize(new Dimension(1100, 820));
        setLocationRelativeTo(null);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(new JLabel("카메라 번호")); controls.add(index); controls.add(backend);
        controls.add(open); controls.add(stop); controls.add(shoot); controls.add(file);
        JPanel policyControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        policyControls.add(new JLabel("최소 개별 점수 (%)")); policyControls.add(minimum);
        policyControls.add(new JLabel("실제 물체 (PLAY=일반 획득, 나머지=기록용 테스트)")); policyControls.add(actual);
        JPanel sessionControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        sessionControls.add(resetSession); sessionControls.add(reload); sessionControls.add(clearInventory);
        JPanel roiControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        roiControls.add(new JLabel("중앙 ROI 가로%")); roiControls.add(roiWidth);
        roiControls.add(new JLabel("세로%")); roiControls.add(roiHeight);
        roiControls.add(new JLabel("획득 판정 입력")); roiControls.add(gameInput);
        roiControls.add(new JLabel("촬영 조건")); roiControls.add(condition);
        JPanel experimentControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        experimentControls.add(new JLabel("실험 묶음")); experimentControls.add(experiment);
        experimentControls.add(new JLabel("조건 메모")); experimentControls.add(notes); experimentControls.add(report);
        JPanel toolbar = new JPanel(new GridLayout(5, 1));
        toolbar.add(controls); toolbar.add(policyControls); toolbar.add(roiControls); toolbar.add(experimentControls); toolbar.add(sessionControls);
        JPanel images = new JPanel(new GridLayout(2, 2, 8, 4));
        live.title("ROI 스캐너 — 물체 전체를 노란 영역 중앙에 놓으세요");
        captured.title("저장된 원본 프레임 — 노란색 ROI (저장 PNG에는 선 없음)");
        fullInput.title("FULL — 실제 RGB 224×224 입력 (정규화 전)");
        roiInput.title("ROI — 실제 RGB 224×224 입력 (정규화 전)");
        live.scannerOverlay = true;
        images.add(live); images.add(captured); images.add(fullInput); images.add(roiInput);
        images.setPreferredSize(new Dimension(1000, 360));
        JPanel north = new JPanel(new BorderLayout()); north.add(toolbar, BorderLayout.NORTH); north.add(images);
        results.setEditable(false);
        results.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        results.setLineWrap(true); results.setWrapStyleWord(true);
        JPanel statuses = new JPanel(new GridLayout(6, 1));
        statuses.add(modelStatus); statuses.add(cameraStatus);
        statuses.add(sessionStatus); statuses.add(inventoryStatus);
        statuses.add(new JLabel("스캔 안내: 물체 전체를 노란 ROI 중앙에 놓고 3회 촬영하세요. 아래 ROI 224 입력에서 잘림을 확인하세요."));
        statuses.add(new JLabel("원본 Top-3 / 최고 개별 점수 / 3회 중 2회. 허용 목록은 AI 성능을 개선하지 않습니다."));
        add(north, BorderLayout.NORTH); add(new JScrollPane(results)); add(statuses, BorderLayout.SOUTH);
        open.addActionListener(e -> openCamera());
        stop.addActionListener(e -> { camera.stop(); cameraStatus.setText("카메라 중지 요청 중..."); stop.setEnabled(false); });
        shoot.addActionListener(e -> {
            try { classify(camera.freshFrame().image(), "카메라 촬영"); }
            catch (Exception ex) { cameraStatus.setText(ex.getMessage()); }
        });
        file.addActionListener(e -> chooseFile());
        resetSession.addActionListener(e -> cancelSession());
        reload.addActionListener(e -> reloadPolicy());
        clearInventory.addActionListener(e -> { inventory.clear(); inventoryStatus.setText("인벤토리: []"); });
        report.addActionListener(e -> generateReport());
        gameInput.addActionListener(e -> { live.showModelGuide = gameInput.getSelectedItem().equals("FULL"); live.repaint(); });
        actual.setToolTipText("PLAY는 실제 물체 미지정입니다. 실물 검증 시 PEN/CUP 또는 NONE:*와 촬영 조건을 선택하세요.");
        actual.addActionListener(e -> {
            if (actual.getSelectedItem().equals("NONE:HAND")) condition.setSelectedItem("HAND_ONLY");
            if (actual.getSelectedItem().equals("NONE:EMPTY")) condition.setSelectedItem("EMPTY_BACKGROUND");
        });
        addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { shutdown(); } });
        preview = new Timer(80, e -> {
            CameraService.Frame frame = camera.latest();
            live.setImage(frame == null ? null : frame.image());
            live.setRoi(frame == null ? null : DiagnosticCapture.bounds(frame.image().getWidth(), frame.image().getHeight(),
                    ((Number)roiWidth.getValue()).intValue(), ((Number)roiHeight.getValue()).intValue()));
            updateButtons();
        });
        preview.start();
        updateButtons();
        inference.execute(() -> {
            try {
                classifier = new Classifier(model);
                Allowlist loaded = Allowlist.load(config);
                policy = loaded;
                String metadata = classifier.metadata();
                onEdt(() -> {
                    ready = true; minimum.setValue(loaded.minimumScore() * 100); addItemOptions(loaded);
                    modelStatus.setText("모델·허용 목록 준비 완료 / CPU / 원본 Top-3 / " + config);
                    results.append(metadata + "허용 목록: " + loaded.mapping() + "\n기존 획득 기록: " + journal.path().toAbsolutePath()
                            + "\n원본·입력 PNG 및 비교 기록: " + diagnostic.directory().toAbsolutePath()
                            + "\n매 촬영의 원본과 FULL/ROI 입력 PNG를 로컬 저장합니다. 손 제거 모델은 사용하지 않습니다.\n"); updateButtons();
                });
            } catch (Exception | LinkageError e) {
                onEdt(() -> { modelStatus.setText("모델 또는 설정 로딩 실패: " + e.getMessage()); results.append("모델·설정 준비 방법은 README를 확인하세요.\n" + e); });
            }
        });
    }

    private void openCamera() {
        int api = switch (backend.getSelectedIndex()) { case 1 -> Videoio.CAP_DSHOW; case 2 -> Videoio.CAP_MSMF; default -> Videoio.CAP_ANY; };
        cameraStatus.setText("카메라 연결 중...");
        camera.start((Integer) index.getValue(), api,
                message -> onEdt(() -> cameraStatus.setText(message)),
                () -> onEdt(() -> { updateButtons(); if (cameraStatus.getText().contains("중지")) cameraStatus.setText("카메라 중지 완료"); }));
        updateButtons();
    }

    private void chooseFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("이미지 (JPG, PNG, BMP)", "jpg", "jpeg", "png", "bmp"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path selected = chooser.getSelectedFile().toPath();
        AttemptContext context;
        try { context = context("FILE"); }
        catch (IllegalArgumentException e) { results.append(e.getMessage() + "\n"); return; }
        setBusy(true);
        inference.execute(() -> {
            try {
                BufferedImage image = ImageIO.read(selected.toFile());
                if (image == null) throw new IllegalArgumentException("지원하지 않거나 손상된 이미지입니다.");
                runInference(image, selected.toString(), context);
            } catch (Exception | LinkageError e) { showInferenceError(e); }
            finally { onEdt(() -> setBusy(false)); }
        });
    }

    private void classify(BufferedImage image, String source) {
        if (!ready || busy || closing) return;
        AttemptContext context = context("CAMERA");
        setBusy(true);
        inference.execute(() -> {
            try { runInference(image, source, context); }
            catch (Exception | LinkageError e) { showInferenceError(e); }
            finally { onEdt(() -> setBusy(false)); }
        });
    }

    private AttemptContext context(String sourceType) {
        try { minimum.commitEdit(); }
        catch (java.text.ParseException e) { throw new IllegalArgumentException("최소 점수를 0~100 숫자로 입력하세요."); }
        try { roiWidth.commitEdit(); roiHeight.commitEdit(); }
        catch (java.text.ParseException e) { throw new IllegalArgumentException("ROI 크기를 10~100 정수로 입력하세요."); }
        if (experiment.getText().isBlank()) throw new IllegalArgumentException("실험 묶음 이름을 입력하세요.");
        String object = (String) actual.getSelectedItem();
        String scene = (String) condition.getSelectedItem();
        if ((object.equals("PEN") || object.equals("CUP")) && !scene.equals("HANDHELD") && !scene.equals("ON_DESK"))
            throw new IllegalArgumentException("PEN/CUP 실물 테스트는 HANDHELD(손에 들기) 또는 ON_DESK(책상 위)를 선택하세요.");
        if (object.equals("NONE:HAND") && !scene.equals("HAND_ONLY"))
            throw new IllegalArgumentException("손만 있는 장면은 HAND_ONLY 조건을 선택하세요.");
        if (object.equals("NONE:EMPTY") && !scene.equals("EMPTY_BACKGROUND"))
            throw new IllegalArgumentException("빈 배경은 EMPTY_BACKGROUND 조건을 선택하세요.");
        return new AttemptContext(((Number)minimum.getValue()).doubleValue() / 100,
                (String)actual.getSelectedItem(), sourceType, (String)gameInput.getSelectedItem(),
                ((Number)roiWidth.getValue()).intValue(), ((Number)roiHeight.getValue()).intValue(),
                (String)condition.getSelectedItem(), experiment.getText().trim(), notes.getText().trim());
    }

    private void runInference(BufferedImage image, String source, AttemptContext context) throws Exception {
        if (session != null && !context.equals(sessionContext))
            throw new IllegalStateException("세션 중 설정·실제 물체·입력 종류를 바꿀 수 없습니다. 세션을 취소하세요.");
        DiagnosticCapture.Pair pair = DiagnosticCapture.capture(classifier, image, context.roiWidth(), context.roiHeight(), diagnostic.directory());
        java.nio.file.Files.writeString(pair.directory().resolve("capture.txt"), "\ncontext=" + context + "\nsource=" + source
                + "\nmapping=" + new java.util.TreeMap<>(policy.mapping()) + "\n", java.nio.file.StandardOpenOption.APPEND);
        if (session == null) {
            session = new VerificationSession(policy, context.threshold());
            comparisonSession = new VerificationSession(policy, context.threshold()); sessionContext = context;
        }
        boolean fullSelected = context.gameInput().equals("FULL");
        VerificationSession.Decision fullDecision = (fullSelected ? session : comparisonSession).accept(pair.full().classification());
        VerificationSession.Decision roiDecision = (fullSelected ? comparisonSession : session).accept(pair.region().classification());
        VerificationSession.Decision decision = fullSelected ? fullDecision : roiDecision;
        Classifier.Classification result = fullSelected ? pair.full().classification() : pair.region().classification();
        lastSessionId = decision.sessionId();
        String logMessage;
        try {
            if (decision.shot() == 1) journal.event(decision.sessionId(), context.actual(),
                    "START context=" + context + " mapping=" + policy.mapping() + " model=" + Classifier.MODEL_SHA256);
            diagnostic.append(pair, context.diagnosticContext(), source, policy, fullDecision, roiDecision, decision.sessionId());
            journal.append(context.actual(), context.sourceType() + ":" + source + " input=" + context.gameInput()
                    + " condition=" + context.condition() + " capture=" + pair.id(), context.threshold(), result, decision);
            logMessage = "비교 TSV: " + diagnostic.directory().resolve("pairs.tsv")
                    + "\n원본·입력 PNG: " + pair.directory().toAbsolutePath() + "\n";
        } catch (java.io.IOException e) {
            logMessage = "일부 TSV 기록 저장 실패: " + e.getMessage()
                    + "\nPNG 보관 위치: " + pair.directory().toAbsolutePath() + "\n전체 증거를 확인하고 저장 문제 해결 후 다시 테스트하세요.\n";
        }
        if (decision.complete()) { session = null; comparisonSession = null; sessionContext = null; }
        String text = "\n" + LocalDateTime.now() + " / " + source + " / 같은 프레임의 FULL·ROI 쌍 / " + context.condition()
                + "\n[FULL 원본 Top-5]\n" + pair.full().classification().format() + fullDecision.format()
                + "\n[ROI 원본 Top-5]\n" + pair.region().classification().format() + roiDecision.format()
                + String.format(java.util.Locale.ROOT, "최소 개별 점수 %.2f%% / 실제 물체 %s\n", context.threshold() * 100, context.actual())
                + "[게임에 반영하는 경로: " + context.gameInput() + "]\n" + decision.format() + logMessage
                + (context.actual().equals("PLAY") ? "" : "기록용 테스트: 인벤토리는 변경하지 않습니다.\n")
                + (decision.complete() ? "실제 물체 기준 최종 평가: " + RecognitionJournal.assessment(context.actual(), decision.approved()) + "\n" : "");
        onEdt(() -> {
            captured.setImage(pair.original()); captured.setRoi(pair.roi());
            fullInput.setImage(pair.full().inputRgb()); roiInput.setImage(pair.region().inputRgb());
            results.append(text); results.setCaretPosition(results.getDocument().getLength());
            sessionShots = decision.complete() ? 0 : decision.shot();
            sourceLock = decision.complete() ? "" : context.sourceType();
            sessionStatus.setText(decision.complete() ? "세션 종료 / 승인 " + decision.approved() + " / 다음 세션 0/3"
                    : "세션 " + decision.shot() + "/3 / 누적 " + decision.votes() + " / 같은 물체를 계속 촬영하세요.");
            if (decision.complete() && context.actual().equals("PLAY")) {
                inventory.addAll(decision.approved()); inventoryStatus.setText("인벤토리: " + inventory);
            }
            updateButtons();
        });
    }

    private void addItemOptions(Allowlist loaded) {
        for (String item : loaded.items()) {
            boolean found = false;
            for (int i = 0; i < actual.getItemCount(); i++) if (actual.getItemAt(i).equals(item)) found = true;
            if (!found) actual.addItem(item);
        }
    }

    private void cancelSession() {
        setBusy(true);
        inference.execute(() -> {
            try { if (session != null) journal.event(lastSessionId, sessionContext.actual(), "CANCELLED: incomplete session discarded"); }
            catch (java.io.IOException e) { onEdt(() -> results.append("취소 기록 저장 실패: " + e.getMessage() + "\n")); }
            session = null; comparisonSession = null; sessionContext = null;
            onEdt(() -> {
                sessionShots = 0; sourceLock = "";
                sessionStatus.setText("세션 취소 완료 / 새 세션 0/3"); results.append("\n미완료 세션 초기화: 획득 없음\n"); setBusy(false);
            });
        });
    }

    private void reloadPolicy() {
        setBusy(true);
        inference.execute(() -> {
            try {
                Allowlist loaded = Allowlist.load(config); policy = loaded;
                onEdt(() -> { minimum.setValue(loaded.minimumScore() * 100); addItemOptions(loaded); results.append("설정 다시 읽기 완료: " + loaded.mapping() + "\n"); });
            } catch (Exception e) { onEdt(() -> results.append("설정 읽기 실패, 기존 설정 유지: " + e.getMessage() + "\n")); }
            finally { onEdt(() -> setBusy(false)); }
        });
    }

    private void generateReport() {
        setBusy(true);
        inference.execute(() -> {
            try {
                Path path = ExperimentReport.generate(Path.of("diagnostics"));
                onEdt(() -> results.append("\n실험 보고서: " + path.toAbsolutePath() + "\n정답을 지정한 실제 촬영 데이터만 집계합니다.\n"));
            } catch (Exception e) { onEdt(() -> results.append("보고서 생성 실패: " + e.getMessage() + "\n")); }
            finally { onEdt(() -> setBusy(false)); }
        });
    }

    private void showInferenceError(Throwable e) {
        onEdt(() -> results.append("\n분류 실패: " + e.getMessage() + "\n이 시도는 3회에 포함하지 않습니다. 같은 입력 종류로 재시도하세요.\n"));
    }
    private void setBusy(boolean value) { busy = value; updateButtons(); }
    private void updateButtons() {
        open.setEnabled(!closing && !camera.isRunning());
        stop.setEnabled(!closing && camera.isRunning());
        index.setEnabled(!camera.isRunning()); backend.setEnabled(!camera.isRunning());
        shoot.setEnabled(!closing && ready && !busy && camera.isRunning() && camera.latest() != null && !sourceLock.equals("FILE"));
        file.setEnabled(!closing && ready && !busy && !sourceLock.equals("CAMERA"));
        boolean idle = !closing && ready && !busy;
        minimum.setEnabled(idle && sessionShots == 0); actual.setEnabled(idle && sessionShots == 0);
        reload.setEnabled(idle && sessionShots == 0); resetSession.setEnabled(idle && sessionShots > 0);
        clearInventory.setEnabled(idle && sessionShots == 0);
        roiWidth.setEnabled(idle && sessionShots == 0); roiHeight.setEnabled(idle && sessionShots == 0);
        gameInput.setEnabled(idle && sessionShots == 0); condition.setEnabled(idle && sessionShots == 0);
        experiment.setEnabled(idle && sessionShots == 0); notes.setEnabled(idle && sessionShots == 0);
        report.setEnabled(idle);
    }
    private void onEdt(Runnable callback) { SwingUtilities.invokeLater(() -> { if (!closing) callback.run(); }); }
    private void shutdown() {
        closing = true; preview.stop(); camera.stop();
        inference.execute(() -> { if (classifier != null) try { classifier.close(); } catch (Exception e) { System.err.println(e); } });
        inference.shutdown(); dispose();
    }

    private static final class ImagePanel extends JPanel {
        private BufferedImage image;
        private final String placeholder;
        private Rectangle roi;
        private boolean showModelGuide;
        private boolean scannerOverlay;
        ImagePanel(String placeholder) { this.placeholder = placeholder; setBackground(new Color(25, 30, 40)); }
        void title(String text) {
            var border = BorderFactory.createTitledBorder(text); border.setTitleColor(Color.WHITE); setBorder(border);
        }
        void setImage(BufferedImage value) { image = value; repaint(); }
        void setRoi(Rectangle value) { roi = value == null ? null : new Rectangle(value); repaint(); }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (image == null) { g.setColor(Color.WHITE); g.drawString(placeholder, 16, 24); return; }
            Insets inset = getInsets();
            double scale = Math.min((double)(getWidth()-inset.left-inset.right) / image.getWidth(),
                    (double)(getHeight()-inset.top-inset.bottom) / image.getHeight());
            int width = (int)(image.getWidth() * scale), height = (int)(image.getHeight() * scale);
            int x = inset.left + (getWidth()-inset.left-inset.right-width)/2;
            int y = inset.top + (getHeight()-inset.top-inset.bottom-height)/2;
            g.drawImage(image, x, y, width, height, null);
            if (roi != null && scannerOverlay) {
                int rx = x + (int)(roi.x * scale), ry = y + (int)(roi.y * scale);
                int rw = (int)(roi.width * scale), rh = (int)(roi.height * scale);
                Graphics2D overlay = (Graphics2D)g.create();
                try {
                    overlay.setColor(new Color(0, 0, 0, 145));
                    overlay.fillRect(x, y, width, ry-y);
                    overlay.fillRect(x, ry+rh, width, y+height-ry-rh);
                    overlay.fillRect(x, ry, rx-x, rh);
                    overlay.fillRect(rx+rw, ry, x+width-rx-rw, rh);
                    overlay.setStroke(new BasicStroke(3f)); overlay.setColor(Color.YELLOW);
                    overlay.drawRect(rx, ry, rw, rh);
                    int corner = Math.max(5, Math.min(18, Math.min(rw,rh)/4));
                    overlay.setStroke(new BasicStroke(5f));
                    for (int dx : new int[]{0,1}) for (int dy : new int[]{0,1}) {
                        int cx=rx+dx*rw, cy=ry+dy*rh;
                        overlay.drawLine(cx,cy,cx+(dx==0?corner:-corner),cy);
                        overlay.drawLine(cx,cy,cx,cy+(dy==0?corner:-corner));
                    }
                } finally { overlay.dispose(); }
            }
            if (showModelGuide) {
                double crop = 224.0 / 256 * Math.min(image.getWidth(), image.getHeight()) * scale;
                g.setColor(new Color(70, 230, 180));
                g.drawRect((int)(x + (width - crop) / 2), (int)(y + (height - crop) / 2), (int)crop, (int)crop);
            }
            if (roi != null) {
                g.setColor(Color.YELLOW);
                g.drawRect(x + (int)(roi.x * scale), y + (int)(roi.y * scale), (int)(roi.width * scale), (int)(roi.height * scale));
            }
        }
    }
}
