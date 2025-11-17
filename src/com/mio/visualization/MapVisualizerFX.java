package com.mio.visualization;

import com.mio.model.Arc;
import com.mio.model.Stop;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.embed.swing.SwingFXUtils;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.List;
import java.util.Map;


public class MapVisualizerFX extends Application {
    
    private static Map<Integer, Stop> stops;
    private static List<Arc> arcs;
    
    private static final int WIDTH = 2304;
    private static final int HEIGHT = 1792;
 
    private double minLat = Double.POSITIVE_INFINITY;
    private double maxLat = Double.NEGATIVE_INFINITY;
    private double minLon = Double.POSITIVE_INFINITY;
    private double maxLon = Double.NEGATIVE_INFINITY;
    

    private int mapZoom = 13;
    private int mapCenterTileX;
    private int mapCenterTileY;
    private int mapGridWidth = 9;
    private int mapGridHeight = 7;
    
    private static final Color COLOR_IDA = Color.rgb(0, 100, 255, 0.9);
    private static final Color COLOR_VUELTA = Color.rgb(255, 50, 50, 0.9);
    private static final Color COLOR_STOP = Color.rgb(255, 215, 0);
    private static final Color COLOR_STOP_BORDER = Color.BLACK;
    
    private Image mapImage = null;
    
    public static void setData(Map<Integer, Stop> stopsData, List<Arc> arcsData) {
        stops = stopsData;
        arcs = arcsData;
    }
    
    @Override
    public void start(Stage primaryStage) {
        calculateBounds();
        
        // Descargar mapa real de Cali
        System.out.println("\nDescargando mapa real de Cali desde OpenStreetMap...");
        mapImage = downloadCaliMap();
        
        Canvas canvas = new Canvas(WIDTH, HEIGHT);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        
        // Dibujar mapa de fondo real
        if (mapImage != null) {
            System.out.println("✓ Mapa descargado, dibujando sobre mapa real de Cali");
            // Dibujar imagen sin escalar (resolución nativa 1:1)
            gc.drawImage(mapImage, 0, 0);
            System.out.println("  Mapa dibujado a resolución nativa: " + (int)mapImage.getWidth() + "x" + (int)mapImage.getHeight());
            gc.setFill(Color.rgb(255, 255, 255, 0.15));
            gc.fillRect(0, 0, WIDTH, HEIGHT);
        } else {
            System.out.println("⚠ No se pudo descargar el mapa, usando fondo simple");
            drawSimpleBackground(gc);
        }
        
        // Dibujar arcos
        System.out.println("Dibujando " + arcs.size() + " arcos sobre el mapa...");
        drawArcs(gc);
        
        // Dibujar paradas
        System.out.println("Dibujando " + stops.size() + " paradas sobre el mapa...");
        drawStops(gc);

        drawLegend(gc);
        
        ScrollPane scrollPane = new ScrollPane();
        StackPane root = new StackPane(canvas);
        scrollPane.setContent(root);
        scrollPane.setPannable(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        
        Scene scene = new Scene(scrollPane, 1400, 1000);
        primaryStage.setTitle("Sistema MIO - Mapa Real de Cali con Grafo de Rutas");
        primaryStage.setScene(scene);
        primaryStage.show();
        
        System.out.println("\n✓ Visualización JavaFX iniciada exitosamente");
        System.out.println("  Usa el scroll para navegar por el mapa de Cali");
    }
    

    private Image downloadCaliMap() {
        Image staticMap = downloadFromStaticAPI();
        if (staticMap != null) {
            return staticMap;
        }
        
        System.out.println("  Intentando con servidor alternativo...");
        return downloadFromTileServer();
    }
    
    private Image downloadFromStaticAPI() {
        try {
            double centerLat = (minLat + maxLat) / 2.0;
            double centerLon = (minLon + maxLon) / 2.0;
            int zoom = calculateZoomLevel();
            
            System.out.println("  Centro del mapa: (" + String.format("%.6f", centerLat) + 
                             ", " + String.format("%.6f", centerLon) + ")");
            System.out.println("  Nivel de zoom: " + zoom);
            System.out.println("  Dimensiones: " + WIDTH + "x" + HEIGHT + " píxeles");
            
            int mapWidth = Math.min(WIDTH, 1800);
            int mapHeight = Math.min(HEIGHT, 1500);
            
            String mapUrl = String.format(
                "https://staticmap.openstreetmap.de/staticmap.php?center=%.6f,%.6f&zoom=%d&size=%dx%d&maptype=mapnik",
                centerLat, centerLon, zoom, mapWidth, mapHeight
            );
            
            System.out.println("  Descargando desde: " + mapUrl);
            System.out.println("  (Esto puede tardar unos segundos...)");
            
            URL url = new URL(mapUrl);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "JavaFX-MIO-Visualizer/1.0");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            
            BufferedImage bufferedImage = ImageIO.read(connection.getInputStream());
            
            if (bufferedImage != null) {
                return SwingFXUtils.toFXImage(bufferedImage, null);
            }
            
        } catch (Exception e) {
            System.err.println("  ✗ Error en servicio estático: " + e.getMessage());
        }
        
        return null;
    }
    
