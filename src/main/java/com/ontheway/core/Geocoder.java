package com.ontheway.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.model.GeocodingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class Geocoder {

    static Logger logger = LoggerFactory.getLogger(Geocoder.class);

    final GeoApiContext context;

    public Geocoder() throws IOException {
        context = new GeoApiContext.Builder()
                .apiKey(Utils.readAll(Conf.get().getString("gkfile.location")))
                .build();
    }

    public String geocodeToLatLng(String address) {
        try {
            GeocodingResult[] results = GeocodingApi.geocode(context, address)
                    .await();
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            return gson.toJson(results[0].geometry.location);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}