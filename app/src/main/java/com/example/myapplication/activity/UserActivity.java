package com.example.myapplication.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.myapplication.R;
import com.example.myapplication.agora.AgoraHelper;
import com.example.myapplication.ai.AIHelper;
import com.example.myapplication.service.MqttManager;
import com.example.myapplication.service.MqttManagerConfig;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.agora.rtc2.IRtcEngineEventHandler;

public class UserActivity extends AppCompatActivity {

    private static final String TAG = "UserActivity";
    private static final int CAMERA_PERMISSION_CODE = 100;

    private FrameLayout videoContainer;

    private AgoraHelper agora;
    private MqttManager mqtt;
    private AIHelper aiHelper;
    private ExecutorService backgroundExecutor;

    private boolean aiEnabled = false;
    private boolean cameraPermissionGranted = false;

    private String controlTopic;
    private String aiTopic;
    private int robotID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user);

        Log.d(TAG, "UserActivity onCreate");

        videoContainer = findViewById(R.id.videoContainer);

        if (videoContainer == null) {
            Log.e(TAG, "CRITICAL: videoContainer is NULL!");
            Toast.makeText(this, "Error: Video container not found", Toast.LENGTH_LONG).show();
            return;
        }

        Log.d(TAG, "VideoContainer initialized");

        backgroundExecutor = Executors.newSingleThreadExecutor();

        robotID = getIntent().getIntExtra("id", 0);
        controlTopic = "robot/" + robotID + "/control";
        aiTopic = "robot/" + robotID + "/ai_mode";

        Log.d(TAG, "Robot ID: " + robotID);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Intent result = new Intent();
                result.putExtra("robotId", robotID);
                setResult(RESULT_OK, result);
                setEnabled(false);
                finish();
            }
        });

        checkCameraPermission();
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {

            cameraPermissionGranted = true;
            Log.d(TAG, "Camera permission granted");

            backgroundExecutor.execute(this::initializeConnections);
        } else {
            Log.d(TAG, "Requesting camera permission...");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_CODE);
        }
    }

    private void initializeConnections() {
        try {
            mqtt = new MqttManager(
                    MqttManagerConfig.HOST,
                    MqttManagerConfig.PORT,
                    MqttManagerConfig.USERNAME,
                    MqttManagerConfig.PASSWORD
            );
            Log.d(TAG, "MQTT connected");

            String statusTopic = "robot/" + robotID + "/status";
            mqtt.publish(statusTopic, "online");
            Log.d(TAG, "Robot " + robotID + " marked ONLINE");

            runOnUiThread(() -> {
                aiHelper = new AIHelper(this, mqtt, controlTopic);
                Log.d(TAG, "AIHelper initialized");
            });

            agora = new AgoraHelper(this, getString(R.string.agora_app_id), rtcHandler);
            String channel = "robot_" + robotID + "_channel";
            agora.joinChannel(getString(R.string.agora_access_token), channel);
            Log.d(TAG, "Agora joined: " + channel);

            listenForAIMode(aiTopic);

        } catch (Exception e) {
            Log.e(TAG, "Init error: " + e.getMessage(), e);
            runOnUiThread(() ->
                    Toast.makeText(this, "Connection error: " + e.getMessage(), Toast.LENGTH_LONG).show()
            );
        }
    }

    private void listenForAIMode(String topic) {
        try {
            mqtt.getClient().subscribeWith()
                    .topicFilter(topic)
                    .callback(publish -> {
                        String msg = new String(publish.getPayloadAsBytes()).trim();
                        boolean newState = msg.equalsIgnoreCase("ON");

                        Log.i(TAG, "AI Mode command: '" + msg + "' → " + newState);

                        runOnUiThread(() -> {
                            if (newState && !aiEnabled) {
                                Log.i(TAG, "ACTIVATING AI MODE");
                                Toast.makeText(this, "AI Mode Activated", Toast.LENGTH_SHORT).show();
                                startAIDetection();
                            } else if (!newState && aiEnabled) {
                                Log.i(TAG, "DEACTIVATING AI MODE");
                                Toast.makeText(this, "Manual Control", Toast.LENGTH_SHORT).show();
                                stopAIDetection();
                            }
                        });
                    })
                    .send()
                    .whenComplete((ack, throwable) -> {
                        if (throwable != null) {
                            Log.e(TAG, "Failed to subscribe: " + throwable.getMessage());
                        } else {
                            Log.d(TAG, "Subscribed to AI topic: " + topic);
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Subscribe error: " + e.getMessage());
        }
    }

    private void startAIDetection() {
        if (!cameraPermissionGranted) {
            Log.e(TAG, "No camera permission");
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
            return;
        }

        if (aiHelper == null) {
            Log.e(TAG, "AIHelper is null");
            return;
        }

        Log.i(TAG, "Starting AI person tracking...");
        aiHelper.startDetection();
        aiEnabled = true;
    }

    private void stopAIDetection() {
        if (aiHelper != null && aiEnabled) {
            aiHelper.stopDetection();
            aiEnabled = false;
            Log.d(TAG, "AI stopped");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                cameraPermissionGranted = true;
                Log.d(TAG, "Camera permission granted");

                backgroundExecutor.execute(this::initializeConnections);
            } else {
                cameraPermissionGranted = false;
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show();
                Log.e(TAG, "Camera permission denied");
                finish();
            }
        }
    }

    private final IRtcEngineEventHandler rtcHandler = new IRtcEngineEventHandler() {
        @Override
        public void onUserJoined(int uid, int elapsed) {
            Log.d(TAG, "Remote user joined: " + uid);
            runOnUiThread(() -> {
                if (agora != null && videoContainer != null) {
                    agora.showRemoteVideo(videoContainer, uid);
                    Log.d(TAG, "Showing remote video in container");
                }
            });
        }

        @Override
        public void onUserOffline(int uid, int reason) {
            Log.d(TAG, "Remote user offline: " + uid);
            runOnUiThread(() -> {
                if (videoContainer != null) {
                    videoContainer.removeAllViews();
                }
            });
        }
    };

    @Override
    protected void onDestroy() {
        Log.d(TAG, "Cleanup");

        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.execute(() -> {
                try {
                    if (aiHelper != null && aiEnabled) {
                        aiHelper.stopDetection();
                    }

                    if (agora != null) {
                        agora.leave();
                        Thread.sleep(300);
                        agora.destroy();
                    }

                    if (mqtt != null) {
                        String statusTopic = "robot/" + robotID + "/status";
                        mqtt.publish(statusTopic, "offline");
                        mqtt.disconnect();
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Cleanup error: " + e.getMessage());
                } finally {
                    backgroundExecutor.shutdown();
                }
            });
        }

        super.onDestroy();
    }
}