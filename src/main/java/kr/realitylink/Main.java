package kr.realitylink;

import kr.realitylink.ai.Classifier;
import kr.realitylink.diagnostics.PrototypeWindow;
import kr.realitylink.mirror.MirrorDialog;
import kr.realitylink.mirror.MirrorScanner;
import kr.realitylink.ui.MainFrame;

import java.nio.file.Path;
import javax.imageio.ImageIO;
import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

public final class Main {
    private Main() { }

    private static void openMirrorTest(Path model) {
        JFrame frame = new JFrame("REALITY LINK | 거울 연결 테스트");
        MirrorScanner scanner = new MirrorScanner(model);
        JLabel status = new JLabel("아이템 획득 이벤트 대기", SwingConstants.CENTER);
        JButton openMirror = new JButton("거울 열기");
        openMirror.addActionListener(event -> new MirrorDialog(frame, scanner,
                item -> status.setText("아이템 획득 이벤트 수신 " + item.name())).setVisible(true));
        frame.add(openMirror, BorderLayout.CENTER);
        frame.add(status, BorderLayout.SOUTH);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) {
                scanner.close();
            }
        });
        frame.setSize(370, 130);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        try {
            Path model = Path.of("models", "mobilenetv2-12.onnx");
            Path image = null;
            String mode = "gui";
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--model" -> model = Path.of(args[++i]);
                    case "--image" -> { image = Path.of(args[++i]); mode = "image"; }
                    case "--inspect" -> mode = "inspect";
                    case "--native-check" -> mode = "native";
                    case "--diagnostics" -> mode = "diagnostics";
                    default -> throw new IllegalArgumentException("사용법: [--model 경로] [--diagnostics | --image 이미지 | --inspect | --native-check]");
                }
            }
            if (mode.equals("native")) {
                nu.pattern.OpenCV.loadLocally();
                System.out.println("OpenCV native loaded: " + org.opencv.core.Core.VERSION + "; " + System.getProperty("os.arch"));
            } else if (mode.equals("diagnostics")) {
                Path selectedModel = model;
                SwingUtilities.invokeLater(() -> new PrototypeWindow(selectedModel).setVisible(true));
            } else if (!mode.equals("gui")) {
                try (Classifier classifier = new Classifier(model)) {
                    System.out.print(classifier.metadata());
                    if (mode.equals("image")) System.out.print(classifier.classify(ImageIO.read(image.toFile())).format());
                }
            } else {
                SwingUtilities.invokeLater(() -> {
                    MainFrame frame = new MainFrame();
                    frame.setVisible(true);
                });
            }
        } catch (Exception | LinkageError e) {
            System.err.println("실행 실패: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
