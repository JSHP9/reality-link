package kr.realitylink.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import kr.realitylink.common.ItemAcquiredListener;
import kr.realitylink.common.ItemType;

public class InventoryPanel extends JPanel implements ItemAcquiredListener {
	private ImageIcon inventoryImage, popupImage;
	private JLabel label;
	private JPopupMenu popup;
	private boolean mouseOnPopup;
	private ArrayList<ItemType> inventory = new ArrayList<>();
	private int selectedSlotIndex = -1;
	private int hoveredSlotIndex = -1;
	private Timer closeTimer;

	private static final int SLOT_X = 25;
	private static final int SLOT_START_Y = 25;
	private static final int SLOT_SIZE = 50;

	public InventoryPanel() {
		setPreferredSize(new Dimension(100, 0));
		setLayout(new BorderLayout());
		inventoryImage = new ImageIcon(getClass().getResource("/assets/room/inventory.png"));

		// 인벤토리 테스트용 아이템 지급
		inventory.add(ItemType.BOOK);
		inventory.add(ItemType.KEY);
		inventory.add(ItemType.PLANT);
		inventory.add(ItemType.CUP);
		inventory.add(ItemType.WATER_CUP);
		inventory.add(ItemType.SCISSORS);

		// 인벤토리
		label = new JLabel() {
			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);

				// 인벤토리 그리기
				g.drawImage(inventoryImage.getImage(), 0, 0, getWidth(), getHeight(), this);

				// 인벤토리에 획득한 아이템 배치
				for (int i = 0; i < inventory.size(); ++i) {
					ItemType item = inventory.get(i);
					if (item != null) {
						ImageIcon itemImage = item.image();
						g.drawImage(itemImage.getImage(), SLOT_X, (int) (SLOT_START_Y + i * 1.85 * SLOT_SIZE), 50, 50,
								this);
						if (i == selectedSlotIndex) {
							g.drawRect(SLOT_X, (int) (SLOT_START_Y + i * 1.85 * SLOT_SIZE), SLOT_SIZE, SLOT_SIZE);
						}
					}
				}
			}
		};

		add(label, BorderLayout.CENTER);

		// 타이머
		closeTimer = new Timer(150, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (!mouseOnPopup) {
					hoveredSlotIndex = -1;

					if (popup != null) {
						popup.setVisible(false);
					}
				}
			}
		});
		closeTimer.setRepeats(false);

		MouseAdapter MouseHandler = new MouseAdapter() {
			// 인벤토리 클릭 상호작용 배치
			@Override
			public void mouseClicked(MouseEvent e) {
				int x = e.getX();
				int y = e.getY();

				for (int i = 0; i < inventory.size(); ++i) {
					int slot = (int) (SLOT_START_Y + i * 1.85 * SLOT_SIZE);
					if (x >= SLOT_X && x <= SLOT_X + SLOT_SIZE && y >= slot && y < slot + SLOT_SIZE
							&& inventory.get(i) != null) {

						selectedSlotIndex = i;
						System.out.println(inventory.get(i) + " 선택됨.");
						label.repaint();
						return;
					}
				}
			}

			// 인벤토리에 마우스 호버 상호작용 배치
			@Override
			public void mouseMoved(MouseEvent e) {
				int x = e.getX();
				int y = e.getY();

				for (int i = 0; i < inventory.size(); ++i) {
					int slot = (int) (SLOT_START_Y + i * 1.85 * SLOT_SIZE);
					if (x >= SLOT_X && x <= SLOT_X + SLOT_SIZE && y >= slot && y < slot + SLOT_SIZE
							&& inventory.get(i) != null && inventory.get(i).hasPopup()) {
						closeTimer.stop();
						mouseOnPopup = false;
						if (hoveredSlotIndex != i || popup == null || !popup.isVisible()) {

							hoveredSlotIndex = i;

							if (popup != null) {
								popup.setVisible(false);
							}

							popup = createItemPopup(inventory.get(i));

							int popupHeight = popup.getPreferredSize().height;
							int labelHeight = label.getHeight();

							int popupY = Math.max(0, Math.min(slot - 40, labelHeight - popupHeight));

							popup.show(label, SLOT_SIZE - 430, popupY);
						}
						return;
					}
				}

				if (popup != null && popup.isVisible() && !closeTimer.isRunning()) {
					closeTimer.restart();
				}
			}

			// 마우스가 아이템 밖으로 나갔을 때 초기화
			@Override
			public void mouseExited(MouseEvent e) {
				if (popup != null && popup.isVisible() && !closeTimer.isRunning()) {
					closeTimer.restart();
				}
			}
		};

		label.addMouseListener(MouseHandler);
		label.addMouseMotionListener(MouseHandler);
	}

	private JPopupMenu createItemPopup(ItemType item) {

		popup = new JPopupMenu();
		popupImage = new ImageIcon(getClass().getResource("/assets/items/popup.png"));

		JLabel popupLabel = new JLabel(popupImage) {
			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				g.setColor(java.awt.Color.WHITE);
				g.setFont(new java.awt.Font("맑은 고딕", java.awt.Font.BOLD, (int) (getWidth() * 0.095)));
				g.drawString(item.displayName(), (int) (getWidth() * 0.265), (int) (getHeight() * 0.227));

				g.setFont(new java.awt.Font("맑은 고딕", java.awt.Font.PLAIN, 18));
				g.setColor(java.awt.Color.WHITE);
				if (item == ItemType.BOOK) {
					g.drawString("무언가 적어넣을 수 있을 것 같다.", (int) (getWidth() * 0.106), (int) (getHeight() * 0.39));
				} else if (item == ItemType.KEY) {
					g.drawString("뇌 과부하 이슈로 텍스트 고민 좀", (int) (getWidth() * 0.106), (int) (getHeight() * 0.39));
				}

			}
		};
		popupLabel.setPreferredSize(new Dimension(378, 308));
		popup.add(popupLabel);

		MouseAdapter MouseHandler = new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				mouseOnPopup = true;
				closeTimer.stop();
			}

			@Override
			public void mouseExited(MouseEvent e) {
				mouseOnPopup = false;
				closeTimer.restart();
			}

			public void mouseClicked(MouseEvent e) {
				double x = (double) e.getX() / popupLabel.getWidth();
				double y = (double) e.getY() / popupLabel.getHeight();

				if (SwingUtilities.isLeftMouseButton(e) && x >= 0.302 && x <= 0.608 && y >= 0.773 && y <= 0.932) {

					System.out.println(item.displayName() + " 사용");
				}
			}
		};

		popup.addMouseListener(MouseHandler);

		return popup;
	}

	// 선택한 아이템 불러오기
	public ItemType getSelectedItem() {
		if (selectedSlotIndex < 0 || selectedSlotIndex >= inventory.size()) {
			return null;
		}
		return inventory.get(selectedSlotIndex);
	}

	// 아이템 얻었을 때 인벤토리에 추가
	@Override
	public void onItemAcquired(ItemType item) {
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
