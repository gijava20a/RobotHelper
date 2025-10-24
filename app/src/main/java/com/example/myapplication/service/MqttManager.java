package com.example.myapplication.service;

import android.util.Log;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttClientState;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;

import java.util.UUID;

public class MqttManager {

    private Mqtt3AsyncClient client;

    /**
     * Constructor for connecting to a remote/cloud broker
     *
     * @param brokerHost Broker IP or domain (MQTTX cloud broker)
     * @param brokerPort Broker port (usually 1883)
     * @param username   Optional username (use null if not required)
     * @param password   Optional password (use null if not required)
     */
    public MqttManager(String brokerHost, int brokerPort, String username, String password) {

        String clientId = "AndroidClient-" + UUID.randomUUID();

        client = MqttClient.builder()
                .useMqttVersion3()
                .identifier(clientId)
                .serverHost(brokerHost)
                .serverPort(brokerPort)
                .sslWithDefaultConfig()
                .buildAsync();

        // Connect to broker
        if (username != null && password != null) {
            connectWithAuth(username, password);
        } else {
            connect();
        }

        waitUntilConnected();
    }

    // Connect without authentication
    private void connect() {
        client.connect()
                .whenComplete((connAck, throwable) -> {
                    if (throwable != null) {
                        Log.e("MQTT", "❌ Failed to connect: " + throwable.getMessage());
                    } else {
                        Log.d("MQTT", "✅ Connected to broker!");
                    }
                });
    }

    public boolean isConnected() {
        return client.getState() == MqttClientState.CONNECTED;
    }

    private void waitUntilConnected() {
        int tries = 0;
        while (client.getState() != MqttClientState.CONNECTED && tries < 50) {
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            tries++;
        }
    }

    // Connect with username/password
    private void connectWithAuth(String username, String password) {
        client.connectWith()
                .simpleAuth()
                .username(username)
                .password(password.getBytes())
                .applySimpleAuth()
                .send()
                .whenComplete((connAck, throwable) -> {
                    if (throwable != null) {
                        Log.e("MQTT", "❌ Failed to connect with auth: " + throwable.getMessage());
                    } else {
                        Log.d("MQTT", "✅ Connected to broker with auth!");
                    }
                });
    }

    // Publish a message to a topic
    public void publish(String topic, String message) {
        if (!client.getState().isConnected()) {
            Log.e("MQTT", "❌ Client not connected, cannot publish");
            return;
        }

        client.publishWith()
                .topic(topic)
                .payload(message.getBytes())
                .retain(true) // retain last message for testing
                .send()
                .whenComplete((ack, throwable) -> {
                    if (throwable != null) {
                        Log.e("MQTT", "❌ Publish failed: " + throwable.getMessage());
                    } else {
                        Log.d("MQTT", "✅ Published to " + topic + ": " + message);
                    }
                });
    }

    // Disconnect cleanly
    public void disconnect() {
        if (client.getState().isConnected()) {
            client.disconnect();
        }
    }

    // Return client (optional, if you want advanced usage)
    public Mqtt3AsyncClient getClient() {
        return client;
    }
}
