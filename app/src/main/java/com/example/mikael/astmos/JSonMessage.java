package com.example.mikael.astmos;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * JSonMessage
 * This class can be used to construct the json formatted message which is used as payload to
 * the mqtt broker. The message will have the following format:
 *  {
 *       "@iot.id"           : serialNr,
 *       "@iot.selflink"     : "http://storagemanager.linksmartcnet.se/Observations(serialNr)",
 *       "FeatureOfInterest" : {
 *           "iot.id"        : serialNr,
 *           "description"   : "description",
 *           "feature"       : {
 *               "type" : "point",
 *               "coordinates": [
 *                   latitude,
 *                   longitude
 *               ]
 *           },
 *           "Datastream": {
 *               "@iot.id": serialNr
 *           },
 *          "phenomenonTime": timestamp,
 *           "resultTime"   : timestamp,
 *           "result"       : {
 *               "Value": sensorValue
 *           }
 *       }
 *   }
 *
 * @author      Mikael Mölder
 * @version     1.0
 * @since       2018-04-16
 */
public class JSonMessage {

    JSONObject msg;

    /**
     * JSonMessage
     * Constructor for the class which creates a JSONObject with the defined structure which can
     * later be obtained via the msg field.
     *
     * @param value     The average sensor value fo the last X measurements
     * @param coord     The location of the measurements
     * @param time      The time of the measurements
     * @param serialNr  The unique serial number of the sensor used which is used to identify who measured
     */
    public JSonMessage(double value, Coordinate coord, String time, String serialNr) {
        JSONObject json = new JSONObject();
        try {
            json.put("@iot.id", serialNr);
            json.put("@iot.selflink", "http://storagemanager.linksmartcnet.se/Observations(" + serialNr + ")");
            JSONObject ds = new JSONObject()
                    .put("@iot.id", serialNr);
            JSONArray coords = new JSONArray()
                    .put(coord.latitude)
                    .put(coord.longitude);
            JSONObject feature = new JSONObject()
                    .put("type", "point")
                    .put("coordinates", coords);
            JSONObject res = new JSONObject()
                    .put("Value", value);
            JSONObject foi = new JSONObject()
                    .put("iot.id", serialNr)
                    .put("description", "description")
                    .put("feature", feature)
                    .put("DataStream", ds)
                    .put("phenomenonTime", time)
                    .put("resultTime", new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(new Date()))
                    .put("result", res);
            json.put("FeatureOfInterest", foi);
        } catch (JSONException ex) {
            ex.printStackTrace();
        }
        msg = json;
    }
}
