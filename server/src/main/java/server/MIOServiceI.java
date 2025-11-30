package server;

import com.zeroc.Ice.*;
import com.mio.model.*;
import com.mio.util.*;
import MIO.*;
import java.util.*;

/**
 * Implementación del servicio MIO usando Ice
 */
public class MIOServiceI implements MIOService {
    
    private Map<Integer, com.mio.model.Stop> stops;
    private List<com.mio.model.Route> routes;
    private List<com.mio.model.Arc> arcs;
    private GraphBuilder graphBuilder;
    private boolean isGraphBuilt = false;
    
    // Referencia al Master para pasarle las paradas
    private MasterI master;
    
    public MIOServiceI() {
        this.stops = new HashMap<>();
        this.routes = new ArrayList<>();
        this.arcs = new ArrayList<>();
    }
    
    public void setMaster(MasterI master) {
        this.master = master;
    }
    
    @Override
    public void buildGraph(Current current) {
        try {
            System.out.println("Construyendo grafo del SITM-MIO...");
            
            // Rutas de los archivos CSV
            String linesPath = "data/lines-241.csv";
            String stopsPath = "data/stops-241.csv";
            String lineStopsPath = "data/linestops-241.csv";
            
            // Leer los archivos CSV
            this.routes = CSVReader.readRoutes(linesPath);
            System.out.printf("  - Rutas leidas: %d%n", routes.size());
            
            this.stops = CSVReader.readStops(stopsPath);
            System.out.printf("  - Paradas leidas: %d%n", stops.size());
            
            List<LineStop> lineStops = CSVReader.readLineStops(lineStopsPath);
            System.out.printf("  - Paradas por ruta leidas: %d%n", lineStops.size());
            
            // Construir el grafo
            this.graphBuilder = new GraphBuilder(routes, stops, lineStops);
            this.graphBuilder.buildGraph();
            this.arcs = graphBuilder.getArcs();
            
            System.out.printf("  - Arcos construidos: %d%n", arcs.size());
            this.isGraphBuilt = true;
            
            // Pasar las paradas al Master para que pueda determinar si un bus está en una parada
            if (master != null) {
                StopInfo[] stopInfos = new StopInfo[stops.size()];
                int i = 0;
                for (com.mio.model.Stop stop : stops.values()) {
                    stopInfos[i++] = convertStop(stop);
                }
                master.setStops(stopInfos);
            }
            
        } catch (java.lang.Exception e) {
            System.err.println("Error construyendo el grafo: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error construyendo el grafo: " + e.getMessage());
        }
    }
    
    @Override
    public StopInfo getStop(int stopId, Current current) {
        checkGraphBuilt();
        
        com.mio.model.Stop stop = stops.get(stopId);
        if (stop == null) {
            throw new RuntimeException("Parada no encontrada: " + stopId);
        }
        
        return convertStop(stop);
    }
    
    @Override
    public StopInfo[] getAllStops(Current current) {
        checkGraphBuilt();
        
        StopInfo[] result = new StopInfo[stops.size()];
        int i = 0;
        for (com.mio.model.Stop stop : stops.values()) {
            result[i++] = convertStop(stop);
        }
        return result;
    }
    
    @Override
    public RouteInfo getRoute(int routeId, Current current) {
        checkGraphBuilt();
        
        for (com.mio.model.Route route : routes) {
            if (route.getLineId() == routeId) {
                return convertRoute(route);
            }
        }
        throw new RuntimeException("Ruta no encontrada: " + routeId);
    }
    
    @Override
    public RouteInfo[] getAllRoutes(Current current) {
        checkGraphBuilt();
        
        RouteInfo[] result = new RouteInfo[routes.size()];
        for (int i = 0; i < routes.size(); i++) {
            result[i] = convertRoute(routes.get(i));
        }
        return result;
    }
    
    @Override
    public ArcInfo[] getAllArcs(Current current) {
        checkGraphBuilt();
        
        ArcInfo[] result = new ArcInfo[arcs.size()];
        for (int i = 0; i < arcs.size(); i++) {
            result[i] = convertArc(arcs.get(i));
        }
        return result;
    }
    
    @Override
    public ArcInfo[] getArcsByRoute(int routeId, int orientation, Current current) {
        checkGraphBuilt();
        
        List<ArcInfo> filtered = new ArrayList<>();
        for (com.mio.model.Arc arc : arcs) {
            if (arc.getLineId() == routeId && arc.getOrientation() == orientation) {
                filtered.add(convertArc(arc));
            }
        }
        
        return filtered.toArray(new ArcInfo[0]);
    }
    
    @Override
    public String getStatistics(Current current) {
        checkGraphBuilt();
        
        StringBuilder stats = new StringBuilder();
        stats.append("=== Estadisticas del Sistema MIO ===\n");
        stats.append(String.format("Total de paradas: %d\n", stops.size()));
        stats.append(String.format("Total de rutas: %d\n", routes.size()));
        stats.append(String.format("Total de arcos: %d\n", arcs.size()));
        
        // Calcular arcos por orientación
        long arcosIda = arcs.stream()
            .filter(a -> a.getOrientation() == 0)
            .count();
        long arcosVuelta = arcs.stream()
            .filter(a -> a.getOrientation() == 1)
            .count();
            
        stats.append(String.format("Arcos IDA: %d\n", arcosIda));
        stats.append(String.format("Arcos VUELTA: %d\n", arcosVuelta));
        
        return stats.toString();
    }
    
    // Métodos auxiliares de conversión
    private void checkGraphBuilt() {
        if (!isGraphBuilt) {
            throw new RuntimeException("El grafo no ha sido construido. Llame a buildGraph() primero.");
        }
    }
    
    private StopInfo convertStop(com.mio.model.Stop stop) {
        StopInfo info = new StopInfo();
        info.stopId = stop.getStopId();
        info.stopName = stop.getLongName();
        info.latitude = stop.getDecimalLatit();
        info.longitude = stop.getDecimalLong();
        return info;
    }
    
    private RouteInfo convertRoute(com.mio.model.Route route) {
        RouteInfo info = new RouteInfo();
        info.routeId = route.getLineId();
        info.routeName = route.getShortName();
        info.variant = route.getDescription();
        info.orientation = 0; // La clase Route no tiene orientación
        return info;
    }
    
    private ArcInfo convertArc(com.mio.model.Arc arc) {
        ArcInfo info = new ArcInfo();
        info.fromStopId = arc.getFromStopId();
        info.toStopId = arc.getToStopId();
        info.routeId = arc.getLineId();
        info.routeName = ""; // No disponible directamente
        info.orientation = arc.getOrientation();
        info.sequenceOrder = arc.getSequenceFrom();
        return info;
    }
}
