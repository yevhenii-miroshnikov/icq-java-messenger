package org.example;

import org.example.database.DatabaseManager;
import org.example.models.Message;
import org.example.models.User;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Base64;

public class ClientHandler implements Runnable {
    private static final Logger logger = Logger.getLogger(ClientHandler.class.getName());

    private final Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String clientName;
    private String realName;
    private boolean isAuthenticated = false;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        try {
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new PrintWriter(socket.getOutputStream(), true);
            this.clientName = "User_" + socket.getPort();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Client stream initialization failed: " + e.getMessage());
        }
    }

    public String getClientName() {
        return clientName;
    }

    public String getRealName() {
        return realName;
    }

    @Override
    public void run() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                XMLProtocol.ParsedPacket packet = XMLProtocol.parse(message);

                // --- 1. AUTHENTICATION PROCESSING ---
                if ("auth".equals(packet.type)) {
                    // Prevent concurrent duplicate sessions for the same profile
                    boolean alreadyOnline = false;
                    for (ClientHandler ch : ServerMain.getActiveClients()) {
                        if (ch.getClientName().equals(packet.username)) {
                            alreadyOnline = true;
                            break;
                        }
                    }

                    if (alreadyOnline) {
                        logger.warning("Blocked duplicate session attempt for: @" + packet.username);
                        sendMessage(XMLProtocol.buildErrorPacket("This account is already logged in from another window!"));
                        closeConnections();
                        return;
                    }

                    boolean isValid = DatabaseManager.authenticateUser(packet.username, packet.password);

                    if (isValid) {
                        this.clientName = packet.username;
                        this.isAuthenticated = true;

                        User user = DatabaseManager.findUserByUsername(this.clientName);
                        this.realName = (user != null) ? user.getName() : this.clientName;
                        String userPhone = (user != null && user.getPhone() != null) ? user.getPhone() : "";

                        logger.info("Authentication successful for: @" + clientName + " (" + realName + ")");
                        ServerMain.addClient(this);

                        sendMessage(XMLProtocol.buildAuthSuccessPacket(this.realName, userPhone));

                        // Load private message history limit to last 50 entries
                        List<Message> history = DatabaseManager.getUserMessagesHistory(this.clientName, 50);
                        for (Message msg : history) {
                            String historyXml = XMLProtocol.buildMessagePacket(
                                    String.valueOf(msg.getId()),
                                    msg.getSender(),
                                    msg.getReceiver(),
                                    msg.getText(),
                                    msg.getTimestamp() != null ? msg.getTimestamp().toString() : "",
                                    String.valueOf(msg.isRead()),
                                    msg.getReadTime() != null ? msg.getReadTime().toString() : "",
                                    String.valueOf(msg.isEdited())
                            );
                            sendMessage(historyXml);
                        }

                        // Broadcast active roster states to the newly joined client
                        for (ClientHandler ch : ServerMain.getActiveClients()) {
                            if (!ch.getClientName().equals(this.clientName)) {
                                sendMessage(XMLProtocol.buildStatusPacket(ch.getClientName(), ch.getRealName(), "online", ""));
                            }
                        }

                        // Broadcast presence update to everyone else
                        String myStatusXml = XMLProtocol.buildStatusPacket(this.clientName, this.realName, "online", "");
                        ServerMain.broadcastMessage(myStatusXml, this);

                    } else {
                        logger.warning("Failed login attempt for: " + packet.username);
                        sendMessage(XMLProtocol.buildErrorPacket("Invalid @username or password!"));
                        closeConnections();
                        return;
                    }
                }
                // --- 2. REGISTRATION PROCESSING ---
                else if ("register".equals(packet.type)) {
                    boolean success = DatabaseManager.registerUser(packet.name, packet.username, packet.phone, packet.password);
                    if (success) {
                        sendMessage(XMLProtocol.buildSystemPacket("Registration successful"));
                    } else {
                        sendMessage(XMLProtocol.buildErrorPacket("This @username is already taken!"));
                    }
                    closeConnections();
                    return;
                }
                // --- 3. AUTHENTICATED REQUEST ROUTING ---
                else if (isAuthenticated) {

                    // Profile updates routing
                    if ("update_profile".equals(packet.type)) {
                        boolean success = DatabaseManager.updateUserProfile(this.clientName, packet.name, packet.phone);
                        if (success) {
                            this.realName = packet.name;

                            sendMessage(XMLProtocol.buildProfileUpdatedPacket(packet.name, packet.phone));

                            String statusXml = XMLProtocol.buildStatusPacket(this.clientName, this.realName, "online", "");
                            ServerMain.broadcastMessage(statusXml, this);
                        } else {
                            sendMessage(XMLProtocol.buildErrorPacket("Error updating profile details."));
                        }
                    }
                    // Password change security validation routing
                    else if ("change_password".equals(packet.type)) {
                        boolean success = DatabaseManager.updateUserPassword(this.clientName, packet.password, packet.newPassword);
                        if (success) {
                            sendMessage(XMLProtocol.buildPasswordChangedPacket());
                        } else {
                            sendMessage(XMLProtocol.buildErrorPacket("Security error: invalid current password!"));
                        }
                    }
                    // Real-time directory query translation
                    else if ("search".equals(packet.type)) {
                        List<User> users = DatabaseManager.searchUsersByPrefix(packet.query, 15);
                        if (!users.isEmpty()) {
                            java.util.Map<String, String> usersMap = new java.util.HashMap<>();
                            for (User u : users) {
                                usersMap.put(u.getUsername(), u.getName());
                            }
                            sendMessage(XMLProtocol.buildSearchResultPacket("found", usersMap));

                            for (User u : users) {
                                if (u.getUsername().equals(this.clientName)) continue;

                                boolean isOnline = false;
                                for (ClientHandler ch : ServerMain.getActiveClients()) {
                                    if (ch.getClientName().equals(u.getUsername())) {
                                        isOnline = true;
                                        break;
                                    }
                                }
                                String lastSeenStr = u.getLastSeen() != null ? u.getLastSeen().toString() : "";
                                sendMessage(XMLProtocol.buildStatusPacket(u.getUsername(), u.getName(), isOnline ? "online" : "offline", lastSeenStr));
                            }
                        } else {
                            sendMessage(XMLProtocol.buildSearchResultPacket("not_found", null));
                        }
                    }
                    // On-demand file retrieval routing from server storage registry
                    else if ("request_file".equals(packet.type)) {
                        try {
                            long msgId = Long.parseLong(packet.id);
                            String dbText = DatabaseManager.getMessageTextById(msgId);

                            if (dbText != null && (dbText.startsWith("[File]: ") || dbText.startsWith("[Файл]: "))) {
                                String filename = dbText.replaceAll("^\\[(File|Файл)\\]: ", "");
                                File fileToSend = new File("server_files", filename);

                                if (fileToSend.exists()) {
                                    byte[] fileBytes = java.nio.file.Files.readAllBytes(fileToSend.toPath());
                                    String base64Content = Base64.getEncoder().encodeToString(fileBytes);

                                    String cleanName = filename;
                                    if (cleanName.matches("^\\d{13}_.*")) {
                                        cleanName = cleanName.substring(14);
                                    }

                                    String responseXml = XMLProtocol.buildFileResponsePacket(packet.id, cleanName, base64Content);
                                    sendMessage(responseXml);
                                } else {
                                    sendMessage(XMLProtocol.buildErrorPacket("Requested file asset no longer exists on the server."));
                                }
                            }
                        } catch (Exception e) {
                            logger.log(Level.SEVERE, "Error processing on-demand file transmission packet", e);
                        }
                    }
                    // Standard message transmission
                    else if ("message".equals(packet.type)) {
                        Message savedMessage = DatabaseManager.saveMessage(this.clientName, packet.receiver, packet.text);
                        if (savedMessage != null) {
                            String msgXml = XMLProtocol.buildMessagePacket(
                                    String.valueOf(savedMessage.getId()),
                                    this.clientName,
                                    packet.receiver,
                                    packet.text,
                                    savedMessage.getTimestamp().toString(),
                                    "false",
                                    "",
                                    "false"
                            );
                            ServerMain.sendMessageToUser(packet.receiver, msgXml);
                            sendMessage(msgXml);
                        }
                    }
                    // Read receipt acknowledgments
                    else if ("read_receipt".equals(packet.type)) {
                        Message readMsg = DatabaseManager.markMessageAsRead(Long.parseLong(packet.id));
                        if (readMsg != null) {
                            ServerMain.sendMessageToUser(readMsg.getSender(), message);
                        }
                    }
                    // Message revision processing
                    else if ("edit".equals(packet.type)) {
                        Message editedMessage = DatabaseManager.editMessage(Long.parseLong(packet.id), packet.text);
                        if (editedMessage != null) {
                            String editXml = XMLProtocol.buildEditPacket(String.valueOf(editedMessage.getId()), packet.text);
                            ServerMain.sendMessageToUser(editedMessage.getReceiver(), editXml);
                            sendMessage(editXml);
                        }
                    }
                    // Message destruction processing
                    else if ("delete".equals(packet.type)) {
                        DatabaseManager.deleteMessage(Long.parseLong(packet.id));
                        ServerMain.broadcastMessage(message, this);
                    }
                    // Ephemeral typing indicators
                    else if ("typing".equals(packet.type)) {
                        String typingXml = XMLProtocol.buildTypingPacket(this.clientName, packet.receiver);
                        ServerMain.sendMessageToUser(packet.receiver, typingXml);
                    }
                    // Binary file storage processing
                    else if ("file".equals(packet.type)) {
                        try {
                            File serverDir = new File("server_files");
                            if (!serverDir.exists()) serverDir.mkdir();

                            String safeFilename = packet.filename.replaceAll("[^a-zA-Z0-9.-]", "_");
                            String uniqueFilename = System.currentTimeMillis() + "_" + safeFilename;
                            File savedFile = new File(serverDir, uniqueFilename);

                            byte[] decodedBytes = Base64.getDecoder().decode(packet.fileContent);
                            try (FileOutputStream fos = new FileOutputStream(savedFile)) {
                                fos.write(decodedBytes);
                            }

                            String dbText = "[File]: " + uniqueFilename;
                            Message dbMessage = DatabaseManager.saveMessage(this.clientName, packet.receiver, dbText);

                            if (dbMessage != null) {
                                String fileXml = XMLProtocol.buildFilePacket(
                                        String.valueOf(dbMessage.getId()),
                                        this.clientName,
                                        packet.receiver,
                                        uniqueFilename,
                                        packet.fileContent
                                );
                                ServerMain.sendMessageToUser(packet.receiver, fileXml);
                                sendMessage(fileXml);
                            }
                        } catch (Exception e) {
                            logger.log(Level.SEVERE, "Error processing file upload on server", e);
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.warning("Connection lost with @" + clientName);
        } finally {
            // Absolute session safety cleanup block
            ServerMain.removeClient(this);

            if (isAuthenticated && clientName != null && !clientName.startsWith("User_")) {
                DatabaseManager.updateLastSeen(clientName);
                String now = java.time.LocalDateTime.now().toString();
                String offlineXml = XMLProtocol.buildStatusPacket(clientName, realName, "offline", now);
                ServerMain.broadcastMessage(offlineXml, this);
            }

            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Error closing client resources in finally", e);
            }
        }
    }

    public void sendMessage(String message) {
        if (out != null) out.println(message);
    }

    private void closeConnections() {
        ServerMain.removeClient(this);
        if (isAuthenticated && clientName != null && !clientName.startsWith("User_")) {
            DatabaseManager.updateLastSeen(clientName);
            String now = java.time.LocalDateTime.now().toString();
            String offlineXml = XMLProtocol.buildStatusPacket(clientName, realName, "offline", now);
            ServerMain.broadcastMessage(offlineXml, this);
        }
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error during client resource teardown", e);
        }
    }
}
