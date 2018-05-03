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

/**
 * MqttHelper
 * Helper class which exposes the functionality of the paho mqtt library and makes the
 * use of the methods cleaner when used in activities of the application.
 *
 * @author Mikael Mölder
 * @version 1.0
 * @since 2018-04-16
 */
public class MqttHelper {
    public MqttAndroidClient mqttAndroidClient;

    final String serverURI = "tcp://almanacscsh.cloudapp.net:1883"; // address of mqtt broker

    final String clientId = "AirPollutionPi";   // identifier of client
    final String subscriptionTopic = "test";    // default topic to subscribe and send messages to

    public final String TAG = "MqttHelper";

    /**
     * MqttHelper
     * Constructor that creates a mqtt android client and registers callback methods before it
     * connects ot the broker.
     *
     * @param context The context of the application calling on the method
     */
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

    /**
     * setCallback
     * Helper method which calls the method with the same name in the paho mqtt library
     *
     * @param callback
     */
    public void setCallback(MqttCallbackExtended callback) {
        mqttAndroidClient.setCallback(callback);
    }

    /**
     * connect
     * Attempts to connect to the specified broker and listens to the result
     */
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

    /**
     * subscribeToTopic
     * Attempts to subscribe to the specified topic, fails if no connection to broker exists.
     */
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

    /**
     * publish
     * Attempts to publish a payload to the specified topic.
     *
     * @param payload The message to be published
     * @param topic The topic to publish the message to
     */
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
