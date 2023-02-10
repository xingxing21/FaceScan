package com.example.facescan;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.formats.proto.LocationDataProto;
import com.google.mediapipe.solutioncore.CameraInput;
import com.google.mediapipe.solutioncore.SolutionGlSurfaceView;
import com.google.mediapipe.solutioncore.VideoInput;
import com.google.mediapipe.solutions.facedetection.FaceDetection;
import com.google.mediapipe.solutions.facedetection.FaceDetectionOptions;
import com.google.mediapipe.solutions.facedetection.FaceDetectionResult;
import com.google.mediapipe.solutions.facedetection.FaceKeypoint;

import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private FaceDetection _faceDetection;
    private enum InputSource {
        UNKNOWN,
        IMAGE,
        VIDEO,
        CAMERA
    }

    private InputSource _inputSource = InputSource.UNKNOWN;

    private CameraInput _cameraInput;
    private SolutionGlSurfaceView<FaceDetectionResult> _glSurfaceView;

    private Button _btnTakeFace;

    private static final int CAMERA_REQUEST_CODE = 10;

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.CAMERA }, CAMERA_REQUEST_CODE);
    }

    private boolean hasStoragePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStoragePermission() {
        int REQUEST_CODE_PERMISSION_STORAGE = 100;
        String[] permissions = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        for (String str : permissions) {
            if (this.checkSelfPermission(str) != PackageManager.PERMISSION_GRANTED) {
                this.requestPermissions(permissions, REQUEST_CODE_PERMISSION_STORAGE);
                return;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        _btnTakeFace = findViewById(R.id.btnTakeFace);

        if (!hasCameraPermission()) {
            requestPermission();
        }

        if (!hasStoragePermission()) {
            requestStoragePermission();
        }

        setupLiveDemoUiComponents();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (_inputSource == InputSource.CAMERA) {
            _cameraInput = new CameraInput(this);
            _cameraInput.setNewFrameListener(textureFrame -> _faceDetection.send(textureFrame));
            _glSurfaceView.post(this::startCamera);
            _glSurfaceView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (_inputSource == InputSource.CAMERA) {
            _glSurfaceView.setVisibility(View.GONE);
            _cameraInput.close();
        }
    }

    private Bitmap downscaleBitmap(Bitmap originalBitmap, int width, int height) {
        double aspectRatio = (double) originalBitmap.getWidth() / originalBitmap.getHeight();
        if (((double) width / height) > aspectRatio) {
            width = (int) (height * aspectRatio);
        } else {
            height = (int) (width / aspectRatio);
        }

        return Bitmap.createScaledBitmap(originalBitmap, width, height, false);
    }

    private Bitmap rotateBitmap(Bitmap inputBitmap, InputStream imageData) throws IOException {
        int orientation = new ExifInterface(imageData)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        if (orientation == ExifInterface.ORIENTATION_NORMAL) {
            return inputBitmap;
        }

        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270);
                break;
            default:
                matrix.postRotate(0);
                break;
        }

        return Bitmap.createBitmap(inputBitmap, 0, 0, inputBitmap.getWidth(), inputBitmap.getHeight(), matrix, true);
    }

    private void setupLiveDemoUiComponents() {
        _btnTakeFace.setOnClickListener(v -> {
            if (_inputSource == InputSource.CAMERA) {
                return;
            }

            stopCurrentPipeline();
            setupStreamingModePipeline(InputSource.CAMERA);
        });
    }

    private void setupStreamingModePipeline(InputSource inputSource) {
        this._inputSource = inputSource;
        _faceDetection =
                new FaceDetection(
                        this,
                        FaceDetectionOptions.builder().setStaticImageMode(false).setModelSelection(0).build());

        _faceDetection.setErrorListener(
                (msg, e) -> Log.d(TAG, "MediaPipe Face Detection error:" + msg));

        if (inputSource == InputSource.CAMERA) {
            _cameraInput = new CameraInput(this);
            _cameraInput.setNewFrameListener(textureFrame -> _faceDetection.send(textureFrame));
        }

        _glSurfaceView = new SolutionGlSurfaceView<>(this, _faceDetection.getGlContext(), _faceDetection.getGlMajorVersion());
        _glSurfaceView.setSolutionResultRenderer(new FaceDetectionResultGlRenderer());
        _glSurfaceView.setRenderInputImage(true);
        _faceDetection.setResultListener(faceDetectionResult -> {
            logNoseTipKeypoint(faceDetectionResult, 0, false);
            _glSurfaceView.setRenderData(faceDetectionResult);
            _glSurfaceView.requestRender();
        });

        if (inputSource == InputSource.CAMERA) {
            _glSurfaceView.post(this::startCamera);
        }

        FrameLayout frameLayout = findViewById(R.id.preview_display_layout);
        frameLayout.removeAllViewsInLayout();
        frameLayout.addView(_glSurfaceView);
        _glSurfaceView.setVisibility(View.VISIBLE);
        frameLayout.requestLayout();
    }

    private void startCamera() {
        _cameraInput.start(this, _faceDetection.getGlContext(), CameraInput.CameraFacing.FRONT, _glSurfaceView.getWidth(), _glSurfaceView.getHeight());
    }

    private void stopCurrentPipeline() {
        if (_cameraInput != null) {
            _cameraInput.setNewFrameListener(null);
            _cameraInput.close();
        }

        if (_glSurfaceView != null) {
            _glSurfaceView.setVisibility(View.GONE);
        }

        if (_faceDetection != null) {
            _faceDetection.close();
        }
    }

    private void logNoseTipKeypoint(FaceDetectionResult result, int faceIndex, boolean showPixelValues) {
        if (result.multiFaceDetections().isEmpty()) {
            return;
        }

        LocationDataProto.LocationData.RelativeKeypoint noseTip = result.multiFaceDetections().get(faceIndex).getLocationData().getRelativeKeypoints(FaceKeypoint.NOSE_TIP);
        if (showPixelValues) {
            int width = result.inputBitmap().getWidth();
            int height = result.inputBitmap().getHeight();
            Log.i(TAG, String.format("MediaPipe Face Detection nose tip coordinates (pixel value): x=%f, y=%f",
                    noseTip.getX() * width, noseTip.getY() * height));
        } else {
            Log.i(TAG, String.format("MediaPipe Face Detection nose tip normalized coordinates (value range: [0, 1]): x=%f, y=%f",
                    noseTip.getX(), noseTip.getY()));
        }
    }
}