package org.example.messengerclient.controllers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.example.messengerclient.network.ServerConnection;
import org.example.messengerclient.network.XMLProtocol;
import org.example.messengerclient.network.SecurityUtil;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoginController {

    private static final Logger logger = Logger.getLogger(LoginController.class.getName());

    @FXML private TextField serverIpField;
    @FXML private TextField loginField;
    @FXML private PasswordField passwordField;
    @FXML private Label statusLabel;

    private ServerConnection connection;
    private ChatController chatController;

    @FXML
    public void initialize() {
        loginField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (loginField.getParent() != null) {
                if (newVal) {
                    loginField.getParent().getStyleClass().add("input-group-focused");
                } else {
                    loginField.getParent().getStyleClass().remove("input-group-focused");
                }
            }
        });
    }

    @FXML
    protected void onLoginButtonClick() {
        String username = loginField.getText().trim();
        String password = passwordField.getText().trim();

        String serverIp = serverIpField.getText().trim();
        if (serverIp.isEmpty()) {
            serverIp = "localhost";
        }

        if (username.isEmpty() || password.isEmpty()) return;

        statusLabel.setText("Connecting...");
        statusLabel.getStyleClass().removeAll("error-label", "info-label");
        statusLabel.getStyleClass().add("info-label");

        connection = new ServerConnection(serverIp);

        connection.setOnMessageReceived(xmlData -> {
            Platform.runLater(() -> {
                XMLProtocol.ParsedPacket packet = XMLProtocol.parse(xmlData);

                if ("error".equals(packet.type)) {
                    statusLabel.setText(packet.text);
                    statusLabel.getStyleClass().removeAll("error-label", "info-label");
                    statusLabel.getStyleClass().add("error-label");
                    connection.closeConnections();
                }
                else if ("auth_success".equals(packet.type)) {
                    if (chatController == null) {
                        openChatWindow(username, packet.name, packet.phone);
                    }
                }
                else {
                    if (chatController != null) {
                        chatController.processIncomingPacket(packet);
                    }
                }
            });
        });

        String hashedPassword = SecurityUtil.hashPassword(password);
        String authXml = XMLProtocol.buildAuthPacket(username, hashedPassword);
        connection.sendMessage(authXml);
    }

    private void openChatWindow(String currentUsername, String realName, String phone) {
        try {
            Stage stage = (Stage) loginField.getScene().getWindow();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/messengerclient/views/chat-view.fxml"));
            Scene scene = new Scene(loader.load(), 800, 600);

            scene.getStylesheets().add(getClass().getResource("/org/example/messengerclient/styles/style.css").toExternalForm());

            chatController = loader.getController();
            chatController.initConnection(connection, currentUsername, realName, phone);

            stage.setMinWidth(800);
            stage.setMinHeight(550);

            stage.setScene(scene);
            stage.setTitle("Messenger — " + realName);
            stage.setResizable(true);
            stage.centerOnScreen();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to open chat window", e);
        }
    }

    @FXML
    protected void onRegisterLinkClick() {
        try {
            Stage stage = (Stage) loginField.getScene().getWindow();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/messengerclient/views/register-view.fxml"));
            Scene scene = new Scene(loader.load(), 400, 600);
            scene.getStylesheets().add(getClass().getResource("/org/example/messengerclient/styles/style.css").toExternalForm());
            stage.setScene(scene);
            stage.setTitle("Messenger - Registration");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to load registration window", e);
        }
    }
}