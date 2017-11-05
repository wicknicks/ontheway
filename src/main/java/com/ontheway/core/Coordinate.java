package com.ontheway.core;

class Coordinate {
    protected final double latitude;
    protected final double longitude;


    Coordinate(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    @Override
    public String toString() {
        return "Coordinate (" + latitude + ", " + longitude + ')';
    }
}
