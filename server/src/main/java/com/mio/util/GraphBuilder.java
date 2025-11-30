package com.mio.util;

import com.mio.model.*;
import java.util.*;
import java.io.*;


public class GraphBuilder {
    
    private List<Route> routes;
    private Map<Integer, Stop> stops;
    private List<LineStop> lineStops;
    private List<Arc> arcs;

    public GraphBuilder(List<Route> routes, Map<Integer, Stop> stops, List<LineStop> lineStops) {
        this.routes = routes;
        this.stops = stops;
        this.lineStops = lineStops;
        this.arcs = new ArrayList<>();
    }


    public void buildGraph() {
   
        Map<String, List<LineStop>> groupedStops = groupLineStops();
        
        for (Map.Entry<String, List<LineStop>> entry : groupedStops.entrySet()) {
            List<LineStop> orderedStops = entry.getValue();
            
            Collections.sort(orderedStops);
            
            for (int i = 0; i < orderedStops.size() - 1; i++) {
                LineStop current = orderedStops.get(i);
                LineStop next = orderedStops.get(i + 1);
                
                Arc arc = new Arc(
                    current.getLineId(),
                    current.getOrientation(),
                    current.getLineVariant(),
                    current.getStopId(),
                    next.getStopId(),
                    current.getStopSequence(),
                    next.getStopSequence()
                );
                
                arcs.add(arc);
            }
        }
    }

    private Map<String, List<LineStop>> groupLineStops() {
        Map<String, List<LineStop>> grouped = new HashMap<>();
        
        for (LineStop ls : lineStops) {
            String key = ls.getLineId() + "_" + ls.getOrientation() + "_" + ls.getLineVariant();
            
            if (!grouped.containsKey(key)) {
                grouped.put(key, new ArrayList<>());
            }
            
            grouped.get(key).add(ls);
        }
        
        return grouped;
    }