    private Image downloadFromTileServer() {
        try {
            double centerLat = (minLat + maxLat) / 2.0;
            double centerLon = (minLon + maxLon) / 2.0;
            
            // Convertir coordenadas geográficas a tiles
            int[] centerTile = latLonToTile(centerLat, centerLon, mapZoom);
            mapCenterTileX = centerTile[0];
            mapCenterTileY = centerTile[1];
            
            // Descargar 9x7 tiles para cubrir el área del canvas (2400x2000)
            int tileSize = 256;
            BufferedImage mapImage = new BufferedImage(
                tileSize * mapGridWidth, tileSize * mapGridHeight, BufferedImage.TYPE_INT_RGB
            );
            Graphics2D g = mapImage.createGraphics();
            
            System.out.println("  Descargando tiles desde tile.openstreetmap.org...");
            System.out.println("  Centro: tile(" + mapCenterTileX + "," + mapCenterTileY + ") zoom=" + mapZoom);
            System.out.println("  Grid: " + mapGridWidth + "x" + mapGridHeight + " tiles");
            
            int offsetX = mapGridWidth / 2;
            int offsetY = mapGridHeight / 2;
            
            for (int dy = -offsetY; dy <= offsetY; dy++) {
                for (int dx = -offsetX; dx <= offsetX; dx++) {
                    try {
                        int tx = mapCenterTileX + dx;
                        int ty = mapCenterTileY + dy;
                        
                        String tileUrl = String.format(
                            "https://tile.openstreetmap.org/%d/%d/%d.png",
                            mapZoom, tx, ty
                        );
                        
                        URL url = new URL(tileUrl);
                        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                        connection.setRequestProperty("User-Agent", "JavaFX-MIO-Visualizer/1.0");
                        connection.setConnectTimeout(5000);
                        connection.setReadTimeout(5000);
                        
                        BufferedImage tile = ImageIO.read(connection.getInputStream());
                        
                        if (tile != null) {
                            int x = (dx + offsetX) * tileSize;
                            int y = (dy + offsetY) * tileSize;
                            g.drawImage(tile, x, y, null);
                            System.out.print(".");
                        }
                        
                        // Pequeña pausa para no sobrecargar el servidor
                        Thread.sleep(100);
                        
                    } catch (Exception e) {
                        System.out.print("x");
                    }
                }
            }
            System.out.println();
            
            g.dispose();
            
            System.out.println("  ✓ Mapa ensamblado exitosamente");
            System.out.println("  Dimensiones del mapa: " + mapImage.getWidth() + "x" + mapImage.getHeight() + " píxeles");
            System.out.println("  Tile superior-izquierdo: (" + (mapCenterTileX - mapGridWidth/2) + ", " + (mapCenterTileY - mapGridHeight/2) + ")");
            return SwingFXUtils.toFXImage(mapImage, null);
            
        } catch (Exception e) {
            System.err.println("  ✗ Error con servidor de tiles: " + e.getMessage());
            System.err.println("  Continuando con mapa simple...");
        }
        
        return null;
    }
    
    private int[] latLonToTile(double lat, double lon, int zoom) {
        int n = 1 << zoom;
        int tileX = (int) Math.floor((lon + 180.0) / 360.0 * n);
        int tileY = (int) Math.floor((1.0 - Math.log(Math.tan(Math.toRadians(lat)) + 
            1.0 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2.0 * n);
        return new int[]{tileX, tileY};
    }
    
  
    private int calculateZoomLevel() {
        double latDiff = maxLat - minLat;
        double lonDiff = maxLon - minLon;
        double maxDiff = Math.max(latDiff, lonDiff);
        
        if (maxDiff > 0.35) return 11;   
        if (maxDiff > 0.20) return 12;   
        if (maxDiff > 0.10) return 13;   
        if (maxDiff > 0.05) return 14;   
        return 15;                        
    }
  
    private void drawSimpleBackground(GraphicsContext gc) {
       
        gc.setFill(Color.rgb(242, 239, 233));
        gc.fillRect(0, 0, WIDTH, HEIGHT);
        
        gc.setStroke(Color.rgb(200, 200, 200, 0.3));
        gc.setLineWidth(1);
        
        int gridSize = 50;
        for (int x = 0; x < WIDTH; x += gridSize) {
            gc.strokeLine(x, 0, x, HEIGHT);
        }
        for (int y = 0; y < HEIGHT; y += gridSize) {
            gc.strokeLine(0, y, WIDTH, y);
        }
        
        
        gc.setFill(Color.rgb(100, 100, 100, 0.3));
        gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 48));
        gc.fillText("CALI", WIDTH / 2 - 60, HEIGHT / 2);
    }
    
