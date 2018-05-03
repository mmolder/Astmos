package com.example.mikael.astmos;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * MainActivity
 * The "main" view of the application which displays what is seen when launching it.
 * Contains a button and a number of textviews which displays information about sensor
 * values and location. When the button is pressed a rfcomm-socket is established between
 * the device and a paired bluetooth device (raspberry pi) which starts to send sensor
 * data on a regular interval. When a buffer is filled with data, and average is
 * calculated and sent to a mqtt broker from the (this) device running the app.
 *
 * @author      Mikael Mölder
 * @version     1.0
 * @since       2018-04-16
 */
public class MainActivity extends Activity implements LocationListener {

    /* object definitions */
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice = null;
    MqttHelper mqttHelper;
    LocationManager locationManager;
    Thread btThread;
    Coordinate currentLocation;
    Handler handler;

    /* textview and button definitions */
    TextView locationText;
    TextView sensorType;
    TextView sensorValue;
    TextView connDevice;
    Button startValueBtn;
    Button stopValueBtn;
    Button shutdownPi;

    /* list and variable definitions */
    Set<BluetoothDevice> pairedDevices = null;
    ArrayList<Double> recentValues = new ArrayList<>();
    final byte delimiter = 33;
    int readBufferPosition = 0;

    /* tag used for logging */
    private final String TAG = "MainActivity";

