module MIO
{
    // ============================================================
    // ESTRUCTURAS BÁSICAS DEL SISTEMA MIO
    // ============================================================
    
    // Estructura para representar una parada
    struct StopInfo
    {
        int stopId;
        string stopName;
        double latitude;
        double longitude;
    }
    
    // Estructura para representar una ruta
    struct RouteInfo
    {
        int routeId;
        string routeName;
        string variant;
        int orientation; // 0 = IDA, 1 = VUELTA
    }
    
    // Estructura para representar un arco
    struct ArcInfo
    {
        int fromStopId;
        int toStopId;
        int routeId;
        string routeName;
        int orientation;
        int sequenceOrder;
    }
    
    // Secuencias de estructuras básicas (declaradas primero para uso posterior)
    sequence<StopInfo> StopList;
    sequence<RouteInfo> RouteList;
    sequence<ArcInfo> ArcList;
    
    // ============================================================
    // ESTRUCTURAS PARA PROCESAMIENTO DISTRIBUIDO
    // Patrones: ThreadPool + Separable Dependencies
    // ============================================================
    
    // Datagrama de velocidad: representa una medición de velocidad en un arco
    struct SpeedDatagram
    {
        int arcId;          // ID del arco (fromStopId * 10000 + toStopId)
        int fromStopId;     // Parada origen
        int toStopId;       // Parada destino
        double speed;       // Velocidad medida (km/h) - del CSV
        long timestamp;     // Timestamp de la medición
        // Coordenadas para calculo de distancia (Separable Dependencies - datos replicados)
        double fromLat;     // Latitud parada origen
        double fromLon;     // Longitud parada origen
        double toLat;       // Latitud parada destino
        double toLon;       // Longitud parada destino
    }
    
    // Secuencia de datagramas
    sequence<SpeedDatagram> DatagramList;
    
    // Resultado parcial de un Task (velocidad promedio de un subconjunto de arcos)
    struct PartialResult
    {
        int taskId;             // ID de la tarea
        int arcCount;           // Número de arcos únicos procesados
        long datagramCount;     // Número de datagramas procesados
        double sumSpeed;        // Suma de velocidades calculadas (dist/tiempo)
        double totalDistance;   // Distancia total recorrida (km)
        double totalTime;       // Tiempo total (horas)
        double avgSpeed;        // Velocidad promedio parcial
        long processingTimeMs;  // Tiempo de procesamiento en ms
        double filteredSpeedSum; // Suma de velocidades filtradas (>5 km/h)
        long filteredCount;      // Cantidad de datagramas filtrados (>5 km/h)
    }
    
    // Resultado global consolidado
    struct GlobalResult
    {
        int totalArcs;              // Total de arcos únicos
        long totalDatagrams;        // Total de datagramas procesados
        double globalAvgSpeed;      // Velocidad promedio global
        long totalProcessingTimeMs; // Tiempo total de procesamiento
        int workerCount;            // Número de workers utilizados
        int taskCount;              // Número de tareas procesadas
    }
    
    // Task: representa una tarea independiente con datos replicados
    struct Task
    {
        int taskId;             // ID único de la tarea
        DatagramList datagrams; // Datos replicados para esta tarea
    }
    
    sequence<Task> TaskList;
    sequence<PartialResult> PartialResultList;
    
    // ============================================================
    // INTERFACES
    // ============================================================
    
    // Interfaz Worker: procesa tareas asignadas (ThreadPool pattern)
    interface Worker
    {
        // Procesar una tarea y retornar resultado parcial
        PartialResult processTask(Task task);
        
        // Verificar si el worker está disponible
        bool isAvailable();
        
        // Obtener ID del worker
        int getWorkerId();
    }
    
    // Interfaz Master: coordina procesamiento distribuido (Separable Dependencies)
    interface Master
    {
        // Registrar un worker en el pool
        void registerWorker(Worker* worker);
        
        // Desregistrar un worker
        void unregisterWorker(int workerId);
        
        // Obtener número de workers registrados
        int getWorkerCount();
        
        // Procesar datagramas y calcular velocidad promedio global
        GlobalResult processDatagrams(DatagramList datagrams, int numTasks);
        
        // Generar datagramas de prueba para benchmarking
        DatagramList generateTestDatagrams(int count, ArcList arcs);
        
        // Cargar datagramas reales desde archivo CSV
        DatagramList loadDatagramsFromCSV(string filePath, int maxCount);
        
        // Ejecutar benchmark completo con datos reales del CSV
        string runBenchmarkWithRealData(string csvPath);
        
        // Ejecutar benchmark completo con múltiples tamaños (datos generados)
        string runBenchmark(ArcList arcs);
    }
    
    // Interfaz principal del sistema MIO
    interface MIOService
    {
        // Consultar información de una parada por ID
        StopInfo getStop(int stopId);
        
        // Obtener todas las paradas del sistema
        StopList getAllStops();
        
        // Consultar información de una ruta por ID
        RouteInfo getRoute(int routeId);
        
        // Obtener todas las rutas
        RouteList getAllRoutes();
        
        // Obtener todos los arcos del grafo
        ArcList getAllArcs();
        
        // Obtener arcos de una ruta específica
        ArcList getArcsByRoute(int routeId, int orientation);
        
        // Obtener estadísticas del sistema
        string getStatistics();
        
        // Construir el grafo (inicializar el sistema)
        void buildGraph();
    }
}
