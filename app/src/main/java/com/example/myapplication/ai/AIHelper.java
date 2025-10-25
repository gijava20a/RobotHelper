package com.example.myapplication.ai;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

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

    private final LifecycleOwner lifecycleOwner;
    private final MqttManager mqtt;
    private final String robotTopic;

    private ProcessCameraProvider cameraProvider;
    private final ExecutorService cameraExecutor;
    private ObjectDetector detector;

    private int frameCount = 0;
    private String lastCommand = "";
    private boolean isDetecting = false;
    private int noPersonFrameCount = 0;
    private static final int NO_PERSON_THRESHOLD = 10;

    public AIHelper(LifecycleOwner lifecycleOwner, MqttManager mqtt, String robotTopic) {
        this.lifecycleOwner = lifecycleOwner;
        this.mqtt = mqtt;
        this.robotTopic = robotTopic;

        cameraExecutor = Executors.newSingleThreadExecutor();

        ObjectDetectorOptions options = new ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableMultipleObjects()
                .enableClassification()
                .build();

        detector = ObjectDetection.getClient(options);
        Log.d(TAG, "AIHelper initialized - Will use FRONT camera for person tracking");
    }

    public void startDetection() {
        if (isDetecting) {
            Log.w(TAG, "Detection already running");
            return;
        }

        Log.i(TAG, "Starting AI PERSON TRACKING with FRONT camera...");
        isDetecting = true;
        noPersonFrameCount = 0;

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance((Context) lifecycleOwner);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraForAI();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Failed to get camera provider: " + e.getMessage());
                isDetecting = false;
            }
        }, ContextCompat.getMainExecutor((Context) lifecycleOwner));
    }

    private void bindCameraForAI() {
        if (cameraProvider == null) {
            Log.e(TAG, "Camera provider is null");
            return;
        }

        cameraProvider.unbindAll();
        Log.d(TAG, "📹 Preparing camera for AI mode...");

        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        analysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

        CameraSelector selector = CameraSelector.DEFAULT_FRONT_CAMERA;

        try {
            cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    analysis
            );

            Log.i(TAG, "AI mode active - FRONT camera analyzing for person tracking!");
        } catch (Exception e) {
            Log.e(TAG, "Failed to bind camera for AI: " + e.getMessage());
            isDetecting = false;
        }
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    @SuppressLint("UnsafeOptInUsageError")
    private void analyzeFrame(ImageProxy image) {
        try {
            frameCount++;
            if (frameCount % 30 == 0) {
                Log.d(TAG, "📸 AI analyzing frame #" + frameCount);
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
                        processObjects(objects, image.getWidth(), image.getHeight());
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

    private void processObjects(List<DetectedObject> objects, int imageWidth, int imageHeight) {
        DetectedObject largestPerson = null;
        int largestPersonArea = 0;

        for (DetectedObject obj : objects) {
            if (isPerson(obj, imageWidth, imageHeight)) {
                int area = obj.getBoundingBox().width() * obj.getBoundingBox().height();

                if (area > largestPersonArea) {
                    largestPersonArea = area;
                    largestPerson = obj;
                }
            }
        }

        if (largestPerson == null) {
            noPersonFrameCount++;

            if (frameCount % 30 == 0) {
                Log.d(TAG, "No person (count: " + noPersonFrameCount + "/" + NO_PERSON_THRESHOLD + ")");
            }

            if (noPersonFrameCount >= NO_PERSON_THRESHOLD && !lastCommand.equals("STOP")) {
                Log.w(TAG, "No person detected - STOPPING");
                sendCommand("STOP");
            }
            return;
        }

        // Person found
        noPersonFrameCount = 0;

        int centerX = largestPerson.getBoundingBox().centerX();
        int frameCenter = imageWidth / 2;
        int tolerance = imageWidth / 6;

        double areaRatio = (double) largestPersonArea / (imageWidth * imageHeight);

        // NOTE: Front camera is MIRRORED, so directions are reversed
        String cmd;
        if (centerX < frameCenter - tolerance) {
            cmd = "RIGHT";
        } else if (centerX > frameCenter + tolerance) {
            cmd = "LEFT";
        } else {
            cmd = "FORWARD";
        }

        if (frameCount % 15 == 0 || !cmd.equals(lastCommand)) {
            Log.i(TAG, "👤 TRACKING | Pos: " + centerX + "/" + imageWidth +
                    " | Size: " + String.format("%.1f%%", areaRatio * 100) + " | CMD: " + cmd);
        }

        if (!cmd.equals(lastCommand)) {
            sendCommand(cmd);
            lastCommand = cmd;
        }
    }

    private boolean isPerson(DetectedObject obj, int imageWidth, int imageHeight) {
        if (obj.getLabels() != null && !obj.getLabels().isEmpty()) {
            for (DetectedObject.Label label : obj.getLabels()) {
                String labelText = label.getText().toLowerCase();

                if (labelText.contains("person") ||
                        labelText.contains("human") ||
                        labelText.contains("people") ||
                        labelText.contains("face") ||
                        labelText.contains("man") ||
                        labelText.contains("woman") ||
                        labelText.contains("child")) {

                    if (frameCount % 30 == 0) {
                        Log.d(TAG, "Person: " + label.getText() +
                                " (" + String.format("%.2f", label.getConfidence()) + ")");
                    }
                    return true;
                }
            }
        }

        int width = obj.getBoundingBox().width();
        int height = obj.getBoundingBox().height();
        float aspectRatio = (float) height / width;

        if (aspectRatio >= 1.3 && aspectRatio <= 3.5) {
            int area = width * height;
            int minArea = (imageWidth * imageHeight) / 100;

            if (area > minArea) {
                if (frameCount % 60 == 0) {
                    Log.d(TAG, "Person-like shape: " + String.format("%.2f", aspectRatio));
                }
                return true;
            }
        }

        return false;
    }

    private void sendCommand(String cmd) {
        Log.i(TAG, "AI Command: " + cmd);
        try {
            mqtt.publish(robotTopic, cmd);
        } catch (Exception e) {
            Log.e(TAG, "Failed to publish: " + e.getMessage());
        }
    }

    public void stopDetection() {
        try {
            isDetecting = false;

            if (!lastCommand.equals("STOP")) {
                sendCommand("STOP");
            }

            if (cameraProvider != null) {
                cameraProvider.unbindAll();
                cameraProvider = null;
                Log.d(TAG, "📹 AI camera unbound");
            }

            if (detector != null) {
                detector.close();
                detector = null;
            }

            if (cameraExecutor != null && !cameraExecutor.isShutdown()) {
                cameraExecutor.shutdown();
            }

            Log.i(TAG, "AI detection stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping detection: " + e.getMessage());
        }
    }
}