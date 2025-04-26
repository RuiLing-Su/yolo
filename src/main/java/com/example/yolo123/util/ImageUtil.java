//package com.example.yolo123.util;
//
//import com.example.yolo123.model.Detection;
//import org.bytedeco.javacv.Frame;
//import org.bytedeco.javacv.Java2DFrameConverter;
//import org.bytedeco.javacv.OpenCVFrameConverter;
//import org.bytedeco.opencv.global.opencv_imgproc;
//import org.bytedeco.opencv.opencv_core.Mat;
//import org.bytedeco.opencv.opencv_core.Point;
//import org.bytedeco.opencv.opencv_core.Scalar;
//import org.springframework.stereotype.Component;
//
//import javax.imageio.ImageIO;
//import java.awt.image.BufferedImage;
//import java.io.ByteArrayInputStream;
//import java.io.ByteArrayOutputStream;
//import java.io.IOException;
//import java.io.InputStream;
//import java.util.List;
//
//@Component
//public class ImageUtil {
//
//    private final OpenCVFrameConverter.ToMat matConverter = new OpenCVFrameConverter.ToMat();
//    private final Java2DFrameConverter java2dConverter = new Java2DFrameConverter();
//
//    public Mat bufferedImageToMat(BufferedImage image) {
//        Frame frame = java2dConverter.convert(image);
//        return matConverter.convert(frame);
//    }
//
//    public BufferedImage matToBufferedImage(Mat mat) {
//        Frame frame = matConverter.convert(mat);
//        return java2dConverter.convert(frame);
//    }
//
//    public BufferedImage drawDetections(BufferedImage image, List<Detection> detections) {
//        Mat mat = bufferedImageToMat(image);
//
//        // Draw bounding boxes and labels
//        for (Detection detection : detections) {
//            int x = detection.getBox().x();
//            int y = detection.getBox().y();
//            int w = detection.getBox().width();
//            int h = detection.getBox().height();
//
//            // Draw rectangle
//            opencv_imgproc.rectangle(
//                    mat,
//                    new Point(x, y),
//                    new Point(x + w, y + h),
//                    detection.getColor(),
//                    2,
//                    opencv_imgproc.LINE_8,
//                    0
//            );
//
//            // Create label text
//            String label = String.format("%s: %.2f", detection.getClassName(), detection.getConfidence());
//
//            // Get label size
//            int[] baseLine = new int[1];
//            org.bytedeco.opencv.opencv_core.Size labelSize = opencv_imgproc.getTextSize(
//                    label,
//                    opencv_imgproc.FONT_HERSHEY_SIMPLEX,
//                    0.5,
//                    1,
//                    baseLine
//            );
//
//            // Draw filled rectangle for text background
//            int top = Math.max(y, labelSize.height());
//            opencv_imgproc.rectangle(
//                    mat,
//                    new Point(x, top - labelSize.height()),
//                    new Point(x + labelSize.width(), top + baseLine[0]),
//                    detection.getColor(),
//                    opencv_imgproc.FILLED,
//                    opencv_imgproc.LINE_8,
//                    0
//            );
//
//            // Draw text
//            Scalar textColor = new Scalar(255, 255, 255, 0);
//            opencv_imgproc.putText(
//                    mat,
//                    label,
//                    new Point(x, top),
//                    opencv_imgproc.FONT_HERSHEY_SIMPLEX,
//                    0.5,
//                    textColor,
//                    1,
//                    opencv_imgproc.LINE_8,
//                    false
//            );
//        }
//
//        return matToBufferedImage(mat);
//    }
//
//    public byte[] bufferedImageToBytes(BufferedImage image, String format) throws IOException {
//        ByteArrayOutputStream baos = new ByteArrayOutputStream();
//        ImageIO.write(image, format, baos);
//        return baos.toByteArray();
//    }
//
//    public BufferedImage bytesToBufferedImage(byte[] imageBytes) throws IOException {
//        try (InputStream is = new ByteArrayInputStream(imageBytes)) {
//            return ImageIO.read(is);
//        }
//    }
//}