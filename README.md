# Proyecto SITM-MIO - Grafo de Arcos
 el objetivo fue construir y analizar el grafo de arcos del Sistema Integrado de Transporte Masivo de Cali.

## Descripción

El programa lee datos de rutas, paradas y secuencias de paradas desde archivos CSV y construye un grafo donde:
- **Nodos**: Representan las paradas del sistema
- **Aristas (Arcos)**: Representan las conexiones entre paradas consecutivas en cada ruta
- Cada ruta tiene dos orientaciones: IDA (0) y VUELTA (1)

## Estructura del Proyecto

```
ProyectoIngSoft4/
├── src/
│   └── com/
│       └── mio/
│           ├── Main.java                    # Clase principal
│           ├── model/                       # Clases del modelo de datos
│           │   ├── Route.java              # Representa una ruta
│           │   ├── Stop.java               # Representa una parada
│           │   ├── LineStop.java           # Relación ruta-parada
│           │   └── Arc.java                # Representa un arco del grafo
│           └── util/                        # Utilidades
│               ├── CSVReader.java          # Lector de archivos CSV
│               └── GraphBuilder.java       # Constructor del grafo
├── data/                                    # Archivos CSV de entrada
│   ├── lines.csv                           # Datos de rutas
│   ├── stops.csv                           # Datos de paradas
│   └── linestops.csv                       # Secuencias de paradas por ruta
└── README.md
```

## Compilación y Ejecución

### Requisitos

- **Java 17** o superior
- **Maven 3.6+**
- Conexión a Internet (para descargar mapa de OpenStreetMap)

### Usando Maven 

```powershell
mvn clean compile javafx:run
```

## Salida del Programa

El programa genera:

1. **Salida en consola**: Resumen de rutas, paradas y arcos cargados
2. **Archivo de texto**: `output/grafo_arcos_mio.txt` con el listado completo de todas las rutas, variantes y arcos
3. **Visualización JavaFX**: Interfaz gráfica que muestra el grafo sobre el mapa real de Cali
   - Descarga automáticamente tiles de OpenStreetMap
   - Proyección Web Mercator para alineación geográfica
   - **Arcos azules**: Rutas de IDA
   - **Arcos rojos**: Rutas de VUELTA
   - **Puntos amarillos**: Paradas del sistema


