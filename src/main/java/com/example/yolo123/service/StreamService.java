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

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class StreamService {
    private final ConcurrentHashMap<Integer, PipedOutputStream> outputStreams = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, FFmpegFrameGrabber> grabbers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, FFmpegFrameRecorder> recorders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Thread> processingThreads = new ConcurrentHashMap<>();
    private final DetectionService detectionService;
    private final SMS sms;
    private final OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat();

    public StreamService(DetectionService detectionService, SMS sms) {
        this.detectionService = detectionService;
        this.sms = sms;
    }

    public void startHlsStream(int channel, String hlsPath) throws Exception {
        if (outputStreams.containsKey(channel)) {
            throw new IllegalStateException("通道 " + channel + " 已处于流处理状态");
        }

        // Create piped streams for ES data
        PipedInputStream pipedInput = new PipedInputStream();
        PipedOutputStream pipedOutput = new PipedOutputStream(pipedInput);
        outputStreams.put(channel, pipedOutput);

        // Create grabber for H.265 input
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(pipedInput);
        grabber.setFormat("hevc");
        grabbers.put(channel, grabber);

        // Start SMS real-time play
        CompletableFuture<String> future = new CompletableFuture<>();
        int userId = 0; // Assuming default userId, adjust as needed
        sms.RealPlay(userId, channel, future);
        String result = future.get();
        if (!"true".equals(result)) {
            throw new RuntimeException("SMS 流启动失败");
        }

        // Start processing thread
        Thread thread = new Thread(() -> processStream(channel, hlsPath));
        thread.start();
        processingThreads.put(channel, thread);
    }

    private void processStream(int channel, String hlsPath) {
        FFmpegFrameGrabber grabber = grabbers.get(channel);
        try {
            grabber.start();

            // Configure recorder for HLS output
            FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(hlsPath, grabber.getImageWidth(), grabber.getImageHeight());
            recorder.setFormat("hls");
            recorder.setOption("hls_time", "2");
            recorder.setOption("hls_list_size", "3");
            recorder.setOption("hls_flags", "delete_segments+append_list");
            recorder.setOption("hls_segment_type", "mpegts");
            recorder.setVideoCodec(org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264);
            recorder.setFrameRate(grabber.getFrameRate());
            recorder.setOption("preset", "veryfast");
            recorder.setOption("tune", "zerolatency");
            recorder.setOption("g", "50");
            recorder.setOption("keyint_min", "25");
            recorder.start();
            recorders.put(channel, recorder);

            Frame frame;
            while ((frame = grabber.grab()) != null && processingThreads.containsKey(channel)) {
                if (frame.image != null) {
                    Mat mat = converter.convert(frame);
                    if (mat != null) {
                        List<Detection> detections = detectionService.runInference(mat);
                        detectionService.drawDetections(mat, detections);
                        Frame processedFrame = converter.convert(mat);
                        recorder.record(processedFrame);
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

    public void processStreamData(Integer userId, byte[] esData) {
        if (userId == null || esData == null || esData.length == 0) {
            log.warn("收到无效的ES数据，用户ID: {}", userId);
            return;
        }

        PipedOutputStream outputStream = outputStreams.get(userId);
        if (outputStream != null) {
            try {
                outputStream.write(esData);
                outputStream.flush();
            } catch (Exception e) {
                log.error("写入 ES 数据失败，用户ID: {}", userId, e);
            }
        }
    }

    public void stopStream(int channel) {
        processingThreads.remove(channel);

        PipedOutputStream outputStream = outputStreams.remove(channel);
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (Exception e) {
                log.error("关闭输出流失败，通道: {}", channel, e);
            }
        }

        FFmpegFrameGrabber grabber = grabbers.remove(channel);
        if (grabber != null) {
            try {
                grabber.stop();
                grabber.release();
            } catch (Exception e) {
                log.error("停止抓取器失败，通道: {}", channel, e);
            }
        }

        FFmpegFrameRecorder recorder = recorders.remove(channel);
        if (recorder != null) {
            try {
                recorder.stop();
                recorder.release();
            } catch (Exception e) {
                log.error("停止录制器失败，通道: {}", channel, e);
            }
        }

        int userId = 0; // Adjust as needed
        Integer sessionId = SMS.LuserIDandSessionMap.get(userId);
        if (sessionId != null) {
            sms.StopRealPlay(userId, sessionId, SMS.SessionIDAndPreviewHandleMap.get(sessionId));
        }
    }
}