    private void calculateBounds() {
        System.out.println("\n=== DEBUG: Calculando límites geográficos ===");
        int count = 0;
        int skipped = 0;
        for (Stop stop : stops.values()) {
            double lat = stop.getDecimalLatit();
            double lon = stop.getDecimalLong();
            
    
            if (lat == 0.0 || lon == 0.0 || lat < 3.0 || lat > 4.0 || lon > -76.0 || lon < -77.0) {
                skipped++;
                if (count < 5) {
                    System.out.println(String.format("  ⚠ IGNORADA Parada %d: lat=%.6f, lon=%.6f (coordenadas inválidas)", 
                        stop.getStopId(), lat, lon));
                    count++;
                }
                continue; 
            }
            
       
            if (count < 5) {
                System.out.println(String.format("  ✓ Parada %d: lat=%.6f, lon=%.6f", 
                    stop.getStopId(), lat, lon));
                count++;
            }
            
            if (lat < minLat) minLat = lat;
            if (lat > maxLat) maxLat = lat;
            if (lon < minLon) minLon = lon;
            if (lon > maxLon) maxLon = lon;
        }
        System.out.println(String.format("  Paradas ignoradas por coordenadas inválidas: %d", skipped));
        System.out.println(String.format("  Límites finales: lat[%.6f, %.6f], lon[%.6f, %.6f]", 
            minLat, maxLat, minLon, maxLon));
        System.out.println("===========================================\n");
    }
    
