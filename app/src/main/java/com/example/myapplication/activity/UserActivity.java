package com.example.myapplication.activity;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;

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
    private PreviewView previewView;

    private AgoraHelper agora;
    private MqttManager mqtt;
    private AIHelper aiHelper;
    private ExecutorService backgroundExecutor;

    private boolean aiEnabled = false;
    private boolean mqttReady = false;

    private String controlTopic;
    private String aiTopic;
    private int robotID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user);

        Log.d(TAG, "UserActivity onCreate");

        videoContainer = findViewById(R.id.videoContainer);
        previewView = findViewById(R.id.cameraPreview);
        backgroundExecutor = Executors.newSingleThreadExecutor();

        robotID = getIntent().getIntExtra("id", 0);
        controlTopic = "robot/" + robotID + "/control";
        aiTopic = "robot/" + robotID + "/ai_mode";

        Log.d(TAG, "Robot: " + robotID + " | Control: " + controlTopic + " | AI: " + aiTopic);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Intent result = new Intent();
                int robotId = getIntent().getIntExtra("id", 0);
                result.putExtra("robotId", robotId);
                setResult(RESULT_OK, result);

                setEnabled(false);
                finish();
            }
        });


        backgroundExecutor.execute(this::initializeConnections);
    }


    private void initializeConnections() {
        try {
            mqtt = new MqttManager(
                    MqttManagerConfig.HOST,
                    MqttManagerConfig.PORT,
                    MqttManagerConfig.USERNAME,
                    MqttManagerConfig.PASSWORD
            );
            mqttReady = true;
            Log.d(TAG, "MQTT connected successfully");

            String statusTopic = "robot/" + robotID + "/status";
            mqtt.publish(statusTopic, "online");
            Log.d(TAG, "Robot " + robotID + " marked ONLINE");

            aiHelper = new AIHelper(this, previewView, mqtt, controlTopic);

            agora = new AgoraHelper(this, getString(R.string.agora_app_id), rtcHandler);
            String channel = "robot_" + robotID + "_channel";
            agora.joinChannel(getString(R.string.agora_access_token), channel);

            listenForAIMode(aiTopic);

        } catch (Exception e) {
            Log.e(TAG, "Error initializing connections: " + e.getMessage(), e);
        }
    }


    private void waitForPreviewViewThenStart() {
        previewView.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        previewView.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                        int width = previewView.getWidth();
                        int height = previewView.getHeight();

                        if (width > 0 && height > 0) {
                            Log.d(TAG, "PreviewView ready: " + width + "x" + height);
                            aiHelper.startDetection();
                            aiEnabled = true;
                        } else {
                            Log.e(TAG, "PreviewView has invalid dimensions!");
                        }
                    }
                }
        );
    }


    private void listenForAIMode(String topic) {
        try {
            mqtt.getClient().subscribeWith()
                    .topicFilter(topic)
                    .callback(publish -> {
                        String msg = new String(publish.getPayloadAsBytes()).trim();
                        boolean newState = msg.equalsIgnoreCase("ON");
                        aiEnabled = newState;
                        Log.d(TAG, "AI Mode update: " + msg + " → " + newState);

                        runOnUiThread(() -> {
                            if (aiEnabled) {
                                Log.d(TAG, "Starting AI detection (topic triggered)");
                                waitForPreviewViewThenStart();
                            } else if (aiHelper != null) {
                                aiHelper.stopDetection();
                                Log.d(TAG, "AI detection stopped (topic triggered)");
                            }
                        });
                    })
                    .send()
                    .whenComplete((ack, throwable) -> {
                        if (throwable != null) {
                            Log.e(TAG, "Failed to subscribe to " + topic + ": " + throwable.getMessage());
                            aiEnabled = false;
                        } else {
                            Log.d(TAG, "Subscribed to AI topic: " + topic);
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Exception subscribing to AI topic: " + e.getMessage());
            aiEnabled = false;
        }
    }

    private void retrieveInfo() {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("robotId", robotID);
        setResult(RESULT_OK, resultIntent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Camera permission granted");
                if (aiEnabled) waitForPreviewViewThenStart();
            } else {
                Toast.makeText(this, "Camera permission required for AI mode", Toast.LENGTH_LONG).show();
                Log.e(TAG, "Camera permission denied");
            }
        }
    }

    private final IRtcEngineEventHandler rtcHandler = new IRtcEngineEventHandler() {
        @Override
        public void onUserJoined(int uid, int elapsed) {
            runOnUiThread(() -> agora.showRemoteVideo(videoContainer, uid));
        }

        @Override
        public void onUserOffline(int uid, int reason) {
            runOnUiThread(() -> videoContainer.removeAllViews());
        }
    };

    @Override
    protected void onDestroy() {
        Log.d(TAG, "Cleaning up resources");

        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.execute(() -> {
                try {
                    if (aiHelper != null) aiHelper.stopDetection();

                    if (agora != null) {
                        Log.d(TAG, "Agora leave() called");
                        agora.leave();
                        Thread.sleep(300);
                        agora.destroy();
                        Log.d(TAG, "Agora destroyed safely");
                    }

                    if (mqtt != null) mqtt.disconnect();

                } catch (Exception e) {
                    Log.e(TAG, "Error during cleanup: " + e.getMessage());
                } finally {
                    backgroundExecutor.shutdown();
                }
            });
        } else if (mqtt != null) {
            mqtt.disconnect();
        }

        super.onDestroy();
    }
}
