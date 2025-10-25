package com.example.myapplication.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.myapplication.R;
import com.example.myapplication.agora.AgoraHelper;
import com.example.myapplication.service.MqttManager;
import com.example.myapplication.service.MqttManagerConfig;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.agora.rtc2.IRtcEngineEventHandler;

public class Controller extends AppCompatActivity {

    private static final String TAG = "Controller";

    private FrameLayout cameraView;
    private AgoraHelper agora;
    private MqttManager mqtt;
    private boolean aiMode = false;
    private ExecutorService backgroundExecutor;

    private int robotId;

    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.controller);

        if (!checkPermissions()) {
            requestPermissions();
        }

        backgroundExecutor = Executors.newSingleThreadExecutor();
        cameraView = findViewById(R.id.camera_preview_container);
        robotId = getIntent().getIntExtra("id", 0);

        Log.d(TAG, "🎮 Controller started for robot: " + robotId);

        backgroundExecutor.execute(() -> {
            try {
                mqtt = new MqttManager(
                        MqttManagerConfig.HOST,
                        MqttManagerConfig.PORT,
                        MqttManagerConfig.USERNAME,
                        MqttManagerConfig.PASSWORD
                );
                String statusTopic = "robot/" + robotId + "/status";
                mqtt.publish(statusTopic, "admin_connected");
                Log.d(TAG, "Robot " + robotId + " marked ONLINE");

                agora = new AgoraHelper(this, getString(R.string.agora_app_id), rtcHandler);
                String channel = "robot_" + robotId + "_channel";
                agora.joinChannel(getString(R.string.agora_access_token), channel);
                Log.d(TAG, "Agora joined: " + channel);

            } catch (Exception e) {
                Log.e(TAG, "Error initializing MQTT/Agora: " + e.getMessage());
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                retrieveInfo();
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });

        setupButtons(robotId);
    }

    private boolean checkPermissions() {
        String[] permissions = {
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.MODIFY_AUDIO_SETTINGS
        };

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Missing permission: " + permission);
                return false;
            }
        }
        Log.d(TAG, "All permissions granted");
        return true;
    }

    private void requestPermissions() {
        Log.d(TAG, "Requesting permissions...");
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.MODIFY_AUDIO_SETTINGS
                },
                PERMISSION_REQUEST_CODE
        );
    }

    private void setupButtons(int robotID) {
        Button aiButton = findViewById(R.id.btn_ai_control);
        aiButton.setOnClickListener(v -> {
            aiMode = !aiMode;
            aiButton.setBackgroundTintList(ColorStateList.valueOf(
                    aiMode ? Color.RED : Color.parseColor("#4CAF50")));
            aiButton.setText(aiMode ? "AI: ON" : "AI: OFF");

            if (mqtt != null) {
                backgroundExecutor.execute(() ->
                        mqtt.publish("robot/" + robotID + "/ai_mode", aiMode ? "ON" : "OFF"));
                Log.d(TAG, "AI mode toggled → " + (aiMode ? "ON" : "OFF"));
            } else {
                Log.w(TAG, "MQTT not ready yet — AI mode toggle skipped");
            }
        });

        setupKeyBehavior(R.id.btn_up, "FORWARD");
        setupKeyBehavior(R.id.btn_down, "BACKWARD");
        setupKeyBehavior(R.id.btn_left, "LEFT");
        setupKeyBehavior(R.id.btn_right, "RIGHT");

        setupStopButton(R.id.btn_stop);
    }


    private void setupKeyBehavior(int buttonId, String command) {
        View btn = findViewById(buttonId);

        btn.setOnTouchListener((v, event) -> {
            if (aiMode) {
                return true;
            }

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.setAlpha(0.6f);
                    sendCmd(command);
                    Log.d(TAG, "Button pressed: " + command);
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setAlpha(1f);
                    sendCmd("STOP");
                    Log.d(TAG, "Button released: STOP");
                    v.performClick();
                    break;
            }
            return true;
        });
    }


    private void setupStopButton(int buttonId) {
        View btn = findViewById(buttonId);

        btn.setOnTouchListener((v, event) -> {
            if (aiMode) {
                return true;
            }

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.setAlpha(0.6f);
                    sendCmd("STOP");
                    Log.d(TAG, "STOP pressed");
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setAlpha(1f);
                    v.performClick();
                    break;
            }
            return true;
        });
    }

    private final IRtcEngineEventHandler rtcHandler = new IRtcEngineEventHandler() {
        @Override
        public void onUserJoined(int uid, int elapsed) {
            Log.d(TAG, "Remote user joined: " + uid);
            runOnUiThread(() -> {
                if (agora != null && cameraView != null) {
                    agora.showRemoteVideo(cameraView, uid);
                }
            });
        }

        @Override
        public void onUserOffline(int uid, int reason) {
            Log.d(TAG, "Remote user offline: " + uid);
            runOnUiThread(() -> {
                if (cameraView != null) {
                    cameraView.removeAllViews();
                }
            });
        }
    };

    private void sendCmd(String cmd) {
        if (mqtt != null) {
            backgroundExecutor.execute(() -> {
                mqtt.publish("robot/" + robotId + "/control", cmd);
            });
            Log.d(TAG, "Sent: " + cmd);
        } else {
            Log.w(TAG, "MQTT not connected — command skipped: " + cmd);
        }
    }

    private void retrieveInfo() {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("robotId", robotId);
        setResult(RESULT_OK, resultIntent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mqtt != null) {
            backgroundExecutor.execute(() -> {
                mqtt.publish("robot/" + robotId + "/control", "STOP");
                Log.d(TAG, "Final STOP sent before disconnect");
            });
        }

        backgroundExecutor.execute(() -> {
            try {
                if (agora != null) {
                    agora.leave();
                    Thread.sleep(200);
                    agora.destroy();
                    Log.d(TAG, "Agora destroyed");
                }

                if (mqtt != null) {
                    String statusTopic = "robot/" + robotId + "/status";
                    mqtt.publish(statusTopic, "admin_disconnected");
                    mqtt.disconnect();
                    Log.d(TAG, "Robot " + robotId + " disconnected cleanly");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error during cleanup: " + e.getMessage());
            }
        });

        backgroundExecutor.shutdown();
    }
}