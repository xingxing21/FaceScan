package com.example.facescan;

import static java.lang.Math.min;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;

import com.google.mediapipe.formats.proto.DetectionProto;
import com.google.mediapipe.solutions.facedetection.FaceDetectionResult;
import com.google.mediapipe.solutions.facedetection.FaceKeypoint;

public class FaceDetectionResultImageView extends AppCompatImageView {
    private static final String TAG = "FaceDetectionResultImageView";

    private static final int KEYPOINT_COLOR = Color.RED;
    private static final int KEYPOINT_RADIUS = 8; // Pixels
    private static final int BBOX_COLOR = Color.GREEN;
    private static final int BBOX_THICKNESS = 5; // Pixels
    private Bitmap _latest;

    public FaceDetectionResultImageView(Context context) {
        super(context);
        setScaleType(AppCompatImageView.ScaleType.FIT_CENTER);
    }

    public void setFaceDetectionResult(FaceDetectionResult result) {
        if (result == null) {
            return;
        }

        Bitmap bmInput = result.inputBitmap();
        int width = bmInput.getWidth();
        int height = bmInput.getHeight();

        _latest = Bitmap.createBitmap(width, height, bmInput.getConfig());
        Canvas canvas = new Canvas(_latest);

        canvas.drawBitmap(bmInput, new Matrix(), null);
        int numDetectedFaces = result.multiFaceDetections().size();
        for (int i = 0; i < numDetectedFaces; i++) {
            drawDetectionCanvas(result.multiFaceDetections().get(i), canvas, width, height);
        }
    }

    public void update() {
        postInvalidate();
        if (_latest != null) {
            setImageBitmap(_latest);
        }
    }

    private void drawDetectionCanvas(DetectionProto.Detection detection, Canvas canvas, int width, int height) {
        if (!detection.hasLocationData()) {
            return;
        }

        Paint keypointPaint = new Paint();
        keypointPaint.setColor(KEYPOINT_COLOR);
        for (int i = 0; i < FaceKeypoint.NUM_KEY_POINTS; i++) {
            int xPixel = min((int) (detection.getLocationData().getRelativeKeypoints(i).getX() * width), width - 1);
            int yPixel = min((int) (detection.getLocationData().getRelativeKeypoints(i).getY() * height), height - 1);
            canvas.drawCircle(xPixel, yPixel, KEYPOINT_RADIUS, keypointPaint);
        }

        if (!detection.getLocationData().hasRelativeBoundingBox()) {
            return;
        }

        Paint bboxPaint = new Paint();
        bboxPaint.setColor(BBOX_COLOR);
        bboxPaint.setStyle(Paint.Style.STROKE);
        bboxPaint.setStrokeWidth(BBOX_THICKNESS);

        float left = detection.getLocationData().getRelativeBoundingBox().getXmin() * width;
        float top = detection.getLocationData().getRelativeBoundingBox().getYmin() * height;
        float right = left + detection.getLocationData().getRelativeBoundingBox().getWidth() * width;
        float bottom = top + detection.getLocationData().getRelativeBoundingBox().getHeight() * height;
        canvas.drawRect(left, top, right, bottom, bboxPaint);
    }
}
