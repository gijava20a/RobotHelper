package com.example.myapplication.activity;

import android.content.Intent;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.controller);

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

    private void setupButtons(int robotID) {
        Button aiButton = findViewById(R.id.btn_ai_control);
        aiButton.setOnClickListener(v -> {
            aiMode = !aiMode;
            aiButton.setBackgroundTintList(ColorStateList.valueOf(
                    aiMode ? Color.RED : Color.parseColor("#4CAF50")));

            if (mqtt != null) {
                backgroundExecutor.execute(() ->
                        mqtt.publish("robot/" + robotID + "/ai_mode", aiMode ? "ON" : "OFF"));
                Log.d(TAG, "AI mode toggled → " + (aiMode ? "ON" : "OFF"));
            } else {
                Log.w(TAG, "MQTT not ready yet — AI mode toggle skipped");
            }
        });

        setupPressEffect(R.id.btn_up, "FORWARD");
        setupPressEffect(R.id.btn_down, "BACK");
        setupPressEffect(R.id.btn_left, "LEFT");
        setupPressEffect(R.id.btn_right, "RIGHT");
        setupPressEffect(R.id.btn_stop, "STOP");
    }

    private void setupPressEffect(int buttonId, String command) {
        View btn = findViewById(buttonId);
        btn.setOnTouchListener((v, event) -> {
            if (aiMode) return true;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.setAlpha(0.6f);
                    sendCmd(command);
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
            runOnUiThread(() -> agora.showRemoteVideo(cameraView, uid));
        }

        @Override
        public void onUserOffline(int uid, int reason) {
            runOnUiThread(() -> cameraView.removeAllViews());
        }
    };

    private void sendCmd(String cmd) {
        if (mqtt != null) {
            backgroundExecutor.execute(() -> mqtt.publish("robot/" + robotId + "/control", cmd));
            Log.d(TAG, "Sent command: " + cmd);
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
        backgroundExecutor.execute(() -> {
            if (agora != null) {
                agora.leave();
                agora.destroy();
            }
            if (mqtt != null) {
                mqtt.disconnect();
                Log.d(TAG, "Robot " + robotId + " disconnected cleanly");
            }
        });
        backgroundExecutor.shutdown();
    }
}
