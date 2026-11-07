package kr.realitylink.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** 향후 팀원이 벽 화면을 구성할 자리. */
public class WallPanel extends JPanel {
	private CardLayout cardLayout;
	private JPanel[] walls;
	private int currentwall = 0;
	private final Runnable onMirrorClick;
	
	public WallPanel(Runnable onMirrorClick) {
		this.onMirrorClick = onMirrorClick;
		cardLayout = new CardLayout();
		walls = new JPanel[4];
		
		setLayout(cardLayout);
		
		for(int i = 0; i < walls.length; ++i) {
			final int wallIndex = i;
			walls[i] = new JPanel(new BorderLayout());
			add(walls[i], "wall" + i);
			String path = "/assets/room/wall" + (i + 1) + ".png";
			ImageIcon image = new ImageIcon(getClass().getResource(path));
			JLabel label = new JLabel() {
			    @Override
			    protected void paintComponent(Graphics g) {
			        super.paintComponent(g);

			        g.drawImage(
			            image.getImage(),
			            0, 0,
			            getWidth(), getHeight(),
			            this
			        );
			    } 
			};
			
			label.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					int x = e.getX();
					int y = e.getY();
					int width = label.getWidth();
					int height = label.getHeight();
					
					if (x < width * 0.05) {
						previousWall();
					}
					else if ( x > width * 0.95) {
						nextWall();
					}
					if (wallIndex == 0 && x >= width * 0.37 && x <= width * 0.56 && y >= height * 0.16 && y <= height * 0.66) {
						onMirrorClick.run();
					}
				}
			});
			
			walls[i].add(label, BorderLayout.CENTER);
		}
	}
	
	public void showWall(int index) {
		cardLayout.show(this, "wall" + index);
		currentwall = index;
	}
	
	public void nextWall() {
		showWall((currentwall + 1) % walls.length);
	}
	
	public void previousWall() {
		showWall((currentwall - 1 + walls.length) % walls.length);
	}
}
