package kr.realitylink.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;

import javax.swing.JPanel;
import javax.swing.ImageIcon;
import javax.swing.JLabel;

/** 향후 팀원이 인벤토리 화면을 구성할 자리. */
public class InventoryPanel extends JPanel {
	private ImageIcon inventoryImage;
	public InventoryPanel() {
		setPreferredSize(new Dimension(120, 0));
		setLayout(new BorderLayout());
		inventoryImage = new ImageIcon(
			    getClass().getResource("/assets/room/inventory.png")
			);
		JLabel label = new JLabel(inventoryImage) {
		    @Override
		    protected void paintComponent(Graphics g) {
		        super.paintComponent(g);

		        g.drawImage(
		        	inventoryImage.getImage(),
		            0, 0,
		            getWidth(), getHeight(),
		            this
		        );
		    } 
		};
		add(label, BorderLayout.CENTER);
	}
}
