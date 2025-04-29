package com.example.yolo123.service;

import com.example.yolo123.SdkService.StreamService.SMS;
import com.example.yolo123.model.Detection;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class StreamService {
    private final ConcurrentHashMap<Integer, FFmpegFrameGrabber> grabbers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, FFmpegFrameRecorder> recorders = new ConcurrentHashMap<>();
    private final DetectionService detectionService;
    private final SMS sms;

    // Converter for Frame <-> Mat conversion
    private final OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat();

    public StreamService(DetectionService detectionService, SMS sms) {
        this.detectionService = detectionService;
        this.sms = sms;
    }

    public void startHlsStream(int channel, String hlsPath) throws Exception {
        if (grabbers.containsKey(channel)) {
            throw new IllegalStateException("通道 " + channel + " 已处于流处理状态");
        }

        CompletableFuture<String> future = new CompletableFuture<>();
        int userId = 0;
        sms.RealPlay(userId, channel, future);

        String result = future.get();
        if (!"true".equals(result)) {
            throw new RuntimeException("SMS 流启动失败");
        }

        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber("rtsp://example.com/stream"); // 替换为实际RTSP地址
        grabber.start();
        grabbers.put(channel, grabber);

        FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(hlsPath, grabber.getImageWidth(), grabber.getImageHeight());
        recorder.setFormat("hls");
        recorder.setOption("hls_time", "2");
        recorder.setOption("hls_list_size", "3");
        recorder.setOption("hls_flags", "delete_segments+append_list");
        recorder.setVideoCodec(org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264);
        recorder.setFrameRate(grabber.getFrameRate());
        recorder.start();
        recorders.put(channel, recorder);

        new Thread(() -> processStream(channel)).start();
    }

    private void processStream(int channel) {
        FFmpegFrameGrabber grabber = grabbers.get(channel);
        FFmpegFrameRecorder recorder = recorders.get(channel);
        try {
            Frame frame;
            while ((frame = grabber.grab()) != null && grabbers.containsKey(channel)) {
                if (frame.image != null) {
                    // Convert Frame to Mat
                    Mat mat = converter.convert(frame);

                    if (mat != null) {
                        // Run object detection
                        List<Detection> detections = detectionService.runInference(mat);

                        // Draw detection results on the frame
                        detectionService.drawDetections(mat, detections);

                        // Convert processed Mat back to Frame
                        Frame processedFrame = converter.convert(mat);

                        // Record the processed frame
                        recorder.record(processedFrame);

                        // Release the Mat resource
                        mat.close();
                    }
                }
            }
        } catch (Exception e) {
            log.error("处理视频流失败，通道: {}，错误: {}", channel, e.getMessage(), e);
        } finally {
            stopStream(channel);
        }
    }

    public void stopStream(int channel) {
        FFmpegFrameGrabber grabber = grabbers.remove(channel);
        if (grabber != null) {
            try {
                grabber.stop();
                grabber.release();
            } catch (Exception e) {
                log.error("停止抓取器失败，通道: {}，错误: {}", channel, e.getMessage());
            }
        }

        FFmpegFrameRecorder recorder = recorders.remove(channel);
        if (recorder != null) {
            try {
                recorder.stop();
                recorder.release();
            } catch (Exception e) {
                log.error("停止录制器失败，通道: {}，错误: {}", channel, e.getMessage());
            }
        }

        int userId = 0; // 假设默认用户ID为0
        Integer sessionId = SMS.LuserIDandSessionMap.get(userId);
        if (sessionId != null) {
            sms.StopRealPlay(userId, sessionId, SMS.SessionIDAndPreviewHandleMap.get(sessionId));
        }
    }

    public void processStreamData(Integer userId, byte[] esData) {
        if (userId == null || esData == null || esData.length == 0) {
            log.warn("收到无效的ES数据，用户ID: {}", userId);
            return;
        }

        log.debug("收到ES数据，用户ID: {}, 大小: {} 字节", userId, esData.length);

        try {
            // 使用FFmpegFrameGrabber处理H.264/H.265编码的ES数据
            try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(new ByteArrayInputStream(esData))) {
                // 配置grabber使用h264解码器
                grabber.setFormat("h264");
                // 设置保持原始格式属性
                grabber.setOption("vsync", "0");
                grabber.start();

                Frame frame;
                while ((frame = grabber.grab()) != null) {
                    if (frame.image != null) {
                        // 将Frame转换为Mat进行处理
                        Mat mat = converter.convert(frame);

                        if (mat != null) {
                            // 运行对象检测
                            List<Detection> detections = detectionService.runInference(mat);

                            // 在图像上绘制检测结果
                            detectionService.drawDetections(mat, detections);

                            // 将处理后的Mat转回Frame
                            Frame processedFrame = converter.convert(mat);

                            // 将处理后的帧发送到对应的recorder
                            FFmpegFrameRecorder recorder = recorders.get(userId);
                            if (recorder != null) {
                                recorder.record(processedFrame);
                            }

                            // 释放资源
                            mat.close();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("处理ES数据失败，用户ID: {}, 错误: {}", userId, e.getMessage(), e);
        }
    }
}