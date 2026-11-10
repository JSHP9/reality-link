package kr.realitylink.mirror;

import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.Jsoner;
import kr.realitylink.common.ItemType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// 사진을 Python에 보내고, 탐지 결과를 게임 아이템으로 바꿈.
final class YoloClient {
    private static final int TIMEOUT_SECONDS = 30; // Python 탐지가 최대 30초가 넘지 않게 세팅.
    private final Path folder; // Python 플젝 위치 폴더 경로

    YoloClient() {
    	// getProperty로 앞에 속성있으면 앞에꺼 가져오고 없으면 뒤에 폴더 기본값으로 사용, toAbsolutePath은 상대경로를 절대경로로 변환.
        folder = Path.of(System.getProperty("realitylink.yolo.dir", "REALITY-LINK-YOLO")).toAbsolutePath();
    }

    // 사진을 Python에 보냄.
    Set<ItemType> detect(BufferedImage frame) throws IOException, InterruptedException {
        // PC마다 Python 위치가 다를 수 있어서 local.properties에서 읽는다.
        Properties config = new Properties();
        Path localConfig = folder.resolve("local.properties");
        
        if (Files.isRegularFile(localConfig)) {
        	// try-with-resources, 파일을 읽은 뒤 reader를 자동으로 닫아 줌.
            try (var reader = Files.newBufferedReader(localConfig)) {
                config.load(reader);
            }
        }
        
        Path python = folder.resolve(config.getProperty("python", ".venv/Scripts/python.exe"));
        Path model = folder.resolve(config.getProperty("model", "yolov8s.pt"));
        Path script = folder.resolve("detect_image.py");

        // 사진을 임시 PNG로 저장
        Path temporary = Files.createTempDirectory("reality-link-yolo-");
        Path input = temporary.resolve("frame.png");
        Path output = temporary.resolve("result.json");
        
        try {
            // frame은 메모리에 있는 사진이라 PNG로 저장한 뒤 Python에 파일 경로를 넘김.
            // 파일을 쓰다가 문제가 생기면 ImageIO.write에서 예외가 난다.
            ImageIO.write(frame, "png", input.toFile());

            // ProcessBuilder가 파이썬이랑 자바랑 통합하는 방법이다.
            // 터미널에서 python -X utf8 detect_image.py frame.png yolov8s.pt와 같음.
            ProcessBuilder command = new ProcessBuilder(python.toString(), "-X", "utf8",
                    script.toString(), input.toString(), model.toString());
            command.directory(folder.toFile());
            
            // 결과 JSON은 파일에 받고, Python 오류는 콘솔로 보냄.
            command.redirectOutput(output.toFile());
            command.redirectError(ProcessBuilder.Redirect.INHERIT);
            
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            Process process = command.start();
            
            // 스캔 중 게임을 종료해도 Python과 임시 파일을 정리.
            Thread shutdown = new Thread(() -> {
                stop(process);
                cleanup(temporary, input, output);
            }, "mirror-yolo-shutdown");
            
            try { 
            	// 게임 종료 시 shutdown 스레드가 실행되도록 JVM에 등록.
                Runtime.getRuntime().addShutdownHook(shutdown);
                
                if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IOException("YOLO 탐지 시간이 초과됐습니다. 다시 시도해 주세요.");
                }
                
                if (process.exitValue() != 0) {
                    throw new IOException("YOLO 실행 오류 (" + process.exitValue() + "). 콘솔을 확인해 주세요.");
                }
                
                return parseItems(Files.readString(output));
            } finally {
                stop(process);
                
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdown);
                } catch (IllegalStateException ignored) {
                    // 게임 종료 중이면 이미 등록된 정리 코드가 실행된다.
                }
            }
        } finally {
        	// 사용한 임시 파일과 폴더를 삭제.
            cleanup(temporary, input, output);
        }
    }

    // Python이 반환한 json 문자열을 게임 아이템의 집합(Set<ItemType>)으로 변환.
    static Set<ItemType> parseItems(String json) throws IOException {
        Object result; // json 값 
        
        try {
            result = Jsoner.deserialize(json); // json값을 java객체로 변환.
        } catch (JsonException invalid) {
            throw new IOException("YOLO의 JSON 응답을 읽을 수 없습니다.", invalid);
        }
        // Python이 보낸 json에서 detections 목록을 꺼냄. ?는 와일드 카드.
        if (!(result instanceof Map<?, ?> root) || !(root.get("detections") instanceof List<?> detections)) {
            throw new IOException("YOLO 응답에 detections 배열이 없습니다.");
        }
        // 같은 클래스의 박스가 여러 개 있어도 게임 아이템은 한 번만 담는다.
        Set<ItemType> items = EnumSet.noneOf(ItemType.class); // noneOf는 빈 EnumSet만드는 메서드.
        for (Object entry : detections) {
            if (!(entry instanceof Map<?, ?> detection)
                    || !(detection.get("classId") instanceof Number id)
                    || !(detection.get("name") instanceof String name)) {
                throw new IOException("YOLO 탐지 항목의 형식이 올바르지 않습니다.");
            }
            // 아이템 목록
            if (id.doubleValue() == 41 && name.equals("cup")) items.add(ItemType.CUP);
            if (id.doubleValue() == 73 && name.equals("book")) items.add(ItemType.BOOK);
        }
        return items;
    }

    private static void stop(Process process) {
        if (!process.isAlive()) return;
        // Windows 가상환경 실행기가 실제 Python을 자식 프로세스로 띄울 수 있다.
        List<ProcessHandle> processes = new ArrayList<>(process.descendants().toList());
        processes.add(process.toHandle());
        processes.forEach(ProcessHandle::destroyForcibly);
        // Windows에서 파일을 지우기 전에 Python이 닫힐 때까지 잠깐 기다린다.
        CompletableFuture<?>[] exits = processes.stream().map(ProcessHandle::onExit).toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(exits).get(2, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException failure) {
            System.err.println("YOLO 프로세스 종료 확인 실패 " + failure.getMessage());
        }
    }

    private static void cleanup(Path directory, Path... files) { // ...은 인자 호출시 여러개 전달 가능한 가변 인자
        try {
            for (Path file : files) Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        } catch (IOException failure) {
            System.err.println("YOLO 임시 파일 정리 실패 " + failure.getMessage());
        }
    }
}
