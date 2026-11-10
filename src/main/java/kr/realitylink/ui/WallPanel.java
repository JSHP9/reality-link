package kr.realitylink.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;

import kr.realitylink.item.GameItemAcquiredListener;
import kr.realitylink.item.GameItem;

public class WallPanel extends JPanel {
	private CardLayout cardLayout;
	private JPanel[] walls;
	private int currentwall = 0;
	private ArrayList<GameItem> collectedItems = new ArrayList<>();
	private final Runnable onMirrorClick;
	private final GameItemAcquiredListener onItemAcquired;
	
	private JPanel createWall(int index) {
		final int wallIndex = index;
		JPanel wall = new JPanel(new BorderLayout());
		String path = "/assets/room/wall" + (index + 1) + ".png";
		ImageIcon image = new ImageIcon(getClass().getResource(path));
		ImageIcon cup = GameItem.CUP.image();
		
		//벽 이미지 그리기 & 아이템 그리기
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
		        
		        if (wallIndex == 1 && !collectedItems.contains(GameItem.CUP)) {
		        	g.drawImage(
				            cup.getImage(),
				            (int)(getWidth() * InteractionConfig.CUP_X_MIN),
				            (int)(getHeight() * InteractionConfig.CUP_Y_MIN),
				            (int)(getWidth() * (InteractionConfig.CUP_X_MAX - InteractionConfig.CUP_X_MIN)),
				            (int)(getHeight() * (InteractionConfig.CUP_Y_MAX - InteractionConfig.CUP_Y_MIN)),
				            this
				    );
		        }
		    }
		};
		
		// 아이템 & 맵 상호작용 배치
		label.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				int x = e.getX();
				int y = e.getY();
				int width = label.getWidth();
				int height = label.getHeight();
				
				if (x < width * InteractionConfig.PRVIOUS_WALL_X_MAX) {
					previousWall();
				}
				else if ( x > width * InteractionConfig.NEXT_WALL_X_MAX) {
					nextWall();
				}
				
				// 거울 상호작용 배치
				if (wallIndex == 0 && x >= width * InteractionConfig.MIRROR_X_MIN &&
						x <= width * InteractionConfig.MIRROR_X_MAX &&
						y >= height * InteractionConfig.MIRROR_Y_MIN &&
						y <= height * InteractionConfig.MIRROR_Y_MAX) {
					onMirrorClick.run();
				}
				
				// 컵 상호작용 배치(아이템, 인벤토리 테스트용, 추후 삭제 예정)
				if (wallIndex == 1 && x >= width * InteractionConfig.CUP_X_MIN &&
						x <= width * InteractionConfig.CUP_X_MAX &&
						y >= height * InteractionConfig.CUP_Y_MIN &&
						y <= height * InteractionConfig.CUP_Y_MAX &&
						!collectedItems.contains(GameItem.CUP)) {
					onItemAcquired.onGameItemAcquired(GameItem.CUP);
					collectedItems.add(GameItem.CUP);
					label.repaint();
				}
			}
		});
		wall.add(label, BorderLayout.CENTER);
		return wall;
	}
	
	public WallPanel(Runnable onMirrorClick, GameItemAcquiredListener onItemAcquired) {
		this.onMirrorClick = onMirrorClick;
		this.onItemAcquired = onItemAcquired;
		cardLayout = new CardLayout();
		walls = new JPanel[4];
		
		setLayout(cardLayout);
		
		//벽 이미지 cardLayout에 등록
		for(int i = 0; i < walls.length; ++i) {
			walls[i] = createWall(i);
			add(walls[i], "wall" + i);
		}
	}
	
	//벽 보이기
	public void showWall(int index) {
		cardLayout.show(this, "wall" + index);
		currentwall = index;
	}
	
	//다음 벽으로 넘기기
	public void nextWall() {
		showWall((currentwall + 1) % walls.length);
	}
	
	//이전 벽으로 넘기기
	public void previousWall() {
		showWall((currentwall - 1 + walls.length) % walls.length);
	}
}
