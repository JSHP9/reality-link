# 외부 파일과 출처

- MobileNetV2 FP32 ONNX: [onnxmodelzoo/mobilenetv2-12](https://huggingface.co/onnxmodelzoo/mobilenetv2-12), revision `23a2a25eaaa6fcd61865414c8a1acf13dda66b7a`. 모델 카드의 라이선스 Apache-2.0. `models/mobilenetv2-12.onnx` 원본 그대로 포함. 해시·다운로드 URL은 README와 스크립트에 고정.
- ImageNet synset: [ONNX Model Zoo synset.txt](https://github.com/onnx/models/blob/c5cae0f1942c8d7727372c7b1f6b485aa39e4421/validated/vision/classification/synset.txt), commit `c5cae0f1942c8d7727372c7b1f6b485aa39e4421`. `src/main/resources/synset.txt` 원본 그대로 포함. ONNX models 저장소 Apache-2.0.
- 모델 전처리 규격: 같은 ONNX models commit의 `validated/vision/classification/imagenet_preprocess.py`, `preprocess` 함수.
- Bilinear 수치 기준: [Pillow 11.3.0 Resample.c](https://github.com/python-pillow/Pillow/blob/11.3.0/src/libImaging/Resample.c). Java 구현은 분리 가능한 triangle 필터·축소 범위·22비트 계수·축별 반올림을 적용. Pillow 라이브러리는 Java 앱 실행 의존성에 포함하지 않음. 참조 fixture 생성에만 사용. Pillow 라이선스는 `licenses/Pillow-LICENSE.txt` 참조.
- `src/test/resources/pillow-reference.f32`: 직접 정의한 결정적 RGB 패턴을 Pillow 11.3.0으로 전처리해 생성한 little-endian float32 기준 텐서. 생성 코드는 `tools/generate-reference.py`. Pillow 외 Python 패키지 불필요; 일반 Java 빌드·실행에 Python 불필요.
- 검증 사진: [PyTorch Hub dog.jpg](https://github.com/pytorch/hub/blob/master/images/dog.jpg). 임시 검증 입력으로만 사용하고 배포 ZIP에 사진을 포함하지 않음.
- ONNX Runtime 및 OpenCV Java 의존성은 Maven 좌표로 고정. 각각의 배포 JAR에 포함된 LICENSE/NOTICE를 유지. 프로젝트 `target/lib`에는 Maven 원본 JAR을 그대로 복사.

이 문서는 외부 구성요소의 출처를 기록하며 사용자 프로젝트 전체에 새로운 라이선스를 부여하지 않습니다.
