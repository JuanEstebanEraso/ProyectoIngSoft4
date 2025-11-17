module ProyectoIngSoft4 {
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.swing;
    requires java.desktop;
    
    exports com.mio;
    exports com.mio.model;
    exports com.mio.util;
    exports com.mio.visualization;
    
    opens com.mio.visualization to javafx.graphics;
}
