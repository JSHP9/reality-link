import contextlib
import json
import os
from pathlib import Path
import sys

PROJECT_DIR = Path(__file__).resolve().parent
MODEL_PATH = PROJECT_DIR / "yolov8s.pt"
TARGET_CLASSES = [41, 73]  # cup, book
CONFIDENCE = 0.25


def detect(image_path, model_path):
    # 라이브러리 로그까지 stderr로 보내 stdout에는 JSON만 남긴다.
    with contextlib.redirect_stdout(sys.stderr):
        import cv2
        import numpy as np
        from ultralytics import YOLO, settings

        settings.update({"sync": False})
        if not model_path.is_file():
            raise FileNotFoundError(f"모델 파일 없음: {model_path}")
        # Windows에서도 한글이 들어간 이미지 경로를 읽을 수 있게 한다.
        frame = cv2.imdecode(np.frombuffer(image_path.read_bytes(), dtype=np.uint8), cv2.IMREAD_COLOR)
        if frame is None:
            raise ValueError("이미지 파일을 읽을 수 없습니다.")

        model = YOLO(str(model_path))
        results = model.predict(source=frame, imgsz=640, conf=CONFIDENCE,
                                device="cpu", classes=TARGET_CLASSES, verbose=False)
        return [
            {"classId": int(box.cls.item()), "name": model.names[int(box.cls.item())],
             "confidence": box.conf.item(), "box": box.xyxy[0].tolist()}
            for box in results[0].boxes
        ]


if __name__ == "__main__":
    if len(sys.argv) not in (2, 3):
        sys.exit("사용법: python detect_image.py <이미지> [모델 경로]")
    runtime_dir = PROJECT_DIR / ".runtime"
    try:
        runtime_dir.mkdir(exist_ok=True)
        os.environ["YOLO_CONFIG_DIR"] = str(runtime_dir)
        model_path = Path(sys.argv[2]) if len(sys.argv) == 3 else MODEL_PATH
        detections = detect(Path(sys.argv[1]), model_path)
        print(json.dumps({"detections": detections}, allow_nan=False))
    except Exception as error:
        sys.exit(f"YOLO 탐지 오류: {error}")
