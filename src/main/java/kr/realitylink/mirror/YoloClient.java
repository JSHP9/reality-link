
package kr.realitylink.mirror;

import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.Jsoner;
import kr.realitylink.common.ItemType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Map;
import java.util.Properties;

// 사진을 Python YOLO로 분석하고 게임 아이템으로 변환
final class YoloClient {

    // Python 프로젝트 폴더
    private final File folder = new File(
        System.getProperty("realitylink.yolo.dir", "REALITY-LINK-YOLO")
    ).getAbsoluteFile();

    ItemType detect(BufferedImage frame)
            throws IOException, InterruptedException {

        // Python과 모델 경로 설정
        Properties config = new Properties();
        File configFile = new File(folder, "local.properties");

        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                config.load(reader);
            }
        }

        String pythonPath = config.getProperty("python", ".venv/Scripts/python.exe");
        File python = new File(pythonPath);

        // 상대경로일 때만 프로젝트 폴더를 붙임
        if (!python.isAbsolute())
            python = new File(folder, pythonPath);

        String modelPath = config.getProperty("model", "yolov8s.pt");
        File model = new File(modelPath);

        if (!model.isAbsolute())
            model = new File(folder, modelPath);

        File script = new File(folder, "detect_image.py");

        // 사진과 결과 파일
        File input = new File(folder, "frame.png");
        File output = new File(folder, "result.json");

        Process process = null;

        try {
            // 카메라 사진을 PNG로 저장
            try (FileOutputStream out = new FileOutputStream(input)) {
                ImageIO.write(frame, "png", out);
            }

            // Java에서 Python 실행
            ProcessBuilder command = new ProcessBuilder(
                python.getPath(), "-X", "utf8",
                script.getPath(), input.getPath(), model.getPath()
            );

            command.directory(folder);
            command.redirectOutput(output);
            command.redirectError(ProcessBuilder.Redirect.INHERIT);

            process = command.start();

            // Python 종료까지 대기
            int exitCode = process.waitFor();

            if (exitCode != 0)
                return null;

            // JSON 파일 읽기
            try (BufferedReader reader = new BufferedReader(
                    new FileReader(output))) {

                String json = reader.readLine();

                return parseItem(json);
            }

        } finally {
            // Python 및 파일 정리
            if (process != null && process.isAlive())
                process.destroyForcibly();


            input.delete();
            output.delete();
        }
    }

    // YOLO 결과를 게임 아이템으로 변환
    static ItemType parseItem(String json) {

        Map<?, ?> result;

        try {
            // JSON을 Map으로 변환
            result = (Map<?, ?>) Jsoner.deserialize(json);
        } catch (JsonException e) {
            return null;
        }

        Number id = (Number) result.get("classId");

        if (id == null)
            return null;

        int classId = id.intValue();

        if (classId == 41) return ItemType.CUP;
        if (classId == 67) return ItemType.PHONE;
        if (classId == 73) return ItemType.BOOK;
        if (classId == 76) return ItemType.SCISSORS;

        return null;
    }
}
