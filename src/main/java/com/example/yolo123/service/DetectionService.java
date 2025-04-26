package com.example.yolo123.service;

import com.example.yolo123.model.Detection;
import org.bytedeco.opencv.opencv_core.Size;
import org.springframework.stereotype.Service;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.global.opencv_imgcodecs;

import java.util.List;

@Service
public class DetectionService {
    private final Inference inference;

    public DetectionService() {
        String modelPath = "C:\\Users\\Su180\\IdeaProjects\\yolocv\\yolo123\\src\\main\\resources\\yolo-model\\yolov8n.onnx";
        System.out.println("Attempting to load model from: " + modelPath);
        this.inference = new Inference(modelPath, new Size(640, 640), false);
    }

    public List<Detection> detect(String imagePath) {
        Mat image = opencv_imgcodecs.imread(imagePath);
        return inference.runInference(image);
    }
}
