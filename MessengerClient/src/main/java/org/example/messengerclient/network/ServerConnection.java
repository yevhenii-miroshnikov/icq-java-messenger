package org.example.messengerclient.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerConnection {
    private static final Logger logger = Logger.getLogger(ServerConnection.class.getName());

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Consumer<String> onMessageReceived;

    public ServerConnection() {
        this("localhost");
    }

    // Instantiates a remote connection socket mapping targeted host IP address parameters
    public ServerConnection(String host) {
        try {
            socket = new Socket(host, 8080);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            Thread listenerThread = new Thread(this::listenForMessages);
            listenerThread.setDaemon(true);
            listenerThread.start();
            logger.info("Successfully connected to the server at: " + host);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to connect to the server at: " + host, e);
        }
    }

    public void setOnMessageReceived(Consumer<String> callback) {
        this.onMessageReceived = callback;
    }

    // Background listener thread executing synchronous inbound read operations
    private void listenForMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                if (onMessageReceived != null) {
                    onMessageReceived.accept(message);
                }
            }
        } catch (IOException e) {
            logger.warning("Connection to the server lost.");
            if (onMessageReceived != null) {
                onMessageReceived.accept("[System]: Connection to the server lost.");
            }
        } finally {
            closeConnections();
        }
    }

    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    // Performs strict clean teardown operations on operational network socket handlers
    public void closeConnections() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error encountered during socket resources closure", e);
        }
    }
}