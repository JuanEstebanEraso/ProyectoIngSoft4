import com.zeroc.Ice.*;
import server.MIOServiceI;
import server.MasterI;

/**
 * Servidor del Sistema MIO usando ZeroICE
 * 
 * Incluye:
 * - MIOService: Servicio principal del sistema MIO
 * - Master: Coordinador de procesamiento distribuido (ThreadPool + Separable Dependencies)
 */
public class Server {
    
    // Numero de threads para el ThreadPool (configurable)
    private static final int NUM_THREADS = Runtime.getRuntime().availableProcessors();
    
    public static void main(String[] args) {
        MasterI master = null;
        
        // Configurar propiedades de Ice
        InitializationData initData = new InitializationData();
        initData.properties = Util.createProperties();
        // 500 MB para soportar 100M datagramas
        initData.properties.setProperty("Ice.MessageSizeMax", "500000");
        initData.properties.setProperty("MIOService.Endpoints", "tcp -h 0.0.0.0 -p 10000");
        
        try (Communicator communicator = Util.initialize(args, initData)) {
            
            System.out.println("=".repeat(80));
            System.out.println("Iniciando servidor del Sistema MIO con ZeroICE...");
            System.out.println("Patrones: ThreadPool + Separable Dependencies");
            System.out.println("=".repeat(80));
            
            // Crear el ObjectAdapter
            ObjectAdapter adapter = communicator.createObjectAdapter("MIOService");
            
            // Crear el Master para procesamiento distribuido
            master = new MasterI(NUM_THREADS);
            adapter.add(master, Util.stringToIdentity("Master"));
            
            // Crear la implementación del servicio MIO y conectarla con el Master
            MIOServiceI mioService = new MIOServiceI();
            mioService.setMaster(master);  // Para que pueda pasar las paradas al Master
            adapter.add(mioService, Util.stringToIdentity("MIOService"));
            
            System.out.println("\nServicios registrados:");
            System.out.println("  - MIOService (identity: 'MIOService')");
            System.out.println("  - Master (identity: 'Master')");
            System.out.println("\nConfiguracion ThreadPool:");
            System.out.println("  - Threads: " + NUM_THREADS);
            System.out.println("\nEndpoint: tcp -h 0.0.0.0 -p 10000");
            
            // Activar el adapter
            adapter.activate();
            
            System.out.println("\n" + "=".repeat(80));
            System.out.println("Servidor listo y esperando conexiones...");
            System.out.println("Presiona Ctrl+C para detener el servidor");
            System.out.println("=".repeat(80) + "\n");
            
            // Esperar hasta que se detenga
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
