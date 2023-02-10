package com.example.facescan;

import android.opengl.GLES20;

import com.google.mediapipe.formats.proto.DetectionProto;
import com.google.mediapipe.solutioncore.ResultGlRenderer;
import com.google.mediapipe.solutions.facedetection.FaceDetectionResult;
import com.google.mediapipe.solutions.facedetection.FaceKeypoint;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class FaceDetectionResultGlRenderer implements ResultGlRenderer<FaceDetectionResult> {
    private static final String TAG = "FaceDetectionResultGlRenderer";

    private static final float[] KEYPOINT_COLOR = new float[] { 1f, 0f, 0f, 1f };
    private static final float KEYPOINT_SIZE = 16f;
    private static final float[] BBOX_COLOR = new float[] { 0f, 1f, 0f, 1f };
    private static final int BBOX_THICKNESS = 8;
    private static final String VERTEX_SHADER =
            "uniform mat4 uProjectionMatrix;\n"
            + "uniform float uPointSize;\n"
            + "attribute vec4 vPosition;\n"
            + "void main() {\n"
            + "  gl_Position = uProjectionMatrix * vPosition;\n"
            + "  gl_PointSize = uPointSize;\n"
            + "}";
    private static final String FRAGMENT_SHADER =
            "precision mediump float;\n"
            + "uniform vec4 uColor;\n"
            + "void main() {\n"
            + "  gl_FragColor = uColor;\n"
            + "}";

    private int _program;
    private int _positionHandle;
    private int _pointSizeHandle;
    private int _projectionMatrixHandle;
    private int _colorHandle;

    private int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }

    @Override
    public void setupRendering() {
        _program = GLES20.glCreateProgram();
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        GLES20.glAttachShader(_program, vertexShader);
        GLES20.glAttachShader(_program, fragmentShader);
        GLES20.glLinkProgram(_program);
        _positionHandle = GLES20.glGetAttribLocation(_program, "vPosition");
        _pointSizeHandle = GLES20.glGetUniformLocation(_program, "uPointSize");
        _projectionMatrixHandle = GLES20.glGetUniformLocation(_program, "uProjectionMatrix");
        _colorHandle = GLES20.glGetUniformLocation(_program, "uColor");
    }

    @Override
    public void renderResult(FaceDetectionResult result, float[] projectionMatrix) {
        if (result == null) {
            return;
        }

        GLES20.glUseProgram(_program);
        GLES20.glUniformMatrix4fv(_projectionMatrixHandle, 1, false, projectionMatrix, 0);
        GLES20.glUniform1f(_pointSizeHandle, KEYPOINT_SIZE);
        int numDetectedFaces = result.multiFaceDetections().size();
        for (int i = 0; i < numDetectedFaces; i++) {
            drawDetection(result.multiFaceDetections().get(i));
        }
    }

    public void release() {
        GLES20.glDeleteProgram(_program);
    }

    private void drawDetection(DetectionProto.Detection detection) {
        if (detection.hasLocationData()) {
            return;
        }

        float[] points = new float[FaceKeypoint.NUM_KEY_POINTS];
        for (int i = 0; i < FaceKeypoint.NUM_KEY_POINTS; i++) {
            points[2 * i] = detection.getLocationData().getRelativeKeypoints(i).getX();
            points[2 * i + 1] = detection.getLocationData().getRelativeKeypoints(i).getY();
        }

        GLES20.glUniform4fv(_colorHandle, 1, KEYPOINT_COLOR, 0);
        FloatBuffer vertextBuffer = ByteBuffer.allocateDirect(points.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(points);
        vertextBuffer.position(0);
        GLES20.glEnableVertexAttribArray(_positionHandle);
        GLES20.glVertexAttribPointer(_positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertextBuffer);
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, FaceKeypoint.NUM_KEY_POINTS);
        if (!detection.getLocationData().hasRelativeBoundingBox()){
            return;
        }

        float left = detection.getLocationData().getRelativeBoundingBox().getXmin();
        float top = detection.getLocationData().getRelativeBoundingBox().getYmin();
        float right = left + detection.getLocationData().getRelativeBoundingBox().getWidth();
        float bottom = top + detection.getLocationData().getRelativeBoundingBox().getHeight();

        drawLine(top, left, top, right);
        drawLine(bottom, left, bottom, right);
        drawLine(top, left, bottom, left);
        drawLine(top, right, bottom, right);
    }

    private void drawLine(float y1, float x1, float y2, float x2) {
        GLES20.glUniform4fv(_colorHandle, 1, BBOX_COLOR, 0);
        GLES20.glLineWidth(BBOX_THICKNESS);
        float[] vertex = { x1, y1, x2, y2 };
        FloatBuffer vertexBuffer = ByteBuffer.allocateDirect(vertex.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(vertex);
        vertexBuffer.position(0);
        GLES20.glEnableVertexAttribArray(_positionHandle);
        GLES20.glVertexAttribPointer(_positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2);
    }
}
