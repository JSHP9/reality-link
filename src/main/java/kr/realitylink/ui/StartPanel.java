package kr.realitylink.ui;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class StartPanel extends JPanel {

	public StartPanel(Runnable onStart) {

		setLayout(new BorderLayout());

		ImageIcon image = new ImageIcon(getClass().getResource("/assets/room/start.png"));

		JLabel label = new JLabel() {
			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				g.drawImage(image.getImage(), 0, 0, getWidth(), getHeight(), this);
			}
		};

		label.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) {
					onStart.run();
				}
			}
		});

		add(label, BorderLayout.CENTER);
	}
}