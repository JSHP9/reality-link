package kr.realitylink.ui;

import java.awt.event.MouseEvent;

public class InteractionConfig {
	// 벽 이동 버튼
	public static final double NEXT_WALL_X_MAX = 0.95;

	public static final double PRVIOUS_WALL_X_MAX = 0.05;

	// 환풍구 나가기 버튼
	public static final double VENT_EXIT_Y_MIN = 0.88;

	// 거울
	public static final double MIRROR_X_MIN = 0.37;
	public static final double MIRROR_X_MAX = 0.56;

	public static final double MIRROR_Y_MIN = 0.16;
	public static final double MIRROR_Y_MAX = 0.66;

	// 사다리(획득 시)
	public static final double LADDER_PICKUP_X_MIN = 0.66;
	public static final double LADDER_PICKUP_X_MAX = 0.87;

	public static final double LADDER_PICKUP_Y_MIN = 0.39;
	public static final double LADDER_PICKUP_Y_MAX = 1.00;

	// 사다리(설치 시)
	public static final double LADDER_PLACED_X_MIN = 0.64;
	public static final double LADDER_PLACED_X_MAX = 0.85;

	public static final double LADDER_PLACED_Y_MIN = 0.39;
	public static final double LADDER_PLACED_Y_MAX = 1.00;

	// 환풍구
	public static final double VENT_X_MIN = 0.66;
	public static final double VENT_X_MAX = 0.87;

	public static final double VENT_Y_MIN = 0.08;
	public static final double VENT_Y_MAX = 0.42;

	// 위쪽 케이블 타이
	public static final double TIE_UP_X_MIN = 0.64;
	public static final double TIE_UP_X_MAX = 0.88;

	public static final double TIE_UP_Y_MIN = 0.11;
	public static final double TIE_UP_Y_MAX = 0.18;

	// 아래쪽 케이블 타이
	public static final double TIE_LOW_X_MIN = 0.64;
	public static final double TIE_LOW_X_MAX = 0.88;

	public static final double TIE_LOW_Y_MIN = 0.29;
	public static final double TIE_LOW_Y_MAX = 0.36;

	// 환풍구 - 식물
	public static final double PLANT_X_MIN = 0.10;
	public static final double PLANT_X_MAX = 0.51;

	public static final double PLANT_Y_MIN = 0.02;
	public static final double PLANT_Y_MAX = 0.56;

	// 화분
	public static final double POT_X_MIN = 0.125;
	public static final double POT_X_MAX = 0.255;
	
	public static final double POT_Y_MIN = 0.49;
	public static final double POT_Y_MAX = 0.65;

	// 화분 - 식물
	public static final double PLANTED_X_MIN = 0.14;
	public static final double PLANTED_X_MAX = 0.25;
	
	public static final double PLANTED_Y_MIN = 0.22;
	public static final double PLANTED_Y_MAX = 0.53;
	
	//세면대
	public static final double SINK_X_MIN = 0.59;
	public static final double SINK_X_MAX = 0.83;
	
	public static final double SINK_Y_MIN = 0.66;
	public static final double SINK_Y_MAX = 0.99;
	
	// 식물이 뱉은 스위치 손잡이
	public static final double SWITCH_X_MIN = 0.32;
	public static final double SWITCH_X_MAX = 0.40;
	
	public static final double SWITCH_Y_MIN = 0.82;
	public static final double SWITCH_Y_MAX = 0.92;
	
	// 스위치
	public static final double LIGHT_SWITCH_X_MIN = 0.435;
	public static final double LIGHT_SWITCH_X_MAX = 0.512;
	
	public static final double LIGHT_SWITCH_Y_MIN = 0.429;
	public static final double LIGHT_SWITCH_Y_MAX = 0.721;
	
	// 범위를 문자열로 반환하는 메서드
	public static double[] getBounds(String object) {
		try {
			return new double[] {
					InteractionConfig.class.getField(object + "_X_MIN").getDouble(null),
					InteractionConfig.class.getField(object + "_X_MAX").getDouble(null),
					InteractionConfig.class.getField(object + "_Y_MIN").getDouble(null),
					InteractionConfig.class.getField(object + "_Y_MAX").getDouble(null)
			};
		} catch (ReflectiveOperationException e) {
			// 찾을 수 없을 경우 에러 메세지
			throw new IllegalArgumentException("좌표를 찾을 수 없음: " + object, e);
		}
	}
	
	// 마우스가 범위내에 있는지 감지하는 메서드
	public static boolean isInside(MouseEvent e, double[] bounds) {
		int x = e.getX();
	    int y = e.getY();
	    int width = e.getComponent().getWidth();
	    int height = e.getComponent().getHeight();
	    
		return x >= width * bounds[0]
	            && x <= width * bounds[1]
	            && y >= height * bounds[2]
	            && y <= height * bounds[3];
	}
}