    public List<Arc> getArcs() {
        return arcs;
    }

   
    public Map<Integer, Map<Integer, Map<Integer, List<Arc>>>> getArcsByRouteAndVariant() {
        Map<Integer, Map<Integer, Map<Integer, List<Arc>>>> arcsByRoute = new TreeMap<>();
        
        for (Arc arc : arcs) {
            int lineId = arc.getLineId();
            int orientation = arc.getOrientation();
            int variant = arc.getLineVariant();
            
            if (!arcsByRoute.containsKey(lineId)) {
                arcsByRoute.put(lineId, new TreeMap<>());
            }
            
            if (!arcsByRoute.get(lineId).containsKey(orientation)) {
                arcsByRoute.get(lineId).put(orientation, new TreeMap<>());
            }
            
            if (!arcsByRoute.get(lineId).get(orientation).containsKey(variant)) {
                arcsByRoute.get(lineId).get(orientation).put(variant, new ArrayList<>());
            }
            
            arcsByRoute.get(lineId).get(orientation).get(variant).add(arc);
        }
        
        return arcsByRoute;
    }

    
    public void printArcs() {
        Map<Integer, Map<Integer, Map<Integer, List<Arc>>>> arcsByRoute = getArcsByRouteAndVariant();
        
        System.out.println("=".repeat(80));
        System.out.println("GRAFO DE ARCOS DEL SITM-MIO");
        System.out.println("=".repeat(80));
        System.out.println();
        
        int totalArcs = 0;
        int totalVariants = 0;
        
        for (Map.Entry<Integer, Map<Integer, Map<Integer, List<Arc>>>> routeEntry : arcsByRoute.entrySet()) {
            int lineId = routeEntry.getKey();
            Route route = findRouteById(lineId);
            
            if (route != null) {
                System.out.println("-".repeat(80));
                System.out.printf("RUTA: %s - %s (ID: %d)%n", 
                    route.getShortName(), route.getDescription(), lineId);
                System.out.println("-".repeat(80));
            } else {
                System.out.println("-".repeat(80));
                System.out.printf("RUTA ID: %d%n", lineId);
                System.out.println("-".repeat(80));
            }
            
            for (Map.Entry<Integer, Map<Integer, List<Arc>>> orientEntry : routeEntry.getValue().entrySet()) {
                int orientation = orientEntry.getKey();
                String orientLabel = orientation == 0 ? "IDA" : "VUELTA";
                
                for (Map.Entry<Integer, List<Arc>> variantEntry : orientEntry.getValue().entrySet()) {
                    int variant = variantEntry.getKey();
                    List<Arc> routeArcs = variantEntry.getValue();
                    
                    System.out.printf("%n  [%s - Variante %d] - %d arcos%n", orientLabel, variant, routeArcs.size());
                    System.out.println("  " + "-".repeat(76));
                    
                    for (Arc arc : routeArcs) {
                        Stop fromStop = stops.get(arc.getFromStopId());
                        Stop toStop = stops.get(arc.getToStopId());
                        
                        String fromStopName = fromStop != null ? fromStop.getLongName() : "Desconocida";
                        String toStopName = toStop != null ? toStop.getLongName() : "Desconocida";
                        
                        System.out.printf("  Seq %2d->%2d: Parada %d (%s) --> Parada %d (%s)%n",
                            arc.getSequenceFrom(), arc.getSequenceTo(),
                            arc.getFromStopId(), truncate(fromStopName, 20),
                            arc.getToStopId(), truncate(toStopName, 20));
                        
                        totalArcs++;
                    }
                    totalVariants++;
                }
            }
            
            System.out.println();
        }
        
        System.out.println("=".repeat(80));
        System.out.printf("RESUMEN:%n");
        System.out.printf("  - Total de rutas: %d%n", arcsByRoute.size());
        System.out.printf("  - Total de variantes: %d%n", totalVariants);
        System.out.printf("  - Total de arcos: %d%n", totalArcs);
        System.out.printf("  - Total de paradas únicas: %d%n", stops.size());
        System.out.println("=".repeat(80));
    }

   
    private Route findRouteById(int lineId) {
        for (Route route : routes) {
            if (route.getLineId() == lineId) {
                return route;
            }
        }
        return null;
    }

    
    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }


    public void printStatistics() {
        Map<Integer, Map<Integer, Map<Integer, List<Arc>>>> arcsByRoute = getArcsByRouteAndVariant();
        
        System.out.println("\nESTADISTICAS DETALLADAS:");
        System.out.println("-".repeat(80));
        
        for (Map.Entry<Integer, Map<Integer, Map<Integer, List<Arc>>>> routeEntry : arcsByRoute.entrySet()) {
            int lineId = routeEntry.getKey();
            Route route = findRouteById(lineId);
            
            int totalArcosRuta = 0;
            int arcosIda = 0;
            int arcosVuelta = 0;
            
            for (Map.Entry<Integer, Map<Integer, List<Arc>>> orientEntry : routeEntry.getValue().entrySet()) {
                int orientation = orientEntry.getKey();
                for (List<Arc> variantArcs : orientEntry.getValue().values()) {
                    int arcsCount = variantArcs.size();
                    totalArcosRuta += arcsCount;
                    if (orientation == 0) {
                        arcosIda += arcsCount;
                    } else {
                        arcosVuelta += arcsCount;
                    }
                }
            }
            
            if (route != null) {
                System.out.printf("Ruta %s: %d arcos (Ida: %d, Vuelta: %d)%n",
                    route.getShortName(), totalArcosRuta, arcosIda, arcosVuelta);
            }
        }
    }

    
    public void exportToFile(String filePath) throws IOException {
        PrintWriter writer = new PrintWriter(new FileWriter(filePath));
        Map<Integer, Map<Integer, Map<Integer, List<Arc>>>> arcsByRoute = getArcsByRouteAndVariant();
        
        writer.println("=".repeat(80));
        writer.println("GRAFO DE ARCOS DEL SITM-MIO");
        writer.println("=".repeat(80));
        writer.println();
        
        int totalArcs = 0;
        int totalVariants = 0;
        
        for (Map.Entry<Integer, Map<Integer, Map<Integer, List<Arc>>>> routeEntry : arcsByRoute.entrySet()) {
            int lineId = routeEntry.getKey();
            Route route = findRouteById(lineId);
            
            if (route != null) {
                writer.println("-".repeat(80));
                writer.printf("RUTA: %s - %s (ID: %d)%n", 
                    route.getShortName(), route.getDescription(), lineId);
                writer.println("-".repeat(80));
            } else {
                writer.println("-".repeat(80));
                writer.printf("RUTA ID: %d%n", lineId);
                writer.println("-".repeat(80));
            }
            
            for (Map.Entry<Integer, Map<Integer, List<Arc>>> orientEntry : routeEntry.getValue().entrySet()) {
                int orientation = orientEntry.getKey();
                String orientLabel = orientation == 0 ? "IDA" : "VUELTA";
                
                for (Map.Entry<Integer, List<Arc>> variantEntry : orientEntry.getValue().entrySet()) {
                    int variant = variantEntry.getKey();
                    List<Arc> routeArcs = variantEntry.getValue();
                    
                    writer.printf("%n  [%s - Variante %d] - %d arcos%n", orientLabel, variant, routeArcs.size());
                    writer.println("  " + "-".repeat(76));
                    
                    for (Arc arc : routeArcs) {
                        Stop fromStop = stops.get(arc.getFromStopId());
                        Stop toStop = stops.get(arc.getToStopId());
                        
                        String fromStopName = fromStop != null ? fromStop.getLongName() : "Desconocida";
                        String toStopName = toStop != null ? toStop.getLongName() : "Desconocida";
                        
                        writer.printf("  Seq %2d->%2d: Parada %d (%s) --> Parada %d (%s)%n",
                            arc.getSequenceFrom(), arc.getSequenceTo(),
                            arc.getFromStopId(), truncate(fromStopName, 20),
                            arc.getToStopId(), truncate(toStopName, 20));
                        
                        totalArcs++;
                    }
                    totalVariants++;
                }
            }
            
            writer.println();
        }
        
        writer.println("=".repeat(80));
        writer.printf("RESUMEN:%n");
        writer.printf("  - Total de rutas: %d%n", arcsByRoute.size());
        writer.printf("  - Total de variantes: %d%n", totalVariants);
        writer.printf("  - Total de arcos: %d%n", totalArcs);
        writer.printf("  - Total de paradas únicas: %d%n", stops.size());
        writer.println("=".repeat(80));
        
        writer.println();
        writer.println("ESTADISTICAS DETALLADAS:");
        writer.println("-".repeat(80));
        
        for (Map.Entry<Integer, Map<Integer, Map<Integer, List<Arc>>>> routeEntry : arcsByRoute.entrySet()) {
            int lineId = routeEntry.getKey();
            Route route = findRouteById(lineId);
            
            int totalArcosRuta = 0;
            int arcosIda = 0;
            int arcosVuelta = 0;
            
            for (Map.Entry<Integer, Map<Integer, List<Arc>>> orientEntry : routeEntry.getValue().entrySet()) {
                int orientation = orientEntry.getKey();
                for (List<Arc> variantArcs : orientEntry.getValue().values()) {
                    int arcsCount = variantArcs.size();
                    totalArcosRuta += arcsCount;
                    if (orientation == 0) {
                        arcosIda += arcsCount;
                    } else {
                        arcosVuelta += arcsCount;
                    }
                }
            }
            
            if (route != null) {
                writer.printf("Ruta %s: %d arcos (Ida: %d, Vuelta: %d)%n",
                    route.getShortName(), totalArcosRuta, arcosIda, arcosVuelta);
            }
        }
        
        writer.close();
    }
}
