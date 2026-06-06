module org.example.messengerclient {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.xml;
    requires java.logging;

    opens org.example.messengerclient to javafx.fxml;
    exports org.example.messengerclient;
    exports org.example.messengerclient.controllers;
    opens org.example.messengerclient.controllers to javafx.fxml;
}