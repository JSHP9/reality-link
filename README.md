# REALITY LINK 팀 공통 프로젝트

기존 0.4.0의 OpenCV 카메라, MobileNetV2 ONNX 추론, ROI 판정과 거울 획득 흐름을 새 Maven 프로젝트에 옮겼습니다. 팀원 담당 파일은 선언과 역할 주석만 들어 있습니다.

## 프로젝트 구성

| 경로 | 역할 | 담당 |
| --- | --- | --- |
| `src/main/java/kr/realitylink/Main.java` | 거울 열기 버튼, 아이템 이벤트 수신 확인, 진단·CLI 실행 분기 | 공동 |
| `ai/` | `CameraService`, `Classifier`, `Preprocessor`, `Allowlist`, `VerificationSession`, `DiagnosticCapture` | AI·거울 담당 |
| `mirror/` | 기존 `MirrorDialog`, `MirrorScanner`, `MirrorAcquisition` | AI·거울 담당 |
| `diagnostics/` | 기존 `PrototypeWindow`, `DiagnosticJournal`, `RecognitionJournal`, `ExperimentReport` | AI·거울 담당 |
| `common/` | `ItemType`, `ItemAcquiredListener` | 공동 계약 |
| `ui/` | `MainFrame`, `WallPanel`, `InventoryPanel`의 기본 선언 | 게임 담당 |
| `game/` | `GameState`와 비어 있는 `WallId` 열거형 | 게임 담당 |
| `save/` | 향후 저장 코드를 둘 패키지 | 게임 담당 |
| `src/main/resources/assets/` | `room/`, `items/`, `sound/` 리소스 자리 | 공동 |
| `src/test/` | 기존 자동 테스트 4개 클래스와 검증용 리소스 | 공동 |

## Eclipse에서 실행

1. ZIP을 풀고 Eclipse에서 **File → Import → Maven → Existing Maven Projects**를 선택합니다. `pom.xml`이 들어 있는 `reality-link` 폴더를 지정합니다.
2. **Finish**를 누릅니다. 기존 `reality-link`와 이름이 같다면 Eclipse의 별도 워크스페이스에 가져옵니다.
3. 64비트 JDK 17 이상을 사용합니다. 의존성이 보이지 않으면 프로젝트 우클릭 → **Maven → Update Project**를 실행합니다.
4. `kr.realitylink.Main`을 **Run As → Java Application**으로 실행합니다. 작업 디렉터리는 `pom.xml`, `config`, `models`가 있는 프로젝트 루트입니다.
5. **거울 열기**를 누르고 같은 물체를 ROI 안에서 세 번 촬영합니다. 승인되면 **인벤토리에 가져오기**를 눌러 테스트 창에서 `PEN` 또는 `CUP` 이벤트 수신 문구를 확인합니다.

Eclipse 실행 설정의 **Arguments → Program arguments**에 `--diagnostics`를 넣으면 기존 FULL/ROI 비교, Top-5, 입력 PNG, TSV와 실험 보고서 화면을 엽니다. 카메라 인덱스를 바꾸려면 **VM arguments**에 `-Drealitylink.cameraIndex=1`을 지정합니다.

프로젝트 루트에서 Maven으로 빌드한 뒤 터미널에서 실행할 수도 있습니다.

```powershell
mvn clean verify
java -jar target/reality-link-0.4.0.jar
java -jar target/reality-link-0.4.0.jar --diagnostics
java -jar target/reality-link-0.4.0.jar --inspect
```

JAR 실행에는 Maven이 빌드 시 생성하는 `target/lib/`가 필요합니다.

## 모델과 리소스 준비

기본 모델 경로는 프로젝트 루트 기준 `models/mobilenetv2-12.onnx`입니다. 배포 ZIP에는 검증한 모델이 들어 있습니다. Git으로 가져온 복사본에서는 프로젝트 루트에서 다음 스크립트를 실행해 동일 모델을 준비합니다. 스크립트가 다운로드한 파일의 SHA-256을 검사합니다.

```powershell
powershell -NoProfile -File .\prepare-model.ps1
```

기존 모델을 복사해 동일 경로에 넣거나, 프로그램 인수 `--model "다른경로/mobilenetv2-12.onnx"`로 파일 위치를 지정할 수 있습니다. 클래스 이름은 `src/main/resources/synset.txt`, PEN/CUP 매핑과 초기 최소 점수 15%는 `config/allowlist.properties`에서 읽습니다.

아이템 이미지는 `src/main/resources/assets/items/pen.png`와 `cup.png`에 두면 됩니다. 이전 방식의 프로젝트 루트 `assets/items/`도 사용할 수 있고, 두 위치에 파일이 있으면 루트 쪽이 우선입니다. 이미지가 없으면 기존 이름 배지가 표시됩니다.

## 게임 화면과 연결

팀원이 만든 게임 창에서 `MirrorScanner`를 하나 생성해 공유하고, 거울 상호작용마다 새 `MirrorDialog`를 엽니다. 다음 예제의 `inventoryListener`에 실제 게임 인벤토리 추가 메서드를 연결하세요.

```java
import kr.realitylink.common.ItemAcquiredListener;
import kr.realitylink.mirror.MirrorDialog;
import kr.realitylink.mirror.MirrorScanner;
import java.nio.file.Path;

// 게임 창 수명 동안 재사용합니다.
MirrorScanner scanner = new MirrorScanner(Path.of("models/mobilenetv2-12.onnx"));
ItemAcquiredListener inventoryListener = item -> {
    // 팀원이 작성하는 게임 시스템에 item을 전달합니다.
};
mirrorButton.addActionListener(event ->
    new MirrorDialog(gameWindow, scanner, inventoryListener).setVisible(true));
```

가져오기 버튼을 누를 때 `onItemAcquired(ItemType item)`이 Swing EDT에서 승인 아이템별로 호출됩니다. 게임 창이 끝날 때 EDT에서 `scanner.close()`를 호출하세요. 거울 창의 카메라 해제와 모델 작업 큐 처리는 기존 구현을 사용합니다.

## Git과 팀 리소스 공유

예정 원격 저장소는 `https://github.com/JSHP9/reality-link.git`입니다. 초기화와 원격 등록, 커밋, 푸시는 팀이 직접 진행합니다.

`.gitignore`는 Maven과 Eclipse 생성물, ONNX, 진단 이미지·로그, 이미지·오디오·영상·ZIP 확장자를 제외합니다. 정식으로 공유할 리소스만 추적하려면 팀에서 합의한 파일을 선택해 강제 추가하거나, 확장자 제외 패턴 아래에 경로별 예외를 둡니다. 예를 들어 `!src/main/resources/assets/items/pen.png`를 추가하면 그 파일을 일반적인 방식으로 추적할 수 있습니다. 별도 공유를 택하면 팀원이 동일한 상대 경로에 넣을 수 있도록 파일 목록과 경로를 기록합니다.

## 검증

- Java 17에서 Maven `clean verify` 성공, 기존 테스트 **35개 통과**. 실제 ONNX ROI 추론과 가져오기 이벤트 흐름을 포함합니다.
- 별도 Eclipse 워크스페이스에서 **Existing Maven Projects** 가져오기와 `Main.class`, `MirrorDialog.class` 생성 확인.
- 빌드한 JAR에서 `Main`과 **거울 열기** 버튼을 실행해 팝업 열기·닫기 확인. 검증용 미존재 카메라 인덱스로 연결 실패 안내를 확인했습니다.
- 실제 웹캠 영상, 펜·컵 실물 인식률, 실제 장치 재연결은 사용자 확인 항목입니다.
