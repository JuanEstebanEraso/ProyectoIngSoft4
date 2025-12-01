package com.mio;

import com.mio.model.*;
import com.mio.util.*;
import java.util.*;
import java.io.*;

/**
 * Clase principal que construye y muestra el grafo de arcos del SITM-MIO
 */
public class Main {
    
    public static void main(String[] args) {
        try {
            System.out.println("Iniciando construcción del grafo de arcos del SITM-MIO...\n");
            
            // Rutas de los archivos CSV
            String linesPath = "/home/swarch/proyecto-mio/MIO/lines-241.csv";
            String stopsPath = "/home/swarch/proyecto-mio/MIO/stops-241.csv";
            String lineStopsPath = "/home/swarch/proyecto-mio/MIO/linestops-241.csv";
            
            // Leer los archivos CSV
            System.out.println("Leyendo archivos CSV...");
            List<Route> routes = CSVReader.readRoutes(linesPath);
            System.out.printf("  - Rutas leídas: %d%n", routes.size());
            
            Map<Integer, Stop> stops = CSVReader.readStops(stopsPath);
            System.out.printf("  - Paradas leídas: %d%n", stops.size());
            
            List<LineStop> lineStops = CSVReader.readLineStops(lineStopsPath);
            System.out.printf("  - Paradas por ruta leídas: %d%n", lineStops.size());
            
            System.out.println("\nConstruyendo grafo de arcos...");
            
            // Construir el grafo
            GraphBuilder graphBuilder = new GraphBuilder(routes, stops, lineStops);
            graphBuilder.buildGraph();
            
            System.out.printf("  - Arcos construidos: %d%n%n", graphBuilder.getArcs().size());
            
            // Crear archivo de salida
            String outputPath = "output/grafo_arcos_mio.txt";
            new File("output").mkdirs();
            
            // Mostrar los arcos ordenados por ruta y secuencia
            graphBuilder.printArcs();
            
            // Mostrar estadísticas adicionales
            graphBuilder.printStatistics();
            
            // Exportar a archivo
            System.out.println("\n" + "=".repeat(80));
            System.out.println("Exportando resultados a archivo...");
            graphBuilder.exportToFile(outputPath);
            System.out.printf("Archivo generado exitosamente: %s%n", outputPath);
            System.out.println("=".repeat(80));
            
            System.out.println("\n¡Proceso completado exitosamente!");
            
        } catch (Exception e) {
            System.err.println("Error durante la ejecución: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

