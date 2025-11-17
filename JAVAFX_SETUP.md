# Visualización con JavaFX - Sistema MIO

## Requisitos

Para ejecutar la visualización gráfica necesitas instalar JavaFX.

### Instalación de JavaFX

#### Opción 1: Descargar JavaFX SDK manualmente

1. Descarga JavaFX SDK desde: https://gluonhq.com/products/javafx/
   - Elige la versión compatible con tu JDK (Java 17)
   - Descarga el SDK para Windows

2. Extrae el archivo ZIP en una carpeta, por ejemplo:
   ```
   C:\javafx-sdk-21
   ```

3. Configura las variables de entorno o usa los parámetros al ejecutar.

#### Opción 2: Usar Maven (Recomendado)

Crea un archivo `pom.xml` en la raíz del proyecto:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <groupId>com.mio</groupId>
    <artifactId>ProyectoIngSoft4</artifactId>
    <version>1.0-SNAPSHOT</version>
    
    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    
    <dependencies>
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-controls</artifactId>
            <version>21.0.1</version>
        </dependency>
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-graphics</artifactId>
            <version>21.0.1</version>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.openjfx</groupId>
                <artifactId>javafx-maven-plugin</artifactId>
                <version>0.0.8</version>
                <configuration>
                    <mainClass>com.mio.Main</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

## Ejecución

### Con Maven:

```powershell
mvn clean javafx:run
```

### Con JavaFX SDK manual:

```powershell
java --module-path "C:\javafx-sdk-21\lib" --add-modules javafx.controls,javafx.graphics -cp bin com.mio.Main
```

### Desde VS Code:

1. Instala la extensión "Extension Pack for Java" si no la tienes
2. Configura el JavaFX SDK en VS Code:
   - Abre Settings (Ctrl+,)
   - Busca "java.project.referencedLibraries"
   - Añade: `C:/javafx-sdk-21/lib/**/*.jar`

3. Ejecuta el proyecto normalmente

## Características de la Visualización

### Ventana Principal
- **Tamaño**: 1200x900 píxeles (con scroll para ver todo el mapa)
- **Canvas**: 1800x1400 píxeles (grafo completo)
- **Navegación**: Scroll y arrastre con el mouse

### Elementos Gráficos
- **Paradas**: Círculos amarillos con borde negro (radio 5px)
- **Arcos IDA**: Líneas azules con flechas direccionales
- **Arcos VUELTA**: Líneas rojas con flechas direccionales
- **Fondo**: Simulación de mapa con cuadrícula

### Controles
- **Scroll**: Usa la rueda del mouse para hacer zoom
- **Pan**: Arrastra con el mouse para moverte por el mapa
- **Cerrar**: Cierra la ventana para finalizar

## Estructura del Código

```
src/
├── com/mio/
│   ├── Main.java                    # Punto de entrada
│   ├── model/                       # Entidades
│   ├── util/                        # Utilidades
│   └── visualization/
│       └── MapVisualizerFX.java     # Visualizador JavaFX
└── module-info.java                 # Configuración módulos
```

## Mejoras Futuras

1. **Zoom Interactivo**: Hacer zoom con la rueda del mouse
2. **Filtros**: Mostrar/ocultar rutas específicas
3. **Tooltips**: Mostrar información al pasar sobre paradas
4. **Búsqueda**: Buscar paradas o rutas por nombre
5. **Exportar**: Guardar vista actual como imagen
6. **Mapa Real**: Integrar tiles de OpenStreetMap
7. **Animación**: Animar el recorrido de los buses

## Solución de Problemas

### Error: "javafx cannot be resolved"
- Asegúrate de tener JavaFX instalado
- Verifica que la ruta en `--module-path` sea correcta
- Usa Maven para gestión automática de dependencias

### Error: "module not found"
- Elimina el archivo `module-info.java` si da problemas
- O asegúrate de que JavaFX esté en el module path

### La ventana no se muestra
- Verifica que no haya errores en la consola
- Asegúrate de que los datos CSV se carguen correctamente
- Comprueba que tengas permisos de ventanas gráficas

## Recursos

- JavaFX Documentation: https://openjfx.io/
- JavaFX Tutorial: https://docs.oracle.com/javafx/2/
- Gluon JavaFX: https://gluonhq.com/products/javafx/
