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

public class ControllerChoice extends AppCompatActivity {

    private static final String TAG = "ControllerChoice";
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
                            int returnedRobotId = result.getData().getIntExtra("robotId", -1);
                            if (returnedRobotId >= 0) {
                                Log.d(TAG, "Returned from robot: " + returnedRobotId);
                                handleReturnedRobot(returnedRobotId);
                            }
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.controller_choice);

        recyclerView = findViewById(R.id.recyclerViewRobots);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new RobotAdapter(robotList, "controller", robot -> {
            Intent intent = new Intent(this, Controller.class);
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
            Log.d(TAG, "MQTT connected in ControllerChoice");

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
        if (robotId < 0 || robotId >= robotList.size()) return;

        Robot robot = robotList.get(robotId);
        String currentStatus = robot.getStatus();

        if ("offline".equalsIgnoreCase(currentStatus)) {
            Log.d(TAG, "Robot " + robotId + " is offline, no changes made.");
            return;
        }

        robot.setStatus("online");
        adapter.notifyItemChanged(robotId);
        Log.d(TAG, "Robot " + robotId + " marked as online (controller returned)");

        Runnable publishTask = () ->
                mqtt.publish("robot/" + robotId + "/status", "online");

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
