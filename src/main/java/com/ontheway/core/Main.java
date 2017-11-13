package com.ontheway.core;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.ParseException;

import java.io.IOException;
import java.util.Map;
import java.util.Scanner;

public class Main {

    void startRepl() throws ConfigurationException, IOException {
        System.out.print("\n\nStarting " + this.getClass().getName() + " ..... ");
        long start = System.currentTimeMillis();

        Conf.get().load();

        Geocoder geocoder;
        try {
            geocoder = new Geocoder();
        } catch (IOException e) {
            System.out.println("Could not start geocoder");
            e.printStackTrace();
            return;
        }

        Intersector intersector;
        try {
             intersector = new Intersector(Conf.get().getString("index.location"));
        } catch (IOException e) {
            System.out.println("Could not start geocoder");
            e.printStackTrace();
            return;
        }

        System.out.println(String.format(" Done (%d millis).", System.currentTimeMillis() - start));
        System.out.println("Search for different places (:q to quit, :h for help)");
        while (true) {
            System.out.print("ontheway> ");
            Scanner scanner = new Scanner(System.in);
            String line = scanner.nextLine().trim();

            if (line.isEmpty()) {
                continue;
            } else if (line.equals(":q")) {
                break;
            } else if (line.equals(":h")) {
                System.out.println("Available Options: ");
                System.out.println("  :q -> quit this application");
                System.out.println("  :h -> print this message");
                System.out.println("  geocode [address] -> geocode a given address");
                System.out.println("  near [keyword] [lat,long] -> search for a keyword near an lat,lng.");
                System.out.println();
            }

            if (line.startsWith("geocode ")) {
                System.out.println(geocoder.geocodeToLatLng(line.substring(7)));
            } else if (line.startsWith("near")) {
                String[] parts = line.split("\\s+");
                if (parts.length <= 3) {
                    System.out.println("Error: Bad Params");
                    continue;
                }

                double lat = Double.parseDouble(parts[1]);
                double lon = Double.parseDouble(parts[2]);
                StringBuilder keywords = new StringBuilder();
                for (int i=3; i<parts.length; i++) keywords.append(parts[i]).append(" ");
                try {
                    Map<String, Document> geo = intersector.intersectingSearch(lat, lon, keywords.toString().trim());
                    geo.entrySet().stream().map(Map.Entry::getValue)
                            .map(Main::clean)
                            .forEach(System.out::println);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            } else if (line.startsWith("set")) {
                String[] parts = line.split("\\s+");
                if (parts.length != 3) {
                    System.out.println("Error: Bad Params");
                    continue;
                }

                switch (parts[1].toLowerCase()) {
                    case "perimeter": Vars.PERIMETER = Integer.parseInt(parts[2]); break;
                    default: System.out.println("Bad var: " + parts[1]);
                }
            } else if (line.startsWith("get")) {
                String[] parts = line.split("\\s+");
                if (parts.length != 2) {
                    System.out.println("Error: Bad Params");
                    continue;
                }

                switch (parts[1].toLowerCase()) {
                    case "perimeter": System.out.println("Vars.Perimeter=" + Vars.PERIMETER); break;
                    default: System.out.println("Bad var: " + parts[1]);
                }
            }
        }
    }

    static String clean(Document e) {
        String desc = e.getField("description").stringValue();
        if (desc.length() > 200) desc = desc.substring(0, 200);
        String coordinates = e.getField("coordinates_text").stringValue();
        coordinates = coordinates.substring(coordinates.indexOf(' '));
        return coordinates + " " + desc;
    }

    public static void main(String[] args) throws Exception {
        new Main().startRepl();
    }

}