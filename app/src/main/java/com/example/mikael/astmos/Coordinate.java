package com.example.mikael.astmos;

/**
 * Coordinate
 * An object used for defining the users location as latitude, longitude
 *
 * @author      Mikael Mölder
 * @version     1.0
 * @since       2018-04-16
 */
public class Coordinate {
    double latitude;
    double longitude;

    /**
     * Coordinate
     * Constructor used to create an empty coordinate object to be populated later, defaults to 0,0
     */
    public Coordinate() {
        latitude = 0.0;
        longitude = 0.0;
    }

    /**
     * Coordinate
     * Constructor to create a coordinate object with pre-defined lat and lon
     *
     * @param lat The latitude of the coordinate
     * @param lon The longitude of the coordinate
     */
    public Coordinate(double lat, double lon) {
        latitude = lat;
        longitude = lon;
    }
}
