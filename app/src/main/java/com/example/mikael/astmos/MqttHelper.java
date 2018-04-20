package com.example.mikael.astmos;

import android.content.Context;
import android.util.Log;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;

public class MqttHelper {
    public MqttAndroidClient mqttAndroidClient;

    final String serverURI = "tcp://almanacscsh.cloudapp.net:1883";

    final String clientId = "AirPollutionPi";
    final String subscriptionTopic = "test";

    public final String TAG = "MqttHelper";

    public MqttHelper(Context context) {
        mqttAndroidClient = new MqttAndroidClient(context, serverURI, clientId);
        mqttAndroidClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                Log.d(TAG, "connectComplete: mqtt");
            }

            @Override
            public void connectionLost(Throwable cause) {
                Log.d(TAG, "connectionLost: mqtt");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                Log.d(TAG, "messageArrived: mqtt");
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                Log.d(TAG, "deliveryComplete: mqtt");
            }
        });
        connect();
    }

    public void setCallback(MqttCallbackExtended callback) {
        mqttAndroidClient.setCallback(callback);
    }

    private void connect() {
        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setAutomaticReconnect(true);
        mqttConnectOptions.setCleanSession(false);
        //mqttConnectOptions.setUserName("");
        //mqttConnectOptions.setPassword("");

        try {
            mqttAndroidClient.connect(mqttConnectOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
                    disconnectedBufferOptions.setBufferEnabled(true);
                    disconnectedBufferOptions.setBufferSize(100);
                    disconnectedBufferOptions.setPersistBuffer(false);
                    disconnectedBufferOptions.setDeleteOldestMessages(false);
                    mqttAndroidClient.setBufferOpts(disconnectedBufferOptions);
                    subscribeToTopic();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.d(TAG, "onFailure: failed to connect to " + serverURI + ", " + exception.toString());
                }
            });
        } catch (MqttException ex) {
            ex.printStackTrace();
        }
    }

    private void subscribeToTopic() {
        try {
            mqttAndroidClient.subscribe(subscriptionTopic, 0, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "onSuccess: subscribed to topic: " + subscriptionTopic);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.d(TAG, "onFailure: could not subscribed to topic: " + subscriptionTopic);
                }
            });
        } catch (MqttException ex) {
            ex.printStackTrace();
        }
    }

    public void publish(JSONObject payload, String topic) {
        byte[] encodedPayload = new byte[0];
        try {
            String msg = payload.toString();
            encodedPayload = msg.getBytes("UTF-8");
            MqttMessage message = new MqttMessage(encodedPayload);
            mqttAndroidClient.publish(topic, message);

        } catch (UnsupportedEncodingException | MqttException ex) {
            ex.printStackTrace();
        }
    }
}
