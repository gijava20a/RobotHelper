package com.example.myapplication.ai;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.example.myapplication.service.MqttManager;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AIHelper {

    private static final String TAG = "AIHelper";

    private final Context context;
    private final PreviewView previewView;
    private final MqttManager mqtt;
    private final String robotTopic;

    private ProcessCameraProvider cameraProvider;
    private final ExecutorService cameraExecutor;
    private ObjectDetector detector;

    private int frameCount = 0;
    private String lastCommand = "";

    public AIHelper(Context context, PreviewView previewView, MqttManager mqtt, String robotTopic) {
        this.context = context;
        this.previewView = previewView;
        this.mqtt = mqtt;
        this.robotTopic = robotTopic;

        cameraExecutor = Executors.newSingleThreadExecutor();

        ObjectDetectorOptions options = new ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableMultipleObjects()
                .enableClassification()
                .build();

        detector = ObjectDetection.getClient(options);
        Log.d(TAG, "AIHelper initialized");
    }

    public void startDetection() {
        Log.d(TAG, "Starting AI detection pipeline...");
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCamera();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Failed to get camera provider: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private void bindCamera() {
        if (cameraProvider == null) return;

        cameraProvider.unbindAll();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        analysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

        CameraSelector selector = CameraSelector.DEFAULT_BACK_CAMERA;

        cameraProvider.bindToLifecycle(
                (androidx.lifecycle.LifecycleOwner) context,
                selector,
                preview,
                analysis
        );

        Log.d(TAG, "Camera bound and analyzer started");
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    @SuppressLint("UnsafeOptInUsageError")
    private void analyzeFrame(ImageProxy image) {
        try {
            frameCount++;
            if (frameCount % 30 == 0) {
                Log.d(TAG, "📸 Frame #" + frameCount + " (" + image.getWidth() + "x" + image.getHeight() + ")");
            }

            if (image.getImage() == null) {
                image.close();
                return;
            }

            InputImage inputImage = InputImage.fromMediaImage(
                    image.getImage(),
                    image.getImageInfo().getRotationDegrees()
            );

            detector.process(inputImage)
                    .addOnSuccessListener(objects -> {
                        processObjects(objects);
                        image.close();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Detection failed: " + e.getMessage());
                        image.close();
                    });

        } catch (Exception e) {
            Log.e(TAG, "Error analyzing frame: " + e.getMessage());
            image.close();
        }
    }

    private void processObjects(List<DetectedObject> objects) {
        if (objects.isEmpty()) {
            if (!lastCommand.equals("STOP")) {
                sendCommand("STOP");
            }
            return;
        }

        DetectedObject largestObject = null;
        int largestArea = 0;

        for (DetectedObject obj : objects) {
            int area = obj.getBoundingBox().width() * obj.getBoundingBox().height();
            if (area > largestArea) {
                largestArea = area;
                largestObject = obj;
            }
        }

        if (largestObject == null || previewView.getWidth() == 0) return;

        int centerX = largestObject.getBoundingBox().centerX();
        int frameCenter = previewView.getWidth() / 2;
        int tolerance = 100;

        String cmd;
        if (centerX < frameCenter - tolerance) cmd = "LEFT";
        else if (centerX > frameCenter + tolerance) cmd = "RIGHT";
        else cmd = "FORWARD";

        if (!cmd.equals(lastCommand)) {
            sendCommand(cmd);
            lastCommand = cmd;
        }
    }

    private void sendCommand(String cmd) {
        Log.d(TAG, "Command: " + cmd);
        mqtt.publish(robotTopic, cmd);
    }

    public void stopDetection() {
        try {
            if (cameraProvider != null) {
                cameraProvider.unbindAll();
                cameraProvider = null;
            }

            if (detector != null) {
                detector.close();
                detector = null;
            }

            if (cameraExecutor != null && !cameraExecutor.isShutdown()) {
                cameraExecutor.shutdown();
            }

            Log.d("AIHelper", "AI detection stopped cleanly");
        } catch (Exception e) {
            Log.e("AIHelper", "Error while stopping detection: " + e.getMessage());
        }
    }

}
