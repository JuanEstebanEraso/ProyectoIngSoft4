import com.zeroc.Ice.*;
import server.MIOServiceI;
import server.MasterI;

public class Server {

    private static final int NUM_THREADS = Runtime.getRuntime().availableProcessors();

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
                    "Archivo 'config.properties' no encontrado. Usando configuracion por defecto (0.0.0.0:9888).");
            initData.properties.setProperty("MIOService.Endpoints", "tcp -h 0.0.0.0 -p 9888");
            initData.properties.setProperty("Ice.MessageSizeMax", "500000");
        }

        MasterI master = null;

        try (Communicator communicator = Util.initialize(args, initData)) {

            System.out.println("=".repeat(80));
            System.out.println("Iniciando servidor del Sistema MIO con ZeroICE...");
            System.out.println("Patrones: ThreadPool + Separable Dependencies");
            System.out.println("=".repeat(80));

            ObjectAdapter adapter = communicator.createObjectAdapter("MIOService");

            master = new MasterI(NUM_THREADS);
            adapter.add(master, Util.stringToIdentity("Master"));

            MIOServiceI mioService = new MIOServiceI();
            mioService.setMaster(master);
            adapter.add(mioService, Util.stringToIdentity("MIOService"));

            System.out.println("\nServicios registrados:");
            System.out.println("  - MIOService (identity: 'MIOService')");
            System.out.println("  - Master (identity: 'Master')");
            System.out.println("\nConfiguracion ThreadPool:");
            System.out.println("  - Threads: " + NUM_THREADS);

            com.zeroc.Ice.Endpoint[] endpoints = adapter.getEndpoints();
            if (endpoints.length > 0) {
                System.out.println("\nEndpoint: " + endpoints[0].toString());
            } else {
                System.out.println("\nEndpoint: (No endpoints configured)");
            }

            adapter.activate();

            System.out.println("\n" + "=".repeat(80));
            System.out.println("Servidor listo y esperando conexiones...");
            System.out.println("Presiona Ctrl+C para detener el servidor");
            System.out.println("=".repeat(80) + "\n");

            communicator.waitForShutdown();

        } catch (java.lang.Exception e) {
            System.err.println("Error en el servidor: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (master != null) {
                master.shutdown();
            }
        }
    }
}
