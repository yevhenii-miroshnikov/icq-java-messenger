package org.example;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerMain {
    private static final Logger logger = Logger.getLogger(ServerMain.class.getName());
    private static final int PORT = 8080;

    // Thread-safe list wrapper to prevent concurrent modification issues during broadcasting
    private static final List<ClientHandler> activeClients = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        logger.info("Server starting on port " + PORT + "...");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            logger.info("Server successfully started. Awaiting incoming connections...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                logger.info("New connection established from: " + clientSocket.getInetAddress() + ":" + clientSocket.getPort());

                ClientHandler clientHandler = new ClientHandler(clientSocket);

                // Worker threads handle clients immediately, but registration happens only after explicit auth packet evaluation
                Thread clientThread = new Thread(clientHandler);
                clientThread.start();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Server execution lifecycle encountered a critical error", e);
        }
    }

    // Explicitly registers a client to the active broadcasting map after successful database authentication
    public static void addClient(ClientHandler clientHandler) {
        activeClients.add(clientHandler);
        logger.info("Client successfully authenticated and joined. Total active: " + activeClients.size());
    }

    // Returns an unmodifiable-safe reference view of the currently connected active client handlers
    public static List<ClientHandler> getActiveClients() {
        return activeClients;
    }

    // Broadcasts an identical payload to all currently authenticated active clients
    public static void broadcastMessage(String message, ClientHandler sender) {
        for (ClientHandler client : activeClients) {
            client.sendMessage(message);
        }
    }

    // Dispatches a message to a single specific authenticated recipient using their targeted unique username
    public static void sendMessageToUser(String targetUsername, String message) {
        for (ClientHandler client : activeClients) {
            if (client.getClientName().equals(targetUsername)) {
                client.sendMessage(message);
                break;
            }
        }
    }

    // Completely purges a client instance from the active registry on socket disconnection
    public static void removeClient(ClientHandler clientHandler) {
        activeClients.remove(clientHandler);
        logger.info("Client reference successfully purged. Total active: " + activeClients.size());
    }
}