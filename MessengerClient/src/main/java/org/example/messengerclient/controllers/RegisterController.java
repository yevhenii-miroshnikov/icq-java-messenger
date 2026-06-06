package org.example.messengerclient.controllers;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.example.messengerclient.network.ServerConnection;
import org.example.messengerclient.network.XMLProtocol;
import org.example.messengerclient.network.SecurityUtil;

import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RegisterController {
    private static final Logger logger = Logger.getLogger(RegisterController.class.getName());

    @FXML private TextField nameField;
    @FXML private TextField usernameField;
    @FXML private TextField phoneField;

    @FXML private PasswordField passwordField;
    @FXML private TextField passwordTextField;

    @FXML private PasswordField confirmField;
    @FXML private TextField confirmTextField;

    @FXML private Label statusLabel;

    private ServerConnection tempConnection;
    private boolean isPasswordVisible = false;
    private boolean isConfirmVisible = false;

    @FXML
    public void initialize() {
        // Bidirectional synchronization between masked and plain text password entries
        passwordTextField.textProperty().bindBidirectional(passwordField.textProperty());
        confirmTextField.textProperty().bindBidirectional(confirmField.textProperty());

        // TextFormatter restriction filter to constrain input to digital telephone notation format limits
        UnaryOperator<TextFormatter.Change> phoneFilter = change -> {
            String newText = change.getControlNewText();
            if (newText.length() > 15) {
                return null;
            }
            if (newText.matches("\\+?\\d*")) {
                return change;
            }
            return null;
        };
        phoneField.setTextFormatter(new TextFormatter<>(phoneFilter));

        // Binds contextual pseudo-classes to display active outline states on focus change events
        usernameField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (usernameField.getParent() != null) {
                if (newVal) {
                    usernameField.getParent().getStyleClass().add("input-group-focused");
                } else {
                    usernameField.getParent().getStyleClass().remove("input-group-focused");
                }
            }
        });

        javafx.beans.value.ChangeListener<Boolean> passwordFocusListener = (obs, oldVal, newVal) -> {
            if (passwordField.getParent() != null && passwordField.getParent().getParent() != null) {
                if (newVal) {
                    passwordField.getParent().getParent().getStyleClass().add("input-group-focused");
                } else {
                    passwordField.getParent().getParent().getStyleClass().remove("input-group-focused");
                }
            }
        };
        passwordField.focusedProperty().addListener(passwordFocusListener);
        passwordTextField.focusedProperty().addListener(passwordFocusListener);

        javafx.beans.value.ChangeListener<Boolean> confirmFocusListener = (obs, oldVal, newVal) -> {
            if (confirmField.getParent() != null && confirmField.getParent().getParent() != null) {
                if (newVal) {
                    confirmField.getParent().getParent().getStyleClass().add("input-group-focused");
                } else {
                    confirmField.getParent().getParent().getStyleClass().remove("input-group-focused");
                }
            }
        };
        confirmField.focusedProperty().addListener(confirmFocusListener);
        confirmTextField.focusedProperty().addListener(confirmFocusListener);
    }

    @FXML
    protected void togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible;
        passwordTextField.setVisible(isPasswordVisible);
        passwordField.setVisible(!isPasswordVisible);

        if (isPasswordVisible) {
            passwordTextField.requestFocus();
            passwordTextField.end();
        } else {
            passwordField.requestFocus();
            passwordField.end();
        }
    }

    @FXML
    protected void toggleConfirmVisibility() {
        isConfirmVisible = !isConfirmVisible;
        confirmTextField.setVisible(isConfirmVisible);
        confirmField.setVisible(!isConfirmVisible);

        if (isConfirmVisible) {
            confirmTextField.requestFocus();
            confirmTextField.end();
        } else {
            confirmField.requestFocus();
            confirmField.end();
        }
    }

    @FXML
    protected void onRegisterButtonClick() {
        String name = nameField.getText().trim();
        String username = usernameField.getText().trim();
        String phone = phoneField.getText().trim();
        String password = passwordField.getText();
        String confirm = confirmField.getText();

        if (name.isEmpty() || username.isEmpty() || phone.isEmpty() || password.isEmpty()) {
            showError("All fields are required!");
            return;
        }
        if (!password.equals(confirm)) {
            showError("Passwords do not match!");
            return;
        }
        if (username.contains(" ")) {
            showError("Username cannot contain spaces!");
            return;
        }

        statusLabel.setText("Sending data to server...");
        statusLabel.getStyleClass().removeAll("error-label", "info-label");
        statusLabel.getStyleClass().add("info-label");

        String hashedPassword = SecurityUtil.hashPassword(password);
        String regXml = XMLProtocol.buildRegisterPacket(name, username, phone, hashedPassword);

        tempConnection = new ServerConnection();
        tempConnection.setOnMessageReceived(xmlData -> {
            Platform.runLater(() -> {
                XMLProtocol.ParsedPacket packet = XMLProtocol.parse(xmlData);

                if ("error".equals(packet.type)) {
                    showError(packet.text);
                    if (tempConnection != null) tempConnection.closeConnections();
                } else if ("system".equals(packet.type)) {
                    showSuccessAndSwitchToLogin();
                }
            });
        });

        tempConnection.sendMessage(regXml);
    }

    private void showError(String msg) {
        statusLabel.setText(msg);
        statusLabel.getStyleClass().removeAll("error-label", "info-label");
        statusLabel.getStyleClass().add("error-label");
    }

    private void showSuccessAndSwitchToLogin() {
        statusLabel.setText("Registration successful! Redirecting to login...");
        statusLabel.getStyleClass().removeAll("error-label", "info-label");
        statusLabel.getStyleClass().add("info-label");

        if (tempConnection != null) tempConnection.closeConnections();

        PauseTransition pause = new PauseTransition(Duration.seconds(2));
        pause.setOnFinished(event -> switchToLogin());
        pause.play();
    }

    @FXML
    protected void onLoginLinkClick() {
        if (tempConnection != null) tempConnection.closeConnections();
        switchToLogin();
    }

    private void switchToLogin() {
        try {
            Stage stage = (Stage) usernameField.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/messengerclient/views/login-view.fxml"));
            Scene scene = new Scene(loader.load(), 400, 450);
            scene.getStylesheets().add(getClass().getResource("/org/example/messengerclient/styles/style.css").toExternalForm());
            stage.setScene(scene);
            stage.setTitle("Messenger - Login");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to return to login window", e);
        }
    }
}