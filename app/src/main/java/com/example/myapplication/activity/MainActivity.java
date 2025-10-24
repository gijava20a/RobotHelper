package com.example.myapplication.activity;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.myapplication.R;
import com.example.myapplication.service.MqttManager;
import com.example.myapplication.service.MqttManagerConfig;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        var controllerButton = findViewById(R.id.controller);
        controllerButton.setOnClickListener(l -> {
            Intent intent = new Intent(MainActivity.this, ControllerChoice.class);
            startActivity(intent);
        });

        var userButton = findViewById(R.id.user);
        userButton.setOnClickListener(l -> {
            Intent intent = new Intent(MainActivity.this, UserChoice.class);
            startActivity(intent);
        });
        MqttManager mqtt;
        mqtt = new MqttManager(
                MqttManagerConfig.HOST,
                MqttManagerConfig.PORT,
                MqttManagerConfig.USERNAME,
                MqttManagerConfig.PASSWORD
        );

        for (int i = 0; i < 5; i++) {
            String statusTopic = "robot/" + i + "/status";
            String aiTopic = "robot/" + i + "/ai_mode";
            String move = "robot/" + i + "/control";
            mqtt.publish(statusTopic, "offline");
            mqtt.publish(aiTopic, "OFF");
            mqtt.publish(aiTopic, "OFF");
            mqtt.publish(move, "STOP");
        }
    }
}