    private double[] geoToPixel(double lat, double lon) {
        int tileSize = 256;
        
        double n = Math.pow(2, mapZoom);
        
        double lonRad = Math.toRadians(lon);
        double latRad = Math.toRadians(lat);
   
        double worldPixelX = ((lon + 180.0) / 360.0) * n * tileSize;
        double worldPixelY = ((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0) * n * tileSize;

        int gridOffsetX = mapGridWidth / 2;
        int gridOffsetY = mapGridHeight / 2;
        int topLeftTileX = mapCenterTileX - gridOffsetX;
        int topLeftTileY = mapCenterTileY - gridOffsetY;
        
        double gridTopLeftPixelX = topLeftTileX * tileSize;
        double gridTopLeftPixelY = topLeftTileY * tileSize;
        
        double x = worldPixelX - gridTopLeftPixelX;
        double y = worldPixelY - gridTopLeftPixelY;
        
        return new double[]{x, y};
    }
    
    private void drawBackground(GraphicsContext gc) {
        gc.setFill(Color.rgb(242, 239, 233));
        gc.fillRect(0, 0, WIDTH, HEIGHT);
        
        gc.setStroke(Color.rgb(200, 200, 200, 0.3));
        gc.setLineWidth(1);
        
        int gridSize = 50;
        for (int x = 0; x < WIDTH; x += gridSize) {
            gc.strokeLine(x, 0, x, HEIGHT);
        }
        for (int y = 0; y < HEIGHT; y += gridSize) {
            gc.strokeLine(0, y, WIDTH, y);
        }
        
        gc.setStroke(Color.rgb(100, 149, 237, 0.2));
        gc.setLineWidth(4);
        gc.strokeLine(WIDTH / 3, 0, 2 * WIDTH / 3, HEIGHT);
        
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, WIDTH, 100);
        
        gc.setFill(Color.BLACK);
        gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 32));
        gc.fillText("Sistema MIO - Grafo de Paradas y Arcos", 80, 50);
        
        gc.setFont(javafx.scene.text.Font.font("Arial", 18));
        gc.fillText("Cali, Colombia - " + stops.size() + " paradas, " + arcs.size() + " arcos", 80, 80);
    }
    
    private void drawArcs(GraphicsContext gc) {
        gc.setLineWidth(3.0);
        
        for (Arc arc : arcs) {
            Stop fromStop = stops.get(arc.getFromStopId());
            Stop toStop = stops.get(arc.getToStopId());
            
            if (fromStop != null && toStop != null) {
                if (!isValidCoordinate(fromStop) || !isValidCoordinate(toStop)) {
                    continue; 
                }
                
                double[] p1 = geoToPixel(fromStop.getDecimalLatit(), fromStop.getDecimalLong());
                double[] p2 = geoToPixel(toStop.getDecimalLatit(), toStop.getDecimalLong());
                
                Color arcColor = (arc.getOrientation() == 0) ? COLOR_IDA : COLOR_VUELTA;
                gc.setStroke(arcColor);
                gc.strokeLine(p1[0], p1[1], p2[0], p2[1]);
                
                drawArrow(gc, p1[0], p1[1], p2[0], p2[1], arcColor);
            }
        }
    }
    

    private void drawArrow(GraphicsContext gc, double x1, double y1, double x2, double y2, Color color) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.sqrt(dx * dx + dy * dy);
        
        if (length < 5) return;
        
    
        double midX = x1 + dx * 0.5;
        double midY = y1 + dy * 0.5;
        
      
        double ux = dx / length;
        double uy = dy / length;
        
        double arrowSize = 10;
        double angle = Math.PI / 6;
        
        double x1a = midX - arrowSize * (ux * Math.cos(angle) + uy * Math.sin(angle));
        double y1a = midY - arrowSize * (uy * Math.cos(angle) - ux * Math.sin(angle));
        double x2a = midX - arrowSize * (ux * Math.cos(angle) - uy * Math.sin(angle));
        double y2a = midY - arrowSize * (uy * Math.cos(angle) + ux * Math.sin(angle));
        
        gc.setStroke(color);
        gc.setLineWidth(2.5);
        gc.strokeLine(midX, midY, x1a, y1a);
        gc.strokeLine(midX, midY, x2a, y2a);
    }
    
  
    private void drawStops(GraphicsContext gc) {
        System.out.println("Dibujando " + stops.size() + " paradas sobre el mapa...");
        double radius = 6; 
        
        int debugCount = 0;
        for (Stop stop : stops.values()) {
      
            if (!isValidCoordinate(stop)) {
                continue; 
            }
            
            double[] p = geoToPixel(stop.getDecimalLatit(), stop.getDecimalLong());
            
            if (debugCount < 3) {
                System.out.println("  Parada " + stop.getStopId() + ": GPS(" + 
                    String.format("%.6f", stop.getDecimalLatit()) + ", " + 
                    String.format("%.6f", stop.getDecimalLong()) + ") -> Pixel(" + 
                    String.format("%.1f", p[0]) + ", " + String.format("%.1f", p[1]) + ")");
                debugCount++;
            }
            
            gc.setFill(COLOR_STOP);
            gc.fillOval(p[0] - radius, p[1] - radius, radius * 2, radius * 2);
            
            gc.setStroke(COLOR_STOP_BORDER);
            gc.setLineWidth(2.0);
            gc.strokeOval(p[0] - radius, p[1] - radius, radius * 2, radius * 2);
        }
    }
    

    private boolean isValidCoordinate(Stop stop) {
        double lat = stop.getDecimalLatit();
        double lon = stop.getDecimalLong();
        return lat != 0.0 && lon != 0.0 && lat >= 3.0 && lat <= 4.0 && lon <= -76.0 && lon >= -77.0;
    }
    
    private void drawLegend(GraphicsContext gc) {
        int legendX = WIDTH - 280;
        int legendY = 20;
        int legendWidth = 260;
        int legendHeight = 110;

        gc.setFill(Color.rgb(255, 255, 255, 0.95));
        gc.fillRoundRect(legendX, legendY, legendWidth, legendHeight, 10, 10);
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(2);
        gc.strokeRoundRect(legendX, legendY, legendWidth, legendHeight, 10, 10);
        
        gc.setFill(Color.BLACK);
        gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 16));
        gc.fillText("Leyenda", legendX + 10, legendY + 25);
        
        gc.setFont(javafx.scene.text.Font.font("Arial", 14));
        
        gc.setStroke(COLOR_IDA);
        gc.setLineWidth(4);
        gc.strokeLine(legendX + 10, legendY + 45, legendX + 60, legendY + 45);
        gc.setFill(Color.BLACK);
        gc.fillText("IDA (Orientación 0)", legendX + 70, legendY + 50);
        
        gc.setStroke(COLOR_VUELTA);
        gc.setLineWidth(4);
        gc.strokeLine(legendX + 10, legendY + 70, legendX + 60, legendY + 70);
        gc.setFill(Color.BLACK);
        gc.fillText("VUELTA (Orientación 1)", legendX + 70, legendY + 75);
        
        gc.setFill(COLOR_STOP);
        gc.fillOval(legendX + 25, legendY + 85, 12, 12);
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(1.5);
        gc.strokeOval(legendX + 25, legendY + 85, 12, 12);
        gc.setFill(Color.BLACK);
        gc.fillText("Parada", legendX + 70, legendY + 97);
    }
    
    public static void launch(Map<Integer, Stop> stopsData, List<Arc> arcsData) {
        setData(stopsData, arcsData);
        Application.launch(MapVisualizerFX.class);
    }
}
