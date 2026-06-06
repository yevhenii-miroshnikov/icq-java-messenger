package org.example.messengerclient;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

public class ClientApplication extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/org/example/messengerclient/views/login-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 400, 450);

        scene.getStylesheets().add(getClass().getResource("/org/example/messengerclient/styles/style.css").toExternalForm());
        stage.setTitle("Messenger (Client)");
        stage.setResizable(false);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}