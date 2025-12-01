import com.zeroc.Ice.*;
import MIO.*;
import client.WorkerI;

public class WorkerClient {

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
            System.out.println("Worker Client - Sistema MIO");
            System.out.println("Esperando tareas del Master...");
            System.out.println("=".repeat(80));

            String serverIp = communicator.getProperties().getPropertyWithDefault("MIO.Server.Host", "10.147.17.101");
            String serverPort = communicator.getProperties().getPropertyWithDefault("MIO.Server.Port", "9888");
            String connectionString = "tcp -h " + serverIp + " -p " + serverPort;

            System.out.println("Conectando a Master en: " + connectionString);

            ObjectPrx baseMaster = communicator.stringToProxy("Master:" + connectionString);
            MasterPrx master = MasterPrx.checkedCast(baseMaster);

            if (master == null) {
                throw new Error("No se pudo conectar al Master");
            }

            System.out.println("✅ Conectado al Master");

            ObjectAdapter adapter = communicator.createObjectAdapterWithEndpoints(
                    "WorkerAdapter",
                    "tcp"
            );

            WorkerI workerServant = new WorkerI(0);
            ObjectPrx workerObj = adapter.addWithUUID(workerServant);

            adapter.activate();

            WorkerPrx workerProxy = WorkerPrx.uncheckedCast(workerObj);
            master.registerWorker(workerProxy);

            System.out.println("✅ Worker registrado con el Master");
            System.out.println("Endpoint del Worker: " + workerObj.toString());
            System.out.println("\n" + "=".repeat(80));
            System.out.println("Worker listo. Esperando tareas...");
            System.out.println("Presiona Ctrl+C para detener");
            System.out.println("=".repeat(80) + "\n");

            communicator.waitForShutdown();

        } catch (java.lang.Exception e) {
            System.err.println("Error en el Worker Client: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
