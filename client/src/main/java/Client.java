import com.zeroc.Ice.*;
import MIO.*;

/**
 * Cliente del Sistema MIO usando ZeroICE
 * 
 * Modos de operacion:
 * - Sin argumentos: Operaciones basicas del sistema MIO
 * - "benchmark": Ejecuta benchmark con datos REALES del CSV
 * - "benchmark-gen": Ejecuta benchmark con datos generados
 * - "test N": Procesa N datagramas reales
 */
public class Client {
    
    // Ruta al archivo de datagramas (archivo completo de historia)
    private static final String DATAGRAMS_CSV = "data/datagrams4history.csv";
    
    public static void main(String[] args) {
        // Configurar propiedades de Ice con limite de mensaje mayor
        InitializationData initData = new InitializationData();
        initData.properties = Util.createProperties();
        // 500 MB para soportar grandes volumenes de datagramas
        initData.properties.setProperty("Ice.MessageSizeMax", "500000");
        
        try (Communicator communicator = Util.initialize(args, initData)) {
            
            System.out.println("=".repeat(80));
            System.out.println("Cliente del Sistema MIO - ZeroICE");
            System.out.println("Patrones: ThreadPool + Separable Dependencies");
            System.out.println("=".repeat(80));
            
            // Crear proxies a los servicios remotos
            ObjectPrx baseMIO = communicator.stringToProxy("MIOService:tcp -h localhost -p 10000");
            ObjectPrx baseMaster = communicator.stringToProxy("Master:tcp -h localhost -p 10000");
            
            MIOServicePrx mioService = MIOServicePrx.checkedCast(baseMIO);
            MasterPrx master = MasterPrx.checkedCast(baseMaster);
            
            if (mioService == null || master == null) {
                throw new Error("Proxy invalido - No se pudo conectar a los servicios");
            }
            
            System.out.println("\nConectado a los servicios:");
            System.out.println("  - MIOService");
            System.out.println("  - Master (procesamiento distribuido)");
            System.out.println("=".repeat(80));
            
            // 1. Construir el grafo
            System.out.println("\n[1] Construyendo el grafo en el servidor...");
            mioService.buildGraph();
            System.out.println("    Grafo construido exitosamente");
            
            // 2. Obtener estadisticas
            System.out.println("\n[2] Estadisticas del sistema:");
            String stats = mioService.getStatistics();
            System.out.println(stats);
            
            // 3. Obtener arcos para benchmark
            System.out.println("\n[3] Obteniendo arcos del grafo...");
            ArcInfo[] arcs = mioService.getAllArcs();
            System.out.println("    Total de arcos: " + arcs.length);
            
            // Verificar modo de ejecucion
            if (args.length > 0 && args[0].equals("benchmark")) {
                // Benchmark con datos REALES del CSV
                runBenchmark(master, arcs);
            } else if (args.length > 0 && args[0].equals("benchmark-gen")) {
                // Benchmark con datos generados
                runBenchmarkWithRealData(master);
            } else if (args.length > 1 && args[0].equals("test")) {
                int count = Integer.parseInt(args[1]);
                runSingleTestWithRealData(master, count);
            } else {
                // Modo demo: prueba rapida con 100,000 datagramas reales
                runDemoWithRealData(master);
            }
            
            System.out.println("\n" + "=".repeat(80));
            System.out.println("Todas las operaciones completadas exitosamente");
            System.out.println("=".repeat(80));
            
        } catch (java.lang.Exception e) {
            System.err.println("Error en el cliente: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Ejecuta benchmark con datos REALES del CSV
     */
    private static void runBenchmarkWithRealData(MasterPrx master) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("EJECUTANDO BENCHMARK CON DATOS REALES (datagrams4history.csv)");
        System.out.println("=".repeat(80));
        
        String report = master.runBenchmarkWithRealData(DATAGRAMS_CSV);
        System.out.println(report);
    }
    
    /**
     * Ejecuta benchmark completo con datos generados
     */
    private static void runBenchmark(MasterPrx master, ArcInfo[] arcs) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("EJECUTANDO BENCHMARK CON DATOS GENERADOS");
        System.out.println("=".repeat(80));
        
        String report = master.runBenchmark(arcs);
        System.out.println(report);
    }
    
    /**
     * Ejecuta prueba con un numero especifico de datagramas reales
     */
    private static void runSingleTestWithRealData(MasterPrx master, int count) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("EJECUTANDO PRUEBA CON " + formatNumber(count) + " DATAGRAMAS REALES");
        System.out.println("=".repeat(80));
        
        // Cargar datagramas reales
        System.out.println("\n[4] Cargando " + formatNumber(count) + " datagramas desde CSV...");
        long loadStart = System.currentTimeMillis();
        SpeedDatagram[] datagrams = master.loadDatagramsFromCSV(DATAGRAMS_CSV, count);
        long loadTime = System.currentTimeMillis() - loadStart;
        System.out.println("    Carga completada en " + loadTime + " ms");
        System.out.println("    Datagramas cargados: " + formatNumber(datagrams.length));
        
        // Procesar datagramas
        int numTasks = Runtime.getRuntime().availableProcessors() * 2;
        System.out.println("\n[5] Procesando datagramas con " + numTasks + " tareas...");
        
        GlobalResult result = master.processDatagrams(datagrams, numTasks);
        
        // Mostrar resultados
        printResults(result);
    }
    
    /**
     * Ejecuta prueba demo con datos reales
     */
    private static void runDemoWithRealData(MasterPrx master) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("PRUEBA DEMO - Procesamiento con datos REALES");
        System.out.println("=".repeat(80));
        System.out.println("\nOpciones de ejecucion:");
        System.out.println("  java -jar client.jar benchmark       -> Benchmark con datos reales");
        System.out.println("  java -jar client.jar benchmark-gen   -> Benchmark con datos generados");
        System.out.println("  java -jar client.jar test <cantidad> -> Prueba con N datagramas reales");
        
        // Obtiene el total de líneas del archivo CSV, pero limita a 1,000,000 máximo
        int totalLines = countLinesInFile(DATAGRAMS_CSV);
        int maxLimit = 1_000_000;
        int toProcess = Math.min(totalLines, maxLimit);
        runSingleTestWithRealData(master, toProcess);
    }

    // Cuenta el total de líneas en el archivo CSV
    private static int countLinesInFile(String filePath) {
        int lines = 0;
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(filePath))) {
            while (reader.readLine() != null) lines++;
        } catch (java.lang.Exception e) {
            System.err.println("Error contando líneas del archivo: " + e.getMessage());
        }
        return lines;
    }
    
    /**
     * Muestra los resultados de procesamiento
     */
    private static void printResults(GlobalResult result) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("RESULTADOS:");
        System.out.println("  - Datagramas procesados: " + formatNumber(result.totalDatagrams));
        System.out.println("  - Arcos unicos: " + result.totalArcs);
        System.out.println("  - Velocidad promedio: " + String.format("%.2f", result.globalAvgSpeed) + " km/h");
        System.out.println("  - Tiempo total: " + result.totalProcessingTimeMs + " ms");
        System.out.println("  - Workers utilizados: " + result.workerCount);
        System.out.println("  - Tareas procesadas: " + result.taskCount);
        
        double throughput = (double) result.totalDatagrams / result.totalProcessingTimeMs * 1000;
        System.out.println("  - Throughput: " + formatNumber((long) throughput) + " datagramas/segundo");
        System.out.println("=".repeat(80));
    }
    
    private static String formatNumber(long num) {
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
