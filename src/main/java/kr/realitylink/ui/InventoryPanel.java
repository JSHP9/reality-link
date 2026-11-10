package kr.realitylink.ui;

import kr.realitylink.item.GameItem;
import kr.realitylink.item.GameItemAcquiredListener;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import java.util.ArrayList;

import javax.swing.JPanel;

import javax.swing.ImageIcon;
import javax.swing.JLabel;

public class InventoryPanel extends JPanel implements GameItemAcquiredListener {
	private ImageIcon inventoryImage;
	private JLabel label;
	private ArrayList<GameItem> inventory = new ArrayList<>();
	private int selectedSlotIndex = -1;
	
	private static final int SLOT_X = 25;
	private static final int SLOT_START_Y = 25;
	private static final int SLOT_SIZE = 50;
	
	public InventoryPanel() {
		setPreferredSize(new Dimension(100, 0));
		setLayout(new BorderLayout());
		inventoryImage = new ImageIcon(
			    getClass().getResource("/assets/room/inventory.png")
			);
		
		//인벤토리
		label = new JLabel() {
		    @Override
		    protected void paintComponent(Graphics g) {
		        super.paintComponent(g);
		        
		      //인벤토리 그리기
		        g.drawImage(
		        	inventoryImage.getImage(),
		            0, 0,
		            getWidth(), getHeight(),
		            this
		        );
		        
		        //인벤토리에 획득한 아이템 배치
		        for (int i = 0; i < inventory.size(); ++i) {
		        	GameItem item = inventory.get(i);
		        	if (item != null) {
		        		ImageIcon itemImage = item.image();
			        	g.drawImage(
			        			itemImage.getImage(),
			        			SLOT_X, SLOT_START_Y + i * SLOT_SIZE,
			        			50, 50,
			        			this
			        	);
			        	if (i == selectedSlotIndex) {
			        		g.drawRect(SLOT_X, SLOT_START_Y + i * SLOT_SIZE, SLOT_SIZE, SLOT_SIZE);
			        	}
		        	}
		        }
		    }
		};
		
		add(label, BorderLayout.CENTER);
		
		//인벤토리 상호작용 배치
		label.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				int x = e.getX();
				int y = e.getY();
				
				for (int i = 0; i < inventory.size(); ++i) {
					int slot = SLOT_START_Y + i * SLOT_SIZE;
					if (x >= SLOT_X && x <= SLOT_X + SLOT_SIZE &&
						y >= slot && y < slot + SLOT_SIZE &&
						inventory.get(i) != null
						) {
						selectedSlotIndex = i;
						System.out.println("선택됨.");
						label.repaint();
					}
				}
			}
		});
	}
	
	//선택한 아이템 불러오기
	public GameItem getSelectedItem() {
		if (selectedSlotIndex < 0 || selectedSlotIndex >= inventory.size()) {
			return null;
		}
		return inventory.get(selectedSlotIndex);
	}
	
	//아이템 얻었을 때 인벤토리에 추가
	@Override
	public void onGameItemAcquired(GameItem item) {
		if (item == null) {
		    return;
		}
		
		if (!inventory.contains(item)) {
			for (int i = 0; i < inventory.size(); ++i) {
				if (inventory.get(i) == null) {
					inventory.set(i, item);
					System.out.println(item + " 얻음.");
					label.repaint();
					return;
				}
			}
			inventory.add(item);
			System.out.println(item + " 얻음.");
			label.repaint();
		}
	}
}
