package com.example.myapplication.activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.DTO.Robot;
import com.example.myapplication.R;
import com.example.myapplication.service.MqttManager;
import com.example.myapplication.service.MqttManagerConfig;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class UserChoice extends AppCompatActivity {

    private static final String TAG = "UserChoice";
    private RecyclerView recyclerView;
    private RobotAdapter adapter;
    private List<Robot> robotList = new ArrayList<>();
    private MqttManager mqtt;
    private boolean mqttReady = false;
    private final Queue<Runnable> mqttTaskQueue = new LinkedList<>();

    private final ActivityResultLauncher<Intent> robotLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            int returnedrobotID = result.getData().getIntExtra("robotId", -1);
                            if (returnedrobotID != -1) {
                                Log.d(TAG, "User returned from robot: " + returnedrobotID);
                                handleReturnedRobot(returnedrobotID);
                            }
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.user_detection_layer);

        recyclerView = findViewById(R.id.recyclerViewRobots);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new RobotAdapter(robotList, "user", robot -> {
            Intent intent = new Intent(this, UserActivity.class);
            intent.putExtra("id", robot.getId());
            robotLauncher.launch(intent);
        });
        recyclerView.setAdapter(adapter);

        initializeRobotList();
        initializeMqtt();
    }

    private void initializeMqtt() {
        new Thread(() -> {
            mqtt = new MqttManager(
                    MqttManagerConfig.HOST,
                    MqttManagerConfig.PORT,
                    MqttManagerConfig.USERNAME,
                    MqttManagerConfig.PASSWORD
            );
            mqttReady = true;
            Log.d(TAG, "MQTT connected in UserChoice");

            while (!mqttTaskQueue.isEmpty()) {
                mqttTaskQueue.poll().run();
            }

            runOnUiThread(this::subscribeToRobotStatuses);
        }).start();
    }

    private void initializeRobotList() {
        robotList.clear();
        for (int i = 0; i < 5; i++) {
            robotList.add(new Robot(i, "Robot " + i, "offline"));
        }
        adapter.notifyDataSetChanged();
        Log.d(TAG, "Initialized " + robotList.size() + " robots");
    }

    private void subscribeToRobotStatuses() {
        for (int i = 0; i < robotList.size(); i++) {
            final int robotId = i;
            String topic = "robot/" + i + "/status";

            mqtt.getClient().subscribeWith()
                    .topicFilter(topic)
                    .callback(publish -> {
                        String status = new String(publish.getPayloadAsBytes());
                        Log.d(TAG, "Robot " + robotId + " status: " + status);

                        runOnUiThread(() -> updateRobotStatus(robotId, status));
                    })
                    .send()
                    .whenComplete((subAck, throwable) -> {
                        if (throwable != null) {
                            Log.e(TAG, "Failed to subscribe to " + topic + ": " + throwable.getMessage());
                        } else {
                            Log.d(TAG, "Subscribed to " + topic);
                        }
                    });
        }
    }

    private void updateRobotStatus(int robotId, String status) {
        if (robotId >= 0 && robotId < robotList.size()) {
            Robot robot = robotList.get(robotId);
            robot.setStatus(status);
            adapter.notifyItemChanged(robotId, status);
            Log.d(TAG, "Updated Robot " + robotId + " -> " + status);
        }
    }

    private void handleReturnedRobot(int robotId) {
        robotList.get(robotId).setStatus("disconnected");
        adapter.notifyItemChanged(robotId);
        Log.d(TAG, "Robot " + robotId + " marked as disconnected (user left)");

        Runnable publishTask = () ->
                mqtt.publish("robot/" + robotId + "/status", "offline");

        if (mqttReady && mqtt != null) {
            publishTask.run();
        } else {
            mqttTaskQueue.add(publishTask);
            Log.w(TAG, "MQTT not ready yet, queued publish for " + robotId);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mqtt != null) {
            mqtt.disconnect();
        }
    }
}
