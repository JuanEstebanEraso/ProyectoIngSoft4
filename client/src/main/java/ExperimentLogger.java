import MIO.GlobalResult;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ExperimentLogger {
    
    private static final String RESULTS_DIR = "experiment_results";
    private static final String RESULTS_FILE = "experiment_results.csv";
    
    public ExperimentLogger() {
        File dir = new File(RESULTS_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        File file = new File(RESULTS_DIR, RESULTS_FILE);
        if (!file.exists()) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                writer.println("timestamp,datagram_count,workers,total_time_ms,load_time_ms," +
                             "separation_time_ms,distribution_time_ms,consolidation_time_ms," +
                             "avg_speed_kmh,throughput_dps,total_arcs,tasks");
            } catch (IOException e) {
                System.err.println("Error creando archivo de resultados: " + e.getMessage());
            }
        }
    }
    
    public void logResult(int datagramCount, GlobalResult result) {
        File file = new File(RESULTS_DIR, RESULTS_FILE);
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(file, true))) {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(new Date());
            
            double throughput = result.totalProcessingTimeMs > 0 
                ? (double) result.totalDatagrams / result.totalProcessingTimeMs * 1000 
                : 0;
            
            writer.printf("%s,%d,%d,%d,%d,%d,%d,%d,%.2f,%.2f,%d,%d%n",
                timestamp,
                datagramCount,
                result.activeWorkers,
                result.totalProcessingTimeMs,
                result.loadCsvTimeMs,
                result.separationTimeMs,
                result.distributionTimeMs,
                result.consolidationTimeMs,
                result.globalAvgSpeed,
                throughput,
                result.totalArcs,
                result.taskCount
            );
            
            System.out.println("[OK] Resultados guardados en: " + RESULTS_DIR + "/" + RESULTS_FILE);
            
        } catch (IOException e) {
            System.err.println("Error guardando resultados: " + e.getMessage());
        }
    }
    
    public void printSummary(int datagramCount, GlobalResult result) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("[RESUMEN DEL EXPERIMENTO]");
        System.out.println("=".repeat(80));
        System.out.println("Tamaño del dataset:       " + formatNumber(datagramCount));
        System.out.println("Workers remotos activos:  " + result.activeWorkers);
        System.out.println("Datagramas procesados:    " + formatNumber(result.totalDatagrams));
        System.out.println("Arcos únicos:             " + result.totalArcs);
        System.out.println("Tareas distribuidas:      " + result.taskCount);
        System.out.println("");
        System.out.println("[TIEMPOS DE EJECUCION]:");
        System.out.println("  Carga CSV:              " + result.loadCsvTimeMs + " ms");
        System.out.println("  Separación de tareas:   " + result.separationTimeMs + " ms");
        System.out.println("  Distribución a workers: " + result.distributionTimeMs + " ms");
        System.out.println("  Consolidación:          " + result.consolidationTimeMs + " ms");
        System.out.println("  ------------------------------------");
        System.out.println("  TOTAL:                  " + result.totalProcessingTimeMs + " ms");
        System.out.println("");
        System.out.println("[METRICAS DE RENDIMIENTO]:");
        System.out.println("  Velocidad promedio:     " + String.format("%.2f", result.globalAvgSpeed) + " km/h");
        
        double throughput = result.totalProcessingTimeMs > 0 
            ? (double) result.totalDatagrams / result.totalProcessingTimeMs * 1000 
            : 0;
        System.out.println("  Throughput:             " + formatNumber((long)throughput) + " datagramas/segundo");
        System.out.println("=".repeat(80) + "\n");
    }
    
    private String formatNumber(long num) {
        if (num >= 1_000_000_000) {
            return String.format("%.1fB", num / 1_000_000_000.0);
        } else if (num >= 1_000_000) {
            return String.format("%.1fM", num / 1_000_000.0);
        } else if (num >= 1_000) {
            return String.format("%.1fK", num / 1_000.0);
        }
        return String.valueOf(num);
    }
}
