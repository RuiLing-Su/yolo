package com.example.yolo123.controller;

import com.example.yolo123.model.Detection;
import com.example.yolo123.service.DetectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/detection")
@Tag(name = "预测")
public class DetectionController {
    private final DetectionService detectionService;

    private final Path uploadDir = Paths.get("C:\\Users\\Su180\\IdeaProjects\\yolocv\\yolo123\\uploads");
    private final Path resultDir = Paths.get("C:\\Users\\Su180\\IdeaProjects\\yolocv\\yolo123\\results");

    public DetectionController(DetectionService detectionService) {
        this.detectionService = detectionService;
        try {
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }
            if (!Files.exists(resultDir)) {
                Files.createDirectories(resultDir);
            }
        } catch (IOException e) {
            throw new RuntimeException("无法创建上传和结果目录", e);
        }
    }

    @PostMapping(value = "/detect", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "处理图像上传和对象检测", description = "传入图片")
    public ResponseEntity<?> detectObjects(@RequestParam("file") MultipartFile file) throws IOException {

        // 保存上传的文件
        String filename = file.getOriginalFilename();
        Path filePath = uploadDir.resolve(filename);
        file.transferTo(filePath.toFile());

        // 加载图像
        Mat image = opencv_imgcodecs.imread(filePath.toString());
        if (image.empty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "无法读取图像文件"));
        }

        List<Detection> detections = detectionService.detect(String.valueOf(filePath));

        // 在图像上绘制检测结果
        for (Detection detection : detections) {
            // 绘制边界框
            opencv_imgproc.rectangle(
                    image,
                    detection.getBox(),
                    detection.getColor(),
                    2,
                    opencv_imgproc.LINE_8,
                    0);

            // 准备标签文本
            String label = detection.getClassName() + ": " + String.format("%.2f", detection.getConfidence());

            // 获取文本大小
            int[] baseLine = new int[1];
            org.bytedeco.opencv.opencv_core.Size labelSize = opencv_imgproc.getTextSize(
                    label,
                    opencv_imgproc.FONT_HERSHEY_SIMPLEX,
                    0.6,
                    1,
                    baseLine);

            // 绘制标签背景
            // 计算标签位置，确保不超出图像边界
            int x = Math.max(detection.getBox().x(), 0);
            int y = Math.max(detection.getBox().y() - labelSize.height(), 0);

            // 如果标签太靠近图像顶部，则将其放在框的底部
            if (y < 5) {
                y = detection.getBox().y() + detection.getBox().height();
            }

            // 确保标签宽度不超出图像
            int width = Math.min(labelSize.width(), image.cols() - x);

            // 绘制标签背景
            opencv_imgproc.rectangle(
                    image,
                    new Point(x, y),
                    new Point(x + width, y + labelSize.height() + baseLine[0]),
                    detection.getColor()
            );

            // 绘制标签文本
            opencv_imgproc.putText(
                    image,
                    label,
                    new Point(x, y + labelSize.height()),
                    opencv_imgproc.FONT_HERSHEY_SIMPLEX,
                    0.6,
                    new Scalar(255, 255, 255, 0),
                    1,
                    opencv_imgproc.LINE_AA,
                    false);
        }

        // 保存结果图像
        String resultFilename = "result_" + filename;
        Path resultPath = resultDir.resolve(resultFilename);
        opencv_imgcodecs.imwrite(resultPath.toString(), image);

        // 准备响应
        Map<String, Object> response = new HashMap<>();
        response.put("detections", detections.size());
        response.put("objects", detections);
        response.put("resultFile", resultFilename);

        return ResponseEntity.ok(response);

    }


    /**
     * 获取结果图像文件
     * @param filename 结果图像文件名
     * @return 图像文件
     */
    @RequestMapping("/result")
    public ResponseEntity<byte[]> getResultImage(@RequestParam("filename") String filename) throws IOException {
        Path resultPath = resultDir.resolve(filename);
        File resultFile = resultPath.toFile();

        if (!resultFile.exists()) {
            return ResponseEntity.notFound().build();
        }

        byte[] imageBytes = Files.readAllBytes(resultPath);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(imageBytes);
    }
}