    /**
     * openConnection
     * When called upon, create a rfcomm-socket between this deice and a paired bluetooth
     * device with the matching uuid (same on both devices). Only call if a paired device
     * exists, otherwise will result in an exception.
     */
    public void openConnection() {
        UUID uuid = UUID.fromString("94f39d29-7d6d-437d-973b-fba39e49d4ee"); //Standard SerialPortService ID
        try {
            mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
            if (!mmSocket.isConnected()){
                mmSocket.connect();
            }

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void sendCommand(String command) {
        try {
            btThread.interrupt();                       // terminate the bluetooth thread
            recentValues.clear();                       // flush the buffer

            OutputStream outputStream = mmSocket.getOutputStream();
            outputStream.write(command.getBytes());
            Log.d(TAG, "sendCommand: send shutdown command to rpi");
            stopValueBtn.setVisibility(View.GONE);          // hide the stop button
            startValueBtn.setVisibility(View.VISIBLE);      // make start button visible
        } catch (IOException e) {
            Log.d(TAG, "sendCommand: failed to send command to rpi");
            e.printStackTrace();
        }
    }

    /**
     * parseResult
     * The data transmitted from the connected bluetooth devices is in the form of a comma
     * separated string. This method splits the fields and returns a list with individual
     * parameters as elements.
     *
     * @param res The resulting data string communicated from the connected device
     * @return String[] The list with the individual parameters separated
     */
    public String[] parseResult(String res) {
        return res.split(",");
    }

    /**
     * ppbToMicroGram
     * Convert the current gas level from parts per billion to micrograms/cubic meters
     *
     * @param   gas     The current gas being measured by the connected device
     * @param   ppb     The ppb level of the gas being measured (sensor data)
     * @param   temp    Current temperature being measured by the connected sensor
     * @return  double  The gas level in micrograms/cubic meter
     */
    public double ppbToMicroGram(String gas, int ppb, int temp) {

        double M;

        switch (gas) {
            case "O3":
                M = 47.998;
                break;
            case "SO2":
                M = 64.06;
                break;
            case "NO2":
                M = 46.0055;
                break;
            case "CO":
                M = 28.011;
                break;
            case "H2S":
                M = 34.076;
                break;
            default:
                M = 0;
                break;
        }
        return ( ppb * 12.187 * M ) / ( 273.15 + temp );
    }

    /**
     * initializeMqtt
     * Make the initial connection to the mqtt broker being used to send messages to
     * using the defined broker address found in the MqttHelper class. Also, register
     * callbacks used to log the result in order to see the result in case it fails.
     */
    private void initializeMqtt() {
        mqttHelper = new MqttHelper(this);
        mqttHelper.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                Log.d(TAG, "connectComplete: mqtt connected");
            }

            @Override
            public void connectionLost(Throwable cause) {
                Log.d(TAG, "connectionLost: mqtt lost connection to broker");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                Log.d(TAG, "messageArrived: received" + message.toString());
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                Log.d(TAG, "deliveryComplete: mqtt delivered message to broker");
            }
        });
    }

    /**
     * sendData
     * When the buffer of values received from the connected sensor device is full, calculate
     * an average of the values, construct a json message and publish it to a given topic.
     *
     * @param values The buffer containing values from the connected sensor
     * @param coord The current position of the device running this application
     * @param time The time of the measurements
     * @param serialNr The serial number of the sensor used (used for unique sensor data message)
     */
    private void sendData(ArrayList<Double> values, Coordinate coord, String time, String serialNr) {
        // create JSon formatted message with all the data
        double average = 0.0;
        if (!values.isEmpty()) {
            for (double value : values) {
                average += value;
            }
            average = average/values.size();

            mqttHelper.publish(new JSonMessage(average, coord, time, serialNr).msg, "test");
        }

    }

    /**
     * checkNavigationPermissions
     * Since android needs to ask th user for permissions when using for example an internet
     * connection or locational data, check that these permissions are defined to be used in
     * the manifest of the application. If so also check if the user already has given permission
     * to the application to use these. If not prompt the user to give permissions. If given
     * then all good.
     */
    private void checkNavigationPermissions() {
        if (ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION}, 101);

        }
    }

    /**
     * onCreate
     * The method first being called when launching an activity. Put all code which should be run
     * on the start of the application here. This will then work as the "main" method of the
     * activity.
     *
     * @param savedInstanceState The bundle object containing the activity's previously saved state
     *                           if such exist, otherwise its null
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /* initialize the global variables defined earlier */
        handler = new Handler();

        sensorType      = findViewById(R.id.sensorType);
        sensorValue     = findViewById(R.id.sensorValue);
        connDevice      = findViewById(R.id.connectedDevice);
        startValueBtn   = findViewById(R.id.startValue);
        stopValueBtn    = findViewById(R.id.stopValue);
        shutdownPi      = findViewById(R.id.shutdownRPi);
        locationText    = findViewById(R.id.locationText);

        currentLocation = new Coordinate();

        /* obtain the device's bluetooth adapter */
        final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        checkNavigationPermissions();   // make sure that the appropriate permissions exist

        initializeMqtt();               // initialize the mqtt connection and connect to a broker

        getLocation();                  // register location updates when the device moves

        /* register onClick listener to the start receiving values button */
        startValueBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Log.d(TAG, "onClick: start receiving values clicked");
                // Start receiving values from sensor
                startValueBtn.setVisibility(View.GONE);         // hide the start button
                stopValueBtn.setVisibility(View.VISIBLE);       // make stop button visible
                btThread = (new Thread(new bluetoothThread())); // create bluetooth thread
                btThread.start();                               // start it

            }
        });

        /* register onClick listener to the stop receiving values button */
        stopValueBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: stop receiving values clicked");
                stopValueBtn.setVisibility(View.GONE);          // hide the stop button
                startValueBtn.setVisibility(View.VISIBLE);      // make start button visible
                try {
                    btThread.interrupt();                       // terminate the bluetooth thread
                    mmSocket.close();                           // close the rfcomm-socket
                    recentValues.clear();                       // flush the buffer
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        shutdownPi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: shutdown rpi clicked");
                sendCommand("shutdown");
            }
        });



        /* check if bluetooth is enabled on the device, of not prompt to enable it */
        if (!mBluetoothAdapter.isEnabled())
        {
            Log.d(TAG, "bluetooth is not enabled, asking to enable it");
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, 0);
        }

        /* obtain list of currently connected bluetooth devices */
        pairedDevices = mBluetoothAdapter.getBondedDevices();

        /* if no paired devices are found, prompt the user to pair one */
        if (pairedDevices.size() == 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert);
            builder.setTitle("Bluetooth device missing")
                    .setMessage("There is no connected AirPollutionPi detected, make sure it is connected in the Bluetooth menu and then press try again!")
                    .setPositiveButton("Try again", new DialogInterface.OnClickListener() {
                        /* when clicked this button will relaunch the activity and check if there is a paired device again */
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent i = getBaseContext().getPackageManager()
                                    .getLaunchIntentForPackage( getBaseContext().getPackageName() );
                            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            startActivity(i);
                        }
                    })
                    .show();
        }

        /* if paired devices are available, find the device matching the one we are looking for */
        if (pairedDevices.size() > 0)
        {
            for (BluetoothDevice device : pairedDevices)
            {
                /* the device we are after will have the following name */
                if (device.getName().equals("AirPollutionPi"))
                {
                    Log.d(TAG, "AirPollutionPi found paired to device");
                    mmDevice = device;  // This is the device we will use to connect to via rfcomm-socket
                    connDevice.setText("Connected device: " + device.getName());
                    break;  // done
                }
            }
            connDevice.setText("No connected device found");
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        this.registerReceiver(mReceiver, filter);

    }

    //The BroadcastReceiver that listens for bluetooth broadcasts
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case BluetoothDevice.ACTION_FOUND:
                    //Device found
                    break;
                case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                    //Done searching
                    break;
                case BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED:
                    //Device is about to disconnect
                    break;
                case BluetoothDevice.ACTION_ACL_DISCONNECTED:
                    //Device has disconnected
                    Toast.makeText(getApplicationContext(), "BT Device Disconnected", Toast.LENGTH_LONG).show();
                    break;
            }
        }
    };

    /**
     * getLocation
     * When called upon, this method will prompt the location service of the device to document its
     * current position.
     */
    void getLocation() {
        try {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 5, this);
        }
        catch(SecurityException e) {
            e.printStackTrace();
        }
    }

    /**
     * bluetoothThread
     * The communication between the device running this application and the bluetooth device is
     * put in a separate thread in order to be non-blocking for the other services and methods used
     * throughout the application.
     */
    private class bluetoothThread implements Runnable {
        public void run() {
            openConnection();   // open rcomm socket to bt device

            /* run as long the thread lives */
            while (!Thread.currentThread().isInterrupted()) {
                int bytesAvailable;

                try {
                    InputStream mmInputStream;                      // inputstream used to read data
                    mmInputStream = mmSocket.getInputStream();      // receive the incoming data
                    bytesAvailable = mmInputStream.available();     // check how much is available to read

                    /* if there is something to read */
                    if (bytesAvailable > 0) {
                        byte[] packetBytes = new byte[bytesAvailable];       // buffer for the incoming data
                        Log.d(TAG, "bluetoothThread: received value");
                        byte[] readBuffer = new byte[1024];                  // buffer for the final result
                        mmInputStream.read(packetBytes);                     // read the incoming data to the buffer

                        /* read one byte at a time in order to check for delimiter '!' */
                        for (int i=0;i<bytesAvailable;i++) {
                            byte b = packetBytes[i];        // current byte
                            /* check for delimiter */
                            if (b == delimiter) {
                                byte[] encodedBytes = new byte[readBufferPosition];     // as many positions as we just read until the delimiter
                                System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                final String data = new String(encodedBytes, "US-ASCII");   // decode the bytes to be readable
                                readBufferPosition = 0;
                                final String [] res = parseResult(data);    // parse the result into individual fields
                                final double level = Math.round(ppbToMicroGram(res[0], Integer.parseInt(res[1]), Integer.parseInt(res[2]))*100.0)/100.0;

                                /* use handler here to be able to change things outside the thread such as textviews etc */
                                handler.post(new Runnable() {
                                    public void run() {
                                        sensorType.setText("Connected sensor: " + res[0]);

                                        sensorValue.setText("Sensor value: " + level + " µg/m3");
                                        //sensorValue.setText("Sensor value: " + res[1] + " ppb");

                                        /*if (recentValues.size() == 5) {
                                            // at half time check the current location
                                            getLocation();
                                        }*/

                                        if (recentValues.size() == 10) {
                                            // get a more updated location before sending
                                            //getLocation();
                                            if (currentLocation.latitude != 0.0 && currentLocation.longitude != 0.0) {
                                                sendData(recentValues, currentLocation, res[3], res[4]);
                                            }
                                            recentValues.clear();
                                        }
                                        recentValues.add(level);
                                        //recentValues.add(Double.parseDouble(res[1]));
                                    }
                                });
                                break;
                            }
                            else {
                                readBuffer[readBufferPosition++] = b;
                            }
                        }
                    }
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

            }
        }
    }

    /**
     * onLocationChanged
     * Callback function called whenever the location of the user has changed.
     *
     * @param location The location object generated by the service
     */
    @Override
    public void onLocationChanged(Location location) {
        locationText.setText("Latitude: " + location.getLatitude() + "\nLongitude: " + location.getLongitude());
        currentLocation.latitude = location.getLatitude();
        currentLocation.longitude = location.getLongitude();

        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            locationText.setText(locationText.getText() + "\n"+addresses.get(0).getAddressLine(0));
        }catch(Exception e) {

        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        Toast.makeText(MainActivity.this, "Please Enable GPS and Internet", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onProviderEnabled(String provider) {}
}
