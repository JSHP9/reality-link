package kr.realitylink.ui;

import kr.realitylink.mirror.MirrorDialog;
import kr.realitylink.mirror.MirrorScanner;

import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;

import javax.swing.JFrame;

public class MainFrame extends JFrame {

    private MirrorScanner scanner;

    public MainFrame() {
        setTitle("REALITY LINK");
        setSize(1200, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setLocationRelativeTo(null);
        
        // 인벤토리
        InventoryPanel inventoryPanel = new InventoryPanel();
        add(inventoryPanel, BorderLayout.EAST);

        // 거울 클릭 이벤트 연결
        WallPanel wallPanel = new WallPanel(this::openMirror, inventoryPanel);
        add(wallPanel, BorderLayout.CENTER);
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
            scanner = new MirrorScanner(
                Path.of("models", "mobilenetv2-12.onnx")
            );
        }

        // 기존 AI 거울 창 실행
        new MirrorDialog(
            this,
            scanner,
            item -> {
                System.out.println("아이템 획득: " + item);
            }
        ).setVisible(true);
    }
}