
import com.zeroc.Ice.*;
import MIO.*;

public class Client {

    private static final String DATAGRAMS_CSV = "/home/swarch/proyecto-mio/MIO/datagrams4history.csv";

    public static void main(String[] args) {
        InitializationData initData = new InitializationData();
        initData.properties = Util.createProperties();

        java.io.File configFile = new java.io.File("config.properties");
        if (configFile.exists()) {
            try {
                initData.properties.load(configFile.getAbsolutePath());
                System.out.println("Configuracion cargada de 'config.properties'");
            } catch (java.lang.Exception e) {
                System.err.println("Advertencia: No se pudo leer 'config.properties', usando defaults.");
            }
        } else {
            System.out.println(
                    "Archivo 'config.properties' no encontrado. Usando configuracion por defecto (10.147.17.101:9888).");
            initData.properties.setProperty("MIO.Server.Host", "10.147.17.101");
            initData.properties.setProperty("MIO.Server.Port", "9888");
            initData.properties.setProperty("Ice.MessageSizeMax", "500000");
        }

        try (Communicator communicator = Util.initialize(args, initData)) {

            System.out.println("=".repeat(80));
            System.out.println("Cliente del Sistema MIO - ZeroICE");
            System.out.println("Patrones: ThreadPool + Separable Dependencies");
            System.out.println("=".repeat(80));

            String serverIp = communicator.getProperties().getPropertyWithDefault("MIO.Server.Host", "10.147.17.101");
            String serverPort = communicator.getProperties().getPropertyWithDefault("MIO.Server.Port", "9888");
            String connectionString = "tcp -h " + serverIp + " -p " + serverPort;

            System.out.println("Conectando a: " + connectionString);

            ObjectPrx baseMIO = communicator.stringToProxy("MIOService:" + connectionString);
            ObjectPrx baseMaster = communicator.stringToProxy("Master:" + connectionString);

            MIOServicePrx mioService = MIOServicePrx.checkedCast(baseMIO);
            MasterPrx master = MasterPrx.checkedCast(baseMaster);

            if (mioService == null || master == null) {
                throw new Error("Proxy invalido - No se pudo conectar a los servicios");
            }

            System.out.println("\nConectado a los servicios:");
            System.out.println("  - MIOService");
            System.out.println("  - Master (procesamiento distribuido)");
            System.out.println("=".repeat(80));

            System.out.println("\n[1] Construyendo el grafo en el servidor...");
            mioService.buildGraph();
            System.out.println("    Grafo construido exitosamente");

            System.out.println("\n[2] Estadisticas del sistema:");
            String stats = mioService.getStatistics();
            System.out.println(stats);

            System.out.println("\n[3] Obteniendo arcos del grafo...");
            ArcInfo[] arcs = mioService.getAllArcs();
            System.out.println("    Total de arcos: " + arcs.length);

            if (args.length > 0 && args[0].equals("benchmark")) {
                runBenchmarkWithRealData(mioService);
            } else if (args.length > 0 && args[0].equals("benchmark-gen")) {
                runBenchmark(master, arcs);
            } else if (args.length > 1 && args[0].equals("test")) {
                int count = Integer.parseInt(args[1]);
                runSingleTestWithRealData(mioService, count);
            } else if (args.length > 0 && args[0].equals("experiment")) {
                runExperiments(mioService);
            } else {
                runDemoWithRealData(mioService);
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

    private static void runBenchmarkWithRealData(MIOServicePrx mioService) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("EJECUTANDO BENCHMARK CON DATOS REALES (datagrams4history.csv)");
        System.out.println("=".repeat(80));

        System.out.println("\n[4] Solicitando cálculo de velocidad promedio al servidor...");
        System.out.println("    (El servidor procesará el CSV completo y distribuirá el trabajo)");
        
        long startTime = System.currentTimeMillis();
        
        GlobalResult result = mioService.calculateAverageSpeed(DATAGRAMS_CSV, Integer.MAX_VALUE);
        
        long totalTime = System.currentTimeMillis() - startTime;
        
        printResults(result);
        System.out.println("\n[5] Tiempo total desde el cliente: " + totalTime + " ms");
    }

    private static void runBenchmark(MasterPrx master, ArcInfo[] arcs) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("EJECUTANDO BENCHMARK CON DATOS GENERADOS");
        System.out.println("=".repeat(80));

        String report = master.runBenchmark(arcs);
        System.out.println(report);
    }

    private static void runSingleTestWithRealData(MIOServicePrx mioService, int count) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("EJECUTANDO PRUEBA CON " + formatNumber(count) + " DATAGRAMAS REALES");
        System.out.println("=".repeat(80));

        System.out.println("\n[4] Solicitando cálculo al servidor...");
        System.out.println("    (El servidor procesará " + formatNumber(count) + " datagramas)");
        
        long startTime = System.currentTimeMillis();
        
        GlobalResult result = mioService.calculateAverageSpeed(DATAGRAMS_CSV, count);
        
        long totalTime = System.currentTimeMillis() - startTime;
        
        printResults(result);
        System.out.println("\n[5] Tiempo total desde el cliente: " + totalTime + " ms");
    }

    private static void runExperiments(MIOServicePrx mioService) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("[MODO EXPERIMENTO] - Pruebas de Escalabilidad");
        System.out.println("=".repeat(80));
        
        ExperimentLogger logger = new ExperimentLogger();
        
        int[] sizes = {1_000_000, 10_000_000, 100_000_000};
        
        System.out.println("\nSe ejecutarán pruebas con los siguientes tamaños:");
        for (int size : sizes) {
            System.out.println("  - " + formatNumber(size) + " datagramas");
        }
        System.out.println("\nPresiona ENTER para continuar...");
        
        try {
            System.in.read();
        } catch (java.io.IOException e) {
        }
        
        for (int i = 0; i < sizes.length; i++) {
            int size = sizes[i];
            System.out.println("\n" + "=".repeat(80));
            System.out.println("[EXPERIMENTO " + (i + 1) + "/" + sizes.length + "]: " + formatNumber(size) + " datagramas");
            System.out.println("=".repeat(80));
            
            try {
                long startTime = System.currentTimeMillis();
                
                GlobalResult result = mioService.calculateAverageSpeed(DATAGRAMS_CSV, size);
                
                long totalTime = System.currentTimeMillis() - startTime;
                
                logger.printSummary(size, result);
                logger.logResult(size, result);
                
                if (i < sizes.length - 1) {
                    System.out.println("\n[*] Esperando 5 segundos antes del siguiente experimento...\n");
                    Thread.sleep(5000);
                }
                
            } catch (java.lang.Exception e) {
                System.err.println("[ERROR] Error en experimento con " + formatNumber(size) + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("[OK] EXPERIMENTOS COMPLETADOS");
        System.out.println("=".repeat(80));
        System.out.println("Los resultados han sido guardados en: experiment_results/experiment_results.csv");
        System.out.println("Usa el script plot_results.py para generar gráficos");
        System.out.println("=".repeat(80) + "\n");
    }

    private static void runDemoWithRealData(MIOServicePrx mioService) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("PRUEBA DEMO - Procesamiento con datos REALES");
        System.out.println("=".repeat(80));
        System.out.println("\nOpciones de ejecucion:");
        System.out.println("  java -jar client.jar benchmark       -> Benchmark con datos reales");
        System.out.println("  java -jar client.jar benchmark-gen   -> Benchmark con datos generados");
        System.out.println("  java -jar client.jar test <cantidad> -> Prueba con N datagramas reales");
        System.out.println("  java -jar client.jar experiment      -> Ejecutar experimentos (1M, 10M, 100M)");

        runSingleTestWithRealData(mioService, 100_000_000);
    }

    private static void printResults(GlobalResult result) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("RESULTADOS:");
        System.out.println("  - Datagramas procesados: " + formatNumber(result.totalDatagrams));
        System.out.println("  - Arcos únicos: " + result.totalArcs);
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
