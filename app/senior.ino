#include <WiFi.h>
#include <PubSubClient.h>
#include <WiFiClientSecure.h>

#define DIR_1 4
#define DIR_2 16
#define speed1 17

#define DIR_3 12
#define DIR_4 14
#define speed2 27 

#define DIR_5 23
#define DIR_6 32
#define speed3 25

#define LED_PIN 2

const char* wifi_name = "Redmi Note 13 Pro 5G";
const char* wifi_pass = "rame";

const char* mqtt_server = "hivemq.cloud";
const int mqtt_port = 8883;
const char* user = "rame1";
const char* pass = "Rame1234";
const char* mqtt_topic = "robot/0/control";

WiFiClientSecure espClient;
PubSubClient client(espClient);

void forward() {
  digitalWrite(DIR_1, HIGH);
  digitalWrite(DIR_2, LOW);
  digitalWrite(DIR_3, LOW);
  digitalWrite(DIR_4, HIGH);
  digitalWrite(DIR_5, LOW);
  digitalWrite(DIR_6, LOW);
}

void back() {
  digitalWrite(DIR_1, LOW);
  digitalWrite(DIR_2, HIGH);
  digitalWrite(DIR_3, HIGH);
  digitalWrite(DIR_4, LOW);
  digitalWrite(DIR_5, LOW);
  digitalWrite(DIR_6, LOW);
}

void turnLeft() {
  digitalWrite(DIR_1, LOW);
  digitalWrite(DIR_2, HIGH);
  digitalWrite(DIR_3, LOW);
  digitalWrite(DIR_4, HIGH);
  digitalWrite(DIR_5, LOW);
  digitalWrite(DIR_6, HIGH);
}

void turnRight() {
  digitalWrite(DIR_1, HIGH);
  digitalWrite(DIR_2, LOW);
  digitalWrite(DIR_3, HIGH);
  digitalWrite(DIR_4, LOW);
  digitalWrite(DIR_5, HIGH);
  digitalWrite(DIR_6, LOW);
}

void stop() {
  digitalWrite(DIR_1, LOW);
  digitalWrite(DIR_2, LOW);
  digitalWrite(DIR_3, LOW);
  digitalWrite(DIR_4, LOW);
  digitalWrite(DIR_5, LOW);
  digitalWrite(DIR_6, LOW);
}

void callback(char* topic, byte* payload, unsigned int length) {
  String message;
  for (unsigned int i = 0; i < length; i++) message += (char)payload[i];
  message.toLowerCase();

  Serial.print("Message arrived [");
  Serial.print(topic);
  Serial.print("]: ");
  Serial.println(message);

  if (message == "forward") forward();
  else if (message == "backward") back();
  else if (message == "left") turnLeft();
  else if (message == "right") turnRight();
  else if (message == "stop") stop();
}

bool connectWiFi() {
  Serial.print("Connecting to Wi-Fi: ");
  Serial.println(wifi_name);
  WiFi.begin(wifi_name, wifi_pass);

  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - start < 20000) {
    delay(500);
    Serial.print(".");
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\nWi-Fi connected!");
    Serial.println(WiFi.localIP());
    return true;
  } else {
    Serial.println("\nWi-Fi connection failed!");
    return false;
  }
}

bool connectMQTT() {
  unsigned long start = millis();
  while (!client.connected() && millis() - start < 15000) {
    String clientId = "ESP32-" + String(random(0xffff), HEX);
    Serial.print("Connecting to MQTT...");

    if (client.connect(clientId.c_str(), user, pass)) {
      Serial.println("connected!");
      client.subscribe(mqtt_topic);
      return true;
    } else {
      Serial.print("failed, rc=");
      Serial.print(client.state());
      Serial.println(" — retrying...");
      delay(1000);
    }
  }
  return client.connected();
}

void setup() {
  Serial.begin(115200);
  pinMode(LED_PIN, OUTPUT);
  
  pinMode(DIR_1, OUTPUT);
  pinMode(DIR_2, OUTPUT);
  pinMode(DIR_3, OUTPUT);
  pinMode(DIR_4, OUTPUT);
  pinMode(DIR_5, OUTPUT);
  pinMode(DIR_6, OUTPUT);

  espClient.setInsecure();
  client.setServer(mqtt_server, mqtt_port);
  client.setCallback(callback);

  connectWiFi();
  connectMQTT();
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) connectWiFi();
  if (!client.connected()) connectMQTT();
  client.loop();
}
