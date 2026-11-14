
import contextlib
import json
import sys
from pathlib import Path
import cv2
import numpy as np
from ultralytics import YOLO, settings


PROJECT_DIR = Path(__file__).resolve().parent
MODEL_PATH = PROJECT_DIR / "yolov8s.pt"

TARGET_CLASSES = [41, 67, 73, 76]  # 컵, 휴대폰, 책, 가위
CONFIDENCE = 0.25


def detect(image_path, model_path):

    # 이미지 파일 읽기
    frame = cv2.imdecode(
        np.frombuffer(image_path.read_bytes(), dtype=np.uint8),
        cv2.IMREAD_COLOR
    )

    # YOLO로 물체 최대 1개 탐지
    model = YOLO(str(model_path))
    results = model.predict(
        source=frame,
        conf=CONFIDENCE,
        classes=TARGET_CLASSES,
        max_det=1,
        device="cpu",
        verbose=False
    )

    # 인식한 물체가 없으면 빈 리스트 반환
    if len(results[0].boxes) == 0:
        return []

    # 인식한 물체의 클래스 번호와 이름 반환
    box = results[0].boxes[0]
    class_id = int(box.cls.item())

    return [{"classId": class_id, "name": model.names[class_id]}]


if __name__ == "__main__":

    # Java가 전달한 이미지 경로와 모델 경로
    image_path = Path(sys.argv[1])
    model_path = Path(sys.argv[2]) if len(sys.argv) == 3 else MODEL_PATH

    # YOLO 실행 로그가 JSON 출력에 섞이지 않도록 처리
    with contextlib.redirect_stdout(sys.stderr): # Python 라이브러리가 출력하는 일반 메시지는 stderr로 보냄
        settings.update({"sync": False})
        detections = detect(image_path, model_path)

    # 탐지 결과를 JSON으로 Java에 전달
    print(json.dumps({"detections": detections}))
