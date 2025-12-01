package com.mio.util;

import com.mio.model.*;
import java.io.*;
import java.util.*;

public class CSVReader {

    public static List<Route> readRoutes(String filePath) throws IOException {
        List<Route> routes = new ArrayList<>();
        BufferedReader br = new BufferedReader(new FileReader(filePath));
        
        String line = br.readLine(); 
        
        while ((line = br.readLine()) != null) {
            String[] values = parseCsvLine(line);
            if (values.length >= 6) {
                try {
                    int lineId = Integer.parseInt(values[0].trim());
                    int planVersionId = Integer.parseInt(values[1].trim());
                    String shortName = values[2].trim();
                    String description = values[3].trim();
                    String activationDate = values[5].trim(); 
                    
                    routes.add(new Route(lineId, planVersionId, shortName, description, activationDate));
                } catch (NumberFormatException e) {
                    System.err.println("Error parseando línea de ruta: " + line);
                }
            }
        }
        
        br.close();
        return routes;
    }

    public static Map<Integer, Stop> readStops(String filePath) throws IOException {
        Map<Integer, Stop> stops = new HashMap<>();
        BufferedReader br = new BufferedReader(new FileReader(filePath));
        
        String line = br.readLine(); 
        
        while ((line = br.readLine()) != null) {
            String[] values = parseCsvLine(line);
            if (values.length >= 8) {
                try {
                    int stopId = Integer.parseInt(values[0].trim());
                    int planVersionId = Integer.parseInt(values[1].trim());
                    String shortName = values[2].trim();
                    String longName = values[3].trim();
                    long gpsX = Long.parseLong(values[4].trim());
                    long gpsY = Long.parseLong(values[5].trim());
                    double decimalLong = Double.parseDouble(values[6].trim());
                    double decimalLatit = Double.parseDouble(values[7].trim());
                    
                    stops.put(stopId, new Stop(stopId, planVersionId, shortName, longName,
                            gpsX, gpsY, decimalLong, decimalLatit));
                } catch (NumberFormatException e) {
                    System.err.println("Error parseando línea de parada: " + line);
                }
            }
        }
        
        br.close();
        return stops;
    }

    public static List<LineStop> readLineStops(String filePath) throws IOException {
        List<LineStop> lineStops = new ArrayList<>();
        BufferedReader br = new BufferedReader(new FileReader(filePath));
        
        String line = br.readLine(); 
        
        while ((line = br.readLine()) != null) {
            String[] values = parseCsvLine(line);
            if (values.length >= 9) {
                try {
                    int lineStopId = Integer.parseInt(values[0].trim());
                    int stopSequence = Integer.parseInt(values[1].trim());
                    int orientation = Integer.parseInt(values[2].trim());
                    int lineId = Integer.parseInt(values[3].trim());
                    int stopId = Integer.parseInt(values[4].trim());
                    int planVersionId = Integer.parseInt(values[5].trim());
                    int lineVariant = Integer.parseInt(values[6].trim());
                    int lineVariantType = Integer.parseInt(values[8].trim());
                    
                    lineStops.add(new LineStop(lineStopId, stopSequence, orientation, lineId,
                            stopId, planVersionId, lineVariant, lineVariantType));
                } catch (NumberFormatException e) {
                    System.err.println("Error parseando línea de linestop: " + line);
                }
            }
        }
        
        br.close();
        return lineStops;
    }

    private static String[] parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentValue = new StringBuilder();
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                values.add(currentValue.toString());
                currentValue = new StringBuilder();
            } else {
                currentValue.append(c);
            }
        }
        
        values.add(currentValue.toString());
        
        return values.toArray(new String[0]);
    }
}
