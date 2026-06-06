package org.example.messengerclient.controllers;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.example.messengerclient.models.ChatMessage;
import org.example.messengerclient.network.ServerConnection;
import org.example.messengerclient.network.XMLProtocol;
import org.example.messengerclient.utils.TimeUtil;

import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.AnchorPane;
import javafx.geometry.Pos;
import javafx.scene.shape.Circle;
import javafx.scene.effect.BoxBlur;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Region;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;

import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

import javafx.stage.FileChooser;
import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChatController {

    private static final Logger logger = Logger.getLogger(ChatController.class.getName());

    // Left panel element bindings (Profile & Search directory)
    @FXML private Label myNameLabel;
    @FXML private Label myUsernameLabel;
    @FXML private Label myStatusLabel;
    @FXML private TextField searchField;
    @FXML private ListView<String> usersListView;

    // Right panel element bindings (Active viewport)
    @FXML private ListView<ChatMessage> chatListView;
    @FXML private TextArea messageField;
    @FXML private Label typingLabel;

    // Dynamic split conversational header layout
    @FXML private Label chatHeaderNameLabel;
    @FXML private Label chatHeaderStatusLabel;
    @FXML private Button closeChatButton;

    // Contextual application containers for overlay injection
    @FXML private BorderPane mainChatContainer;
    @FXML private AnchorPane blurOverlay;

    // Ephemeral contextual message editing layouts
    @FXML private HBox editPanel;
    @FXML private Label editMessageLabel;
    @FXML private Button sendButton;

    private String editingMessageId = null;

    // Core observable bindings for collections pipeline
    private final ObservableList<String> activeChatsList = FXCollections.observableArrayList();
    private final ObservableList<String> searchResultsList = FXCollections.observableArrayList();
    private final ObservableList<ChatMessage> allMessages = FXCollections.observableArrayList();
    private FilteredList<ChatMessage> filteredMessages;

    // State cache lookup tables for local UI mapping
    private final Map<String, String> userStatuses = new HashMap<>();
    private final Map<String, String> userNames = new HashMap<>();
    private final Map<String, String> userLastSeen = new HashMap<>();

    private ServerConnection serverConnection;
    private String myLogin;

    private String myPhone = "";
    private Label activeProfileStatusLabel = null;

    private boolean isSwitchingLists = false;
    private long lastTypingTime = 0;
    private final PauseTransition typingTimer = new PauseTransition(Duration.seconds(2));

    @FXML
    public void initialize() {
        usersListView.setItems(activeChatsList);

        // Intercept secondary mouse clicks on layout roster to prevent unintended state shifts
        usersListView.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                event.consume();
            }
        });

        filteredMessages = new FilteredList<>(allMessages, p -> false);
        chatListView.setItems(filteredMessages);

        // Chat list selection listener
        usersListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (isSwitchingLists) return;

            if (newVal != null) {
                if (editingMessageId != null) {
                    onCancelEditClick();
                }

                closeChatButton.setVisible(true);
                closeChatButton.setManaged(true);

                updateChatHeader(newVal);
                updateMessageFilter(newVal);
                scrollToBottom();

                // Generate read receipts acknowledgments for inbound pending entities
                boolean hasUnread = false;
                for (ChatMessage msg : allMessages) {
                    if (msg.getSender().equals(newVal) && msg.getReceiver().equals(myLogin) && !msg.isRead()) {
                        msg.setRead(true);
                        if (serverConnection != null) {
                            serverConnection.sendMessage(XMLProtocol.buildReadReceiptPacket(msg.getId()));
                        }
                        hasUnread = true;
                    }
                }
                if (hasUnread) {
                    chatListView.refresh();
                }

                // Promote transient dynamic directory result into primary layout roster
                if (usersListView.getItems() == searchResultsList) {
                    final String selectedUser = newVal;

                    Platform.runLater(() -> {
                        isSwitchingLists = true;

                        if (!activeChatsList.contains(selectedUser)) {
                            activeChatsList.add(0, selectedUser);
                        }

                        searchField.clear();
                        usersListView.setItems(activeChatsList);
                        usersListView.getSelectionModel().select(selectedUser);

                        isSwitchingLists = false;
                        sortActiveChats();
                    });
                }
            }
        });

        // Dynamic directory lookups state bindings
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (isSwitchingLists) return;

            if (newVal == null || newVal.trim().isEmpty()) {
                usersListView.setItems(activeChatsList);
            } else {
                if (serverConnection != null) {
                    String query = newVal.trim().replace("@", "");
                    serverConnection.sendMessage(XMLProtocol.buildSearchPacket(query));
                }
            }
        });

        // Isolate interactive layer events to block mouse event leaks
        blurOverlay.setPickOnBounds(true);
        blurOverlay.setOnContextMenuRequested(javafx.scene.input.ContextMenuEvent::consume);
        blurOverlay.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.SECONDARY) e.consume();
        });
        blurOverlay.setOnMouseReleased(e -> {
            if (e.getButton() == MouseButton.SECONDARY) e.consume();
        });

        setupUsersListViewCellFactory();
        setupChatListViewCellFactory();

        typingTimer.setOnFinished(event -> typingLabel.setText(""));

        // Throttled typing indicator broadcast tracking
        messageField.textProperty().addListener((observable, oldValue, newValue) -> {
            String selectedChat = usersListView.getSelectionModel().getSelectedItem();
            if (serverConnection != null && !newValue.isEmpty() && selectedChat != null) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastTypingTime > 1500) {
                    lastTypingTime = currentTime;
                    serverConnection.sendMessage(XMLProtocol.buildTypingPacket(myLogin, selectedChat));
                }
            }
        });

        // Handle text submission mechanics and structural line break insertions
        messageField.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                if (event.isShiftDown()) {
                    int caretPosition = messageField.getCaretPosition();
                    messageField.insertText(caretPosition, "\n");
                    event.consume();
                } else {
                    event.consume();
                    onSendButtonClick();
                }
            }
        });

        // Deflect initial structural focus state
        Platform.runLater(() -> {
            if (myNameLabel != null) {
                myNameLabel.requestFocus();
            }
        });
    }

    // Instantiates network baseline connection data and binds credentials mapping
    public void initConnection(ServerConnection conn, String login, String realName, String phone) {
        this.serverConnection = conn;
        this.myLogin = login;
        this.myPhone = (phone == null) ? "" : phone;

        String displayName = (realName == null || realName.trim().isEmpty()) ? login : realName;
        displayNameLabel(displayName);

        myUsernameLabel.setText("@" + login);
        myStatusLabel.setText("Online");

        this.serverConnection.setOnMessageReceived(xmlData -> {
            Platform.runLater(() -> {
                XMLProtocol.ParsedPacket packet = XMLProtocol.parse(xmlData);
                processIncomingPacket(packet);
            });
        });
    }

    private void displayNameLabel(String displayName) {
        myNameLabel.setText(displayName);
    }

    // Refreshes conversation header components based on active data metrics
    private void updateChatHeader(String username) {
        if (username == null) return;
        String status = userStatuses.getOrDefault(username, "offline");
        String lastSeen = userLastSeen.getOrDefault(username, "");

        String statusText;
        chatHeaderStatusLabel.getStyleClass().removeAll("chat-header-status-online", "chat-header-status-offline");

        if ("online".equals(status)) {
            statusText = "online";
            chatHeaderStatusLabel.getStyleClass().add("chat-header-status-online");
        } else {
            statusText = "offline";
            chatHeaderStatusLabel.getStyleClass().add("chat-header-status-offline");
            if (!lastSeen.isEmpty()) {
                String relativeTime = TimeUtil.formatRelativeTime(lastSeen);
                if (!relativeTime.isEmpty()) {
                    statusText = "last seen " + relativeTime;
                }
            }
        }

        String realName = userNames.getOrDefault(username, username);
        if (realName == null || realName.trim().isEmpty()) {
            realName = username;
        }

        chatHeaderNameLabel.setText(realName);
        chatHeaderStatusLabel.setText("@" + username + "  •  " + statusText);

        chatHeaderNameLabel.setCursor(javafx.scene.Cursor.HAND);
        chatHeaderStatusLabel.setCursor(javafx.scene.Cursor.HAND);
        chatHeaderNameLabel.setOnMouseClicked(this::onChatHeaderClick);
        chatHeaderStatusLabel.setOnMouseClicked(this::onChatHeaderClick);
    }

    @FXML
    protected void onCloseChatClick() {
        isSwitchingLists = true;

        usersListView.getSelectionModel().clearSelection();

        chatHeaderNameLabel.setText("Select a chat to start messaging...");
        chatHeaderStatusLabel.setText("");

        chatHeaderNameLabel.setCursor(javafx.scene.Cursor.DEFAULT);
        chatHeaderStatusLabel.setCursor(javafx.scene.Cursor.DEFAULT);
        chatHeaderNameLabel.setOnMouseClicked(null);
        chatHeaderStatusLabel.setOnMouseClicked(null);

        closeChatButton.setVisible(false);
        closeChatButton.setManaged(false);

        if (editingMessageId != null) {
            onCancelEditClick();
        }

        filteredMessages = new FilteredList<>(allMessages, p -> false);
        chatListView.setItems(filteredMessages);

        isSwitchingLists = false;
    }

    private void updateMessageFilter(String selectedChat) {
        filteredMessages.setPredicate(msg -> {
            if (selectedChat == null) return false;

            boolean iSentToHim = msg.getSender().equals(myLogin) && msg.getReceiver().equals(selectedChat);
            boolean heSentToMe = msg.getSender().equals(selectedChat) && msg.getReceiver().equals(myLogin);
            boolean systemForThisChat = "System".equals(msg.getSender()) && selectedChat.equals(msg.getReceiver());

            return iSentToHim || heSentToMe || systemForThisChat;
        });

        boolean hasMessages = filteredMessages.stream()
                .anyMatch(msg -> !"System".equals(msg.getSender()));

        if (!hasMessages) {
            boolean alreadyHasPlaceholder = allMessages.stream()
                    .anyMatch(msg -> "System".equals(msg.getSender())
                            && selectedChat.equals(msg.getReceiver())
                            && msg.getText().contains("Start chatting"));

            if (!alreadyHasPlaceholder) {
                allMessages.add(new ChatMessage("", "System", selectedChat, "💬 It's empty here... Start chatting!"));
            }
        }
    }

    private void sortActiveChats() {
        Platform.runLater(() -> {
            isSwitchingLists = true;
            String selected = usersListView.getSelectionModel().getSelectedItem();

            FXCollections.sort(activeChatsList, (a, b) -> {
                boolean aOnline = "online".equals(userStatuses.getOrDefault(a, "offline"));
                boolean bOnline = "online".equals(userStatuses.getOrDefault(b, "offline"));

                if (aOnline && !bOnline) return -1;
                if (!aOnline && bOnline) return 1;
                return a.compareToIgnoreCase(b);
            });

            if (selected != null) {
                usersListView.getSelectionModel().select(selected);
            }
            isSwitchingLists = false;
        });
    }

    public void processIncomingPacket(XMLProtocol.ParsedPacket packet) {
        if (packet.username != null && !packet.username.isEmpty() && packet.name != null && !packet.name.isEmpty()) {
            userNames.put(packet.username, packet.name);
        }

        if ("system".equals(packet.type)) {
            String targetChat = usersListView.getSelectionModel().getSelectedItem();
            if (targetChat != null) {
                allMessages.add(new ChatMessage("", "System", targetChat, packet.text));
            }
            scrollToBottom();
        }
        else if ("password_changed".equals(packet.type)) {
            Platform.runLater(() -> {
                if (activeProfileStatusLabel != null) {
                    activeProfileStatusLabel.setText("✓ Password successfully changed and saved in the database!");
                    activeProfileStatusLabel.getStyleClass().removeAll("error-label", "info-label");
                    activeProfileStatusLabel.getStyleClass().add("info-label");
                }
            });
        }
        else if ("error".equals(packet.type)) {
            Platform.runLater(() -> {
                if (activeProfileStatusLabel != null) {
                    activeProfileStatusLabel.setText("✕ " + packet.text);
                    activeProfileStatusLabel.getStyleClass().removeAll("error-label", "info-label");
                    activeProfileStatusLabel.getStyleClass().add("error-label");
                } else {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Error");
                    alert.setHeaderText(null);
                    alert.setContentText(packet.text);
                    alert.showAndWait();
                }
            });
        }
        else if ("profile_updated".equals(packet.type)) {
            Platform.runLater(() -> {
                if (packet.name != null && !packet.name.isEmpty()) {
                    displayNameLabel(packet.name);

                    if (myNameLabel.getScene() != null && myNameLabel.getScene().getWindow() instanceof Stage) {
                        ((Stage) myNameLabel.getScene().getWindow()).setTitle("Messenger — " + packet.name);
                    }
                }
                if (packet.phone != null) {
                    this.myPhone = packet.phone;
                }
                if (activeProfileStatusLabel != null) {
                    activeProfileStatusLabel.setText("✓ Profile successfully updated!");
                    activeProfileStatusLabel.getStyleClass().removeAll("error-label", "info-label");
                    activeProfileStatusLabel.getStyleClass().add("info-label");
                }
            });
        }
        else if ("search_result".equals(packet.type)) {
            Platform.runLater(() -> {
                userNames.putAll(packet.foundUsersList);

                String currentQuery = searchField.getText().trim();
                if (!currentQuery.isEmpty()) {
                    searchResultsList.clear();
                    if ("found".equals(packet.status)) {
                        for (String uname : packet.foundUsersList.keySet()) {
                            if (!uname.equals(myLogin)) {
                                searchResultsList.add(uname);
                            }
                        }
                    }
                    usersListView.setItems(searchResultsList);
                } else {
                    usersListView.refresh();
                    String selectedChat = usersListView.getSelectionModel().getSelectedItem();
                    if (selectedChat != null && packet.foundUsersList.containsKey(selectedChat)) {
                        updateChatHeader(selectedChat);
                    }
                }
            });
        }
        else if ("status".equals(packet.type)) {
            userStatuses.put(packet.username, packet.state);
            if (packet.name != null && !packet.name.isEmpty()) {
                userNames.put(packet.username, packet.name);
            }
            if (packet.lastSeen != null && !packet.lastSeen.isEmpty()) {
                userLastSeen.put(packet.username, packet.lastSeen);
            }

            sortActiveChats();

            String selectedChat = usersListView.getSelectionModel().getSelectedItem();
            if (selectedChat != null && packet.username.equals(selectedChat)) {
                updateChatHeader(selectedChat);
            }
            usersListView.refresh();
        }
        else if ("message".equals(packet.type)) {
            String otherUser = packet.sender.equals(myLogin) ? packet.receiver : packet.sender;

            allMessages.removeIf(msg -> "System".equals(msg.getSender())
                    && otherUser.equals(msg.getReceiver())
                    && msg.getText().contains("Start chatting"));

            ChatMessage newMsg = new ChatMessage(packet.id, packet.sender, packet.receiver, packet.text, null, packet.timestamp, packet.isRead, packet.readTime);
            newMsg.setEdited(packet.isEdited);
            allMessages.add(newMsg);

            String selectedChat = usersListView.getSelectionModel().getSelectedItem();
            if (myLogin.equals(packet.receiver) && packet.sender.equals(selectedChat)) {
                newMsg.setRead(true);
                if (serverConnection != null) {
                    serverConnection.sendMessage(XMLProtocol.buildReadReceiptPacket(packet.id));
                }
            }

            if (!otherUser.equals(myLogin) && !activeChatsList.contains(otherUser)) {
                activeChatsList.add(0, otherUser);
                sortActiveChats();

                if (serverConnection != null && !userNames.containsKey(otherUser)) {
                    serverConnection.sendMessage(XMLProtocol.buildSearchPacket(otherUser));
                }
            }

            if (packet.sender.equals(typingLabel.getText().replace(" is typing...", ""))) {
                typingLabel.setText("");
                typingTimer.stop();
            }
            scrollToBottom();
        }
        else if ("read_receipt".equals(packet.type)) {
            for (ChatMessage msg : allMessages) {
                if (msg.getId().equals(packet.id)) {
                    msg.setRead(true);
                    msg.setReadTime(java.time.LocalDateTime.now().toString());
                    chatListView.refresh();
                    break;
                }
            }
        }
        else if ("edit".equals(packet.type)) {
            for (int i = 0; i < allMessages.size(); i++) {
                ChatMessage msg = allMessages.get(i);
                if (msg.getId().equals(packet.id)) {
                    msg.setText(packet.text);
                    msg.setEdited(true);
                    allMessages.set(i, msg);
                    chatListView.refresh();
                    break;
                }
            }
        }
        else if ("delete".equals(packet.type)) {
            allMessages.removeIf(msg -> packet.id.equals(msg.getId()));
        }
        else if ("typing".equals(packet.type)) {
            String currentChat = usersListView.getSelectionModel().getSelectedItem();
            if (packet.receiver.equals(myLogin) && packet.sender.equals(currentChat)) {
                typingLabel.setText(packet.sender + " is typing...");
                typingTimer.playFromStart();
            }
        }
        else if ("file".equals(packet.type)) {
            allMessages.add(new ChatMessage(packet.id, packet.sender, packet.receiver, "[File]: " + packet.filename, packet.fileContent));
            scrollToBottom();
        }
        else if ("file_response".equals(packet.type)) {
            Platform.runLater(() -> {
                javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
                fileChooser.setTitle("Save Requested File from Server");
                fileChooser.setInitialFileName(packet.filename);
                java.io.File saveFile = fileChooser.showSaveDialog(chatListView.getScene().getWindow());

                if (saveFile != null) {
                    try {
                        byte[] decodedBytes = Base64.getDecoder().decode(packet.fileContent);
                        java.nio.file.Files.write(saveFile.toPath(), decodedBytes);
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Failed to save requested binary file to disk", e);
                    }
                }
            });
        }
    }

    @FXML
    protected void onSendButtonClick() {
        String receiver = usersListView.getSelectionModel().getSelectedItem();
        String text = messageField.getText().trim();

        if (receiver == null) return;

        if (!text.isEmpty() && serverConnection != null) {
            if (editingMessageId != null) {
                ChatMessage originalMsg = null;
                for (ChatMessage msg : allMessages) {
                    if (msg.getId().equals(editingMessageId)) {
                        originalMsg = msg;
                        break;
                    }
                }

                if (originalMsg != null && originalMsg.getText().trim().equals(text)) {
                    onCancelEditClick();
                    return;
                }

                String editXml = XMLProtocol.buildEditPacket(editingMessageId, text);
                serverConnection.sendMessage(editXml);

                editPanel.setVisible(false);
                editPanel.setManaged(false);
                sendButton.setText("➜");
                editingMessageId = null;
            } else {
                String msgXml = XMLProtocol.buildMessagePacket("", myLogin, receiver, text, "", "false", "", "false");
                serverConnection.sendMessage(msgXml);
            }
            messageField.clear();
            messageField.requestFocus();
        }
    }

    @FXML
    protected void onCancelEditClick() {
        editingMessageId = null;
        messageField.clear();
        editPanel.setVisible(false);
        editPanel.setManaged(false);
        sendButton.setText("➜");
    }

    @FXML
    protected void onSendFileClick() {
        String receiver = usersListView.getSelectionModel().getSelectedItem();
        if (serverConnection == null || receiver == null) return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select a file/image to send");
        File file = fileChooser.showOpenDialog(messageField.getScene().getWindow());

        if (file != null) {
            if (file.length() > 5 * 1024 * 1024) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("File Error");
                alert.setHeaderText(null);
                alert.setContentText("Error: File is too large (5 MB limit)");
                alert.showAndWait();
                return;
            }
            try {
                byte[] fileBytes = Files.readAllBytes(file.toPath());
                String base64Content = Base64.getEncoder().encodeToString(fileBytes);
                String fileXml = XMLProtocol.buildFilePacket("", myLogin, receiver, file.getName(), base64Content);
                serverConnection.sendMessage(fileXml);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "File reading failed", e);
            }
        }
    }

    private void scrollToBottom() {
        if (chatListView == null || chatListView.getItems() == null || chatListView.getItems().isEmpty()) {
            return;
        }

        javafx.application.Platform.runLater(() -> {
            javafx.application.Platform.runLater(() -> {
                int lastIndex = chatListView.getItems().size() - 1;
                if (lastIndex >= 0) {
                    chatListView.scrollTo(lastIndex);
                }
            });
        });
    }

    @FXML
    protected void onBlurOverlayClick(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY) {
            hideBlurMenu();
        }
    }

    private void hideBlurMenu() {
        activeProfileStatusLabel = null;

        if (blurOverlay != null) {
            blurOverlay.setVisible(false);
            blurOverlay.getChildren().clear();
        }
        if (mainChatContainer != null) {
            mainChatContainer.setEffect(null);
        }
    }

    @FXML
    protected void onProfileClick(MouseEvent event) {
        if (mainChatContainer == null || blurOverlay == null) return;

        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }

        mainChatContainer.setEffect(new BoxBlur(8, 8, 3));
        blurOverlay.getChildren().clear();
        blurOverlay.setVisible(true);

        // Main card container
        VBox card = new VBox(15);
        card.getStyleClass().add("profile-card");
        card.setPadding(new javafx.geometry.Insets(25));
        card.setOnMouseClicked(MouseEvent::consume);

        // Clean header title
        Label title = new Label("My Profile");
        title.getStyleClass().add("header-label");

        activeProfileStatusLabel = new Label();
        activeProfileStatusLabel.setWrapText(true);
        activeProfileStatusLabel.getStyleClass().add("info-label");

        // Field: Username + Copy button
        VBox userBox = new VBox(3);
        Label userLabel = new Label("Username");
        userLabel.getStyleClass().add("profile-card-label");

        HBox userRow = new HBox(10);
        userRow.setAlignment(Pos.CENTER_LEFT);
        Label userValue = new Label("@" + myLogin);
        userValue.getStyleClass().add("profile-card-value");
        Button copyUserBtn = new Button("📋");
        copyUserBtn.getStyleClass().add("copy-button-small");
        copyUserBtn.setOnAction(e -> {
            Clipboard.getSystemClipboard().setContent(Map.of(DataFormat.PLAIN_TEXT, "@" + myLogin));
        });
        userRow.getChildren().addAll(userValue, copyUserBtn);
        userBox.getChildren().addAll(userLabel, userRow);

        // Field: Name + Copy button
        VBox nameBox = new VBox(3);
        Label nameLabel = new Label("Name");
        nameLabel.getStyleClass().add("profile-card-label");

        HBox nameRow = new HBox(10);
        nameRow.setAlignment(Pos.CENTER_LEFT);
        TextField nameInput = new TextField(myNameLabel.getText());
        nameInput.getStyleClass().add("text-field");
        Button copyNameBtn = new Button("📋");
        copyNameBtn.getStyleClass().add("copy-button-medium");
        copyNameBtn.setOnAction(e -> {
            Clipboard.getSystemClipboard().setContent(Map.of(DataFormat.PLAIN_TEXT, nameInput.getText().trim()));
        });
        nameRow.getChildren().addAll(nameInput, copyNameBtn);
        nameBox.getChildren().addAll(nameLabel, nameRow);

        // Field: Phone + Copy button
        VBox phoneBox = new VBox(3);
        Label phoneLabel = new Label("Phone");
        phoneLabel.getStyleClass().add("profile-card-label");

        HBox phoneRow = new HBox(10);
        phoneRow.setAlignment(Pos.CENTER_LEFT);
        TextField phoneInput = new TextField(myPhone);
        phoneInput.getStyleClass().add("text-field");
        Button copyPhoneBtn = new Button("📋");
        copyPhoneBtn.getStyleClass().add("copy-button-medium");
        copyPhoneBtn.setOnAction(e -> {
            Clipboard.getSystemClipboard().setContent(Map.of(DataFormat.PLAIN_TEXT, phoneInput.getText().trim()));
        });
        phoneRow.getChildren().addAll(phoneInput, copyPhoneBtn);
        phoneBox.getChildren().addAll(phoneLabel, phoneRow);

        // Collapsible security section
        VBox securitySection = new VBox(10);
        securitySection.setVisible(false);
        securitySection.setManaged(false);
        securitySection.getStyleClass().add("security-section");

        PasswordField oldPasswordInput = new PasswordField();
        oldPasswordInput.setPromptText("Current password");
        oldPasswordInput.getStyleClass().add("password-field");

        PasswordField newPasswordInput = new PasswordField();
        newPasswordInput.setPromptText("New password");
        newPasswordInput.getStyleClass().add("password-field");

        PasswordField confirmPasswordInput = new PasswordField();
        confirmPasswordInput.setPromptText("Confirm new password");
        confirmPasswordInput.getStyleClass().add("password-field");

        Button changePasswordBtn = new Button("Update Password");
        changePasswordBtn.getStyleClass().add("button");
        changePasswordBtn.setMaxWidth(Double.MAX_VALUE);

        changePasswordBtn.setOnAction(e -> {
            String oldPwd = oldPasswordInput.getText().trim();
            String newPwd = newPasswordInput.getText().trim();
            String confPwd = confirmPasswordInput.getText().trim();

            activeProfileStatusLabel.getStyleClass().removeAll("error-label", "info-label");

            if (oldPwd.isEmpty() || newPwd.isEmpty() || confPwd.isEmpty()) {
                activeProfileStatusLabel.setText("✕ Please fill in all security fields!");
                activeProfileStatusLabel.getStyleClass().add("error-label");
                return;
            }

            if (!newPwd.equals(confPwd)) {
                activeProfileStatusLabel.setText("✕ New passwords do not match!");
                activeProfileStatusLabel.getStyleClass().add("error-label");
                return;
            }

            if (newPwd.equals(oldPwd)) {
                activeProfileStatusLabel.setText("✕ New password matches the current one!");
                activeProfileStatusLabel.getStyleClass().add("error-label");
                return;
            }

            if (serverConnection != null) {
                String oldHash = org.example.messengerclient.network.SecurityUtil.hashPassword(oldPwd);
                String newHash = org.example.messengerclient.network.SecurityUtil.hashPassword(newPwd);

                activeProfileStatusLabel.setText("Sending password change request...");
                activeProfileStatusLabel.getStyleClass().add("info-label");

                serverConnection.sendMessage(XMLProtocol.buildChangePasswordPacket(oldHash, newHash));

                oldPasswordInput.clear();
                newPasswordInput.clear();
                confirmPasswordInput.clear();
            }
        });

        // Enter key handler for password form submission
        javafx.event.EventHandler<javafx.scene.input.KeyEvent> passwordEnterHandler = enterEvent -> {
            if (enterEvent.getCode() == javafx.scene.input.KeyCode.ENTER) {
                changePasswordBtn.fire();
                enterEvent.consume();
            }
        };

        oldPasswordInput.setOnKeyPressed(passwordEnterHandler);
        newPasswordInput.setOnKeyPressed(passwordEnterHandler);
        confirmPasswordInput.setOnKeyPressed(passwordEnterHandler);

        securitySection.getChildren().addAll(
                new Label("Change Security Password:"),
                oldPasswordInput, newPasswordInput, confirmPasswordInput, changePasswordBtn
        );

        Hyperlink toggleSecurityLink = new Hyperlink("🔒 Security & Password Settings");
        toggleSecurityLink.getStyleClass().add("link-button");
        toggleSecurityLink.setOnAction(e -> {
            boolean isVisible = securitySection.isVisible();
            securitySection.setVisible(!isVisible);
            securitySection.setManaged(!isVisible);
            toggleSecurityLink.setText(isVisible ? "🔒 Security & Password Settings" : "🔓 Hide Security Settings");
        });

        HBox btnBox = new HBox(15);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().addAll("button", "btn-secondary");
        cancelBtn.setOnAction(e -> hideBlurMenu());

        Button saveBtn = new Button("Save");
        saveBtn.getStyleClass().add("button");
        saveBtn.setOnAction(e -> {
            String newName = nameInput.getText().trim();
            String newPhone = phoneInput.getText().trim();
            if (!newName.isEmpty() && serverConnection != null) {
                activeProfileStatusLabel.setText("Saving text changes...");
                serverConnection.sendMessage(XMLProtocol.buildUpdateProfilePacket(newName, newPhone));
            }
        });

        btnBox.getChildren().addAll(cancelBtn, saveBtn);

        // Assemble components
        card.getChildren().addAll(title, userBox, nameBox, phoneBox, toggleSecurityLink, securitySection, activeProfileStatusLabel, btnBox);

        // Fixed scroll pane setup
        ScrollPane scrollPane = new ScrollPane(card);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        scrollPane.setMaxHeight(470);
        scrollPane.setMinWidth(360);
        scrollPane.setMaxWidth(360);
        scrollPane.getStyleClass().add("profile-scroll-pane");

        scrollPane.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            if (newSkin != null) {
                javafx.scene.Node viewport = scrollPane.lookup(".viewport");
                if (viewport != null) {
                    viewport.getStyleClass().add("transparent-viewport");
                }
            }
        });

        StackPane centerWrapper = new StackPane(scrollPane);
        centerWrapper.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        centerWrapper.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                hideBlurMenu();
            }
        });

        centerWrapper.setOnMousePressed(e -> { if (e.getButton() == MouseButton.SECONDARY) e.consume(); });
        centerWrapper.setOnMouseReleased(e -> { if (e.getButton() == MouseButton.SECONDARY) e.consume(); });
        centerWrapper.setOnContextMenuRequested(javafx.scene.input.ContextMenuEvent::consume);

        AnchorPane.setTopAnchor(centerWrapper, 0.0);
        AnchorPane.setBottomAnchor(centerWrapper, 0.0);
        AnchorPane.setLeftAnchor(centerWrapper, 0.0);
        AnchorPane.setRightAnchor(centerWrapper, 0.0);

        blurOverlay.getChildren().add(centerWrapper);

        centerWrapper.setScaleX(0.7);
        centerWrapper.setScaleY(0.7);
        centerWrapper.setOpacity(0.0);

        ScaleTransition scale = new ScaleTransition(Duration.millis(150), centerWrapper);
        scale.setToX(1.0);
        scale.setToY(1.0);

        FadeTransition fade = new FadeTransition(Duration.millis(150), centerWrapper);
        fade.setToValue(1.0);

        ParallelTransition pt = new ParallelTransition(scale, fade);
        pt.play();
    }

    @FXML
    protected void onChatHeaderClick(MouseEvent event) {
        if (mainChatContainer == null || blurOverlay == null) return;

        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }

        String username = usersListView.getSelectionModel().getSelectedItem();
        if (username == null) return;

        mainChatContainer.setEffect(new BoxBlur(8, 8, 3));
        blurOverlay.getChildren().clear();
        blurOverlay.setVisible(true);

        // Main card container
        VBox card = new VBox(15);
        card.getStyleClass().add("profile-card");
        card.setPadding(new javafx.geometry.Insets(25));
        card.setOnMouseClicked(MouseEvent::consume);

        Label title = new Label("User Profile");
        title.getStyleClass().add("header-label");

        // Field: Username + Copy button
        VBox userBox = new VBox(3);
        Label userLabel = new Label("Username");
        userLabel.getStyleClass().add("profile-card-label");

        HBox userRow = new HBox(10);
        userRow.setAlignment(Pos.CENTER_LEFT);
        Label userValue = new Label("@" + username);
        userValue.getStyleClass().add("profile-card-value");
        Button copyUserBtn = new Button("📋");
        copyUserBtn.getStyleClass().add("copy-button-small");
        copyUserBtn.setOnAction(e -> {
            Clipboard.getSystemClipboard().setContent(Map.of(DataFormat.PLAIN_TEXT, "@" + username));
        });
        userRow.getChildren().addAll(userValue, copyUserBtn);
        userBox.getChildren().addAll(userLabel, userRow);

        // Field: Name + Copy button
        VBox nameBox = new VBox(3);
        Label nameLabel = new Label("Name");
        nameLabel.getStyleClass().add("profile-card-label");

        HBox nameRow = new HBox(10);
        nameRow.setAlignment(Pos.CENTER_LEFT);
        String realName = userNames.getOrDefault(username, username);
        String finalName = (realName == null || realName.trim().isEmpty()) ? username : realName;
        Label nameValue = new Label(finalName);
        nameValue.getStyleClass().add("profile-card-value");
        Button copyNameBtn = new Button("📋");
        copyNameBtn.getStyleClass().add("copy-button-small");
        copyNameBtn.setOnAction(e -> {
            Clipboard.getSystemClipboard().setContent(Map.of(DataFormat.PLAIN_TEXT, finalName));
        });
        nameRow.getChildren().addAll(nameValue, copyNameBtn);
        nameBox.getChildren().addAll(nameLabel, nameRow);

        // Secure field: Phone (Hidden by privacy limitations)
        VBox phoneBox = new VBox(3);
        Label phoneLabel = new Label("Phone");
        phoneLabel.getStyleClass().add("profile-card-label");
        Label phoneValue = new Label("🔒 Hidden by privacy");
        phoneValue.getStyleClass().add("profile-privacy-placeholder");
        phoneBox.getChildren().addAll(phoneLabel, phoneValue);

        // Field: Network Status & Last Seen data mapping
        VBox statusBox = new VBox(3);
        Label statusLabel = new Label("Network Status");
        statusLabel.getStyleClass().add("profile-card-label");

        String status = userStatuses.getOrDefault(username, "offline");
        String lastSeen = userLastSeen.getOrDefault(username, "");
        String statusText = "offline";

        if ("online".equals(status)) {
            statusText = "online";
        } else if (!lastSeen.isEmpty()) {
            String relativeTime = TimeUtil.formatRelativeTime(lastSeen);
            if (!relativeTime.isEmpty()) {
                statusText = "last seen " + relativeTime;
            }
        }

        Label statusValue = new Label(statusText);
        statusValue.getStyleClass().add("profile-card-value");
        statusValue.getStyleClass().add("online".equals(status) ? "status-value-online" : "status-value-offline");
        statusBox.getChildren().addAll(statusLabel, statusValue);

        HBox btnBox = new HBox(15);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("button");
        closeBtn.setOnAction(e -> hideBlurMenu());
        btnBox.getChildren().add(closeBtn);

        card.getChildren().addAll(title, userBox, nameBox, phoneBox, statusBox, btnBox);

        // Fixed scroll pane setup
        ScrollPane scrollPane = new ScrollPane(card);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        scrollPane.setMaxHeight(400);
        scrollPane.setMinWidth(360);
        scrollPane.setMaxWidth(360);
        scrollPane.getStyleClass().add("profile-scroll-pane");

        scrollPane.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            if (newSkin != null) {
                javafx.scene.Node viewport = scrollPane.lookup(".viewport");
                if (viewport != null) {
                    viewport.getStyleClass().add("transparent-viewport");
                }
            }
        });

        StackPane centerWrapper = new StackPane(scrollPane);
        centerWrapper.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        centerWrapper.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                hideBlurMenu();
            }
        });

        centerWrapper.setOnMousePressed(e -> { if (e.getButton() == MouseButton.SECONDARY) e.consume(); });
        centerWrapper.setOnMouseReleased(e -> { if (e.getButton() == MouseButton.SECONDARY) e.consume(); });
        centerWrapper.setOnContextMenuRequested(javafx.scene.input.ContextMenuEvent::consume);

        AnchorPane.setTopAnchor(centerWrapper, 0.0);
        AnchorPane.setBottomAnchor(centerWrapper, 0.0);
        AnchorPane.setLeftAnchor(centerWrapper, 0.0);
        AnchorPane.setRightAnchor(centerWrapper, 0.0);

        blurOverlay.getChildren().add(centerWrapper);

        centerWrapper.setScaleX(0.7);
        centerWrapper.setScaleY(0.7);
        centerWrapper.setOpacity(0.0);

        ScaleTransition scale = new ScaleTransition(Duration.millis(150), centerWrapper);
        scale.setToX(1.0);
        scale.setToY(1.0);

        FadeTransition fade = new FadeTransition(Duration.millis(150), centerWrapper);
        fade.setToValue(1.0);

        ParallelTransition pt = new ParallelTransition(scale, fade);
        pt.play();
    }

    // Triggers custom blur context menu mapping for message interactions
    private void showCustomBlurMenu(ChatMessage item, Label originalBubble, VBox originalWrapper) {
        if (mainChatContainer == null || blurOverlay == null) return;

        mainChatContainer.setEffect(new BoxBlur(8, 8, 3));
        blurOverlay.getChildren().clear();
        blurOverlay.setVisible(true);

        javafx.geometry.Bounds bounds = originalWrapper.localToScene(originalWrapper.getBoundsInLocal());

        VBox overlayContent = new VBox(10);
        overlayContent.setFillWidth(false);
        double posX = bounds.getMinX();
        double posY = bounds.getMinY();

        if (posY + 180 > blurOverlay.getHeight() && blurOverlay.getHeight() > 0) {
            posY = blurOverlay.getHeight() - 200;
        }
        if (posX + 220 > blurOverlay.getWidth() && blurOverlay.getWidth() > 0) {
            posX = blurOverlay.getWidth() - 240;
        }
        overlayContent.setLayoutX(posX);
        overlayContent.setLayoutY(Math.max(10, posY));

        VBox clonedWrapper = new VBox(2);
        clonedWrapper.setAlignment(originalWrapper.getAlignment());

        Label clonedBubble = new Label(originalBubble.getText());
        clonedBubble.getStyleClass().addAll(originalBubble.getStyleClass());
        clonedBubble.setWrapText(true);
        clonedBubble.setPrefWidth(originalBubble.getWidth());

        String timeStr = TimeUtil.formatMessageTime(item.getTimestamp());
        if (timeStr.isEmpty()) {
            timeStr = TimeUtil.formatMessageTime(java.time.LocalDateTime.now().toString());
        }

        // Render localized visual overlays for outbound versus inbound message items
        if (item.getSender().equals(myLogin)) {
            HBox metaRow = new HBox(4);
            metaRow.setAlignment(Pos.CENTER_RIGHT);
            if (item.isEdited()) {
                Label editedLabel = new Label("edited");
                editedLabel.getStyleClass().add("chat-message-edited-right");
                metaRow.getChildren().add(editedLabel);
            }
            Label timeLabel = new Label(timeStr);
            timeLabel.getStyleClass().add("chat-message-time");
            Label checksLabel = new Label(item.isRead() ? "✓✓" : "✓");
            checksLabel.getStyleClass().add("chat-message-checks");
            metaRow.getChildren().addAll(timeLabel, checksLabel);
            clonedWrapper.getChildren().addAll(clonedBubble, metaRow);
        } else {
            String realSenderName = userNames.getOrDefault(item.getSender(), item.getSender());
            Label senderLabel = new Label(realSenderName);
            senderLabel.getStyleClass().add("chat-message-sender");
            HBox metaRow = new HBox(4);
            metaRow.setAlignment(Pos.CENTER_LEFT);
            Label timeLabel = new Label(timeStr);
            timeLabel.getStyleClass().add("chat-message-time");
            metaRow.getChildren().addAll(timeLabel);
            if (item.isEdited()) {
                Label editedLabel = new Label("edited");
                editedLabel.getStyleClass().add("chat-message-edited-left");
                metaRow.getChildren().add(editedLabel);
            }
            clonedWrapper.getChildren().addAll(senderLabel, clonedBubble, metaRow);
        }

        VBox customMenu = new VBox();
        customMenu.getStyleClass().add("custom-context-menu");
        customMenu.setMinWidth(200);

        // Copy item routing
        if (item.getFileBase64() == null) {
            HBox copyItem = new HBox();
            copyItem.getStyleClass().add("custom-menu-item");
            Label copyText = new Label("📋 Copy Text");
            copyText.getStyleClass().add("custom-menu-item-text");
            copyItem.getChildren().add(copyText);

            copyItem.setOnMouseClicked(e -> {
                if (e.getButton() != MouseButton.PRIMARY) return;
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent content = new ClipboardContent();
                content.putString(item.getText());
                clipboard.setContent(content);
                hideBlurMenu();
            });
            customMenu.getChildren().add(copyItem);
        }

        // Context items for self outbound messages
        if (item.getSender().equals(myLogin) && !item.getId().isEmpty()) {
            boolean isItemFile = item.getFileBase64() != null || (item.getText() != null && item.getText().startsWith("[File]: "));

            if (!isItemFile) {
                HBox editItem = new HBox();
                editItem.getStyleClass().add("custom-menu-item");
                Label editText = new Label("✎ Edit Message");
                editText.getStyleClass().add("custom-menu-item-text");
                editItem.getChildren().add(editText);

                editItem.setOnMouseClicked(e -> {
                    if (e.getButton() != MouseButton.PRIMARY) return;
                    editingMessageId = item.getId();
                    String originalText = item.getText();
                    messageField.setText(originalText);

                    editMessageLabel.setText(originalText);
                    editPanel.setVisible(true);
                    editPanel.setManaged(true);
                    sendButton.setText("✓");

                    messageField.requestFocus();
                    messageField.end();
                    hideBlurMenu();
                });
                customMenu.getChildren().add(editItem);
            }

            // Delete item routing (Available for both text and files)
            HBox deleteItem = new HBox();
            deleteItem.getStyleClass().addAll("custom-menu-item", "custom-menu-item-danger");
            Label deleteText = new Label("🗑 Delete Message");
            deleteText.getStyleClass().add("custom-menu-item-text");
            deleteItem.getChildren().add(deleteText);

            deleteItem.setOnMouseClicked(e -> {
                if (e.getButton() != MouseButton.PRIMARY) return;
                serverConnection.sendMessage(XMLProtocol.buildDeletePacket(item.getId()));
                hideBlurMenu();
            });
            customMenu.getChildren().add(deleteItem);

            // Descriptive message read receipts telemetry layout
            Label statusLabel = new Label();
            statusLabel.getStyleClass().add("custom-menu-status-text");

            if (item.isRead()) {
                String readTimeStr = TimeUtil.formatRelativeTime(item.getReadTime());
                if (!readTimeStr.isEmpty()) {
                    statusLabel.setText("✓✓ Read " + readTimeStr);
                } else {
                    statusLabel.setText("✓✓ Read");
                }
            } else {
                statusLabel.setText("✓ Delivered to server");
            }

            HBox statusItem = new HBox(statusLabel);
            statusItem.setAlignment(Pos.CENTER);
            statusItem.getStyleClass().add("custom-menu-status-container");
            customMenu.getChildren().add(statusItem);
        }

        overlayContent.getChildren().addAll(clonedWrapper, customMenu);
        blurOverlay.getChildren().add(overlayContent);

        customMenu.setScaleX(0.4);
        customMenu.setScaleY(0.4);
        customMenu.setOpacity(0.0);

        ScaleTransition scale = new ScaleTransition(Duration.millis(140), customMenu);
        scale.setToX(1.0);
        scale.setToY(1.0);

        FadeTransition fade = new FadeTransition(Duration.millis(140), customMenu);
        fade.setToValue(1.0);

        ParallelTransition pt = new ParallelTransition(scale, fade);
        pt.play();
    }

    // Direct cell factory renderer instantiation for directory layout list views
    private void setupUsersListViewCellFactory() {
        usersListView.setCellFactory(lv -> new ListCell<String>() {
            private final HBox container;
            private final Circle dot;
            private final VBox textContainer;
            private final Label nameLabel;
            private final Label usernameLabel;

            {
                container = new HBox(8);
                dot = new Circle(5);
                textContainer = new VBox(2);
                nameLabel = new Label();
                usernameLabel = new Label();

                container.setAlignment(Pos.CENTER_LEFT);
                nameLabel.getStyleClass().add("sidebar-item-label");
                usernameLabel.getStyleClass().add("sidebar-item-username");

                textContainer.getChildren().addAll(nameLabel, usernameLabel);
                container.getChildren().addAll(dot, textContainer);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    String realName = userNames.getOrDefault(item, item);
                    if (realName == null || realName.trim().isEmpty()) {
                        realName = item;
                    }

                    nameLabel.setText(realName);
                    usernameLabel.setText("@" + item);

                    String status = userStatuses.getOrDefault(item, "offline");

                    dot.getStyleClass().removeAll("status-dot-online", "status-dot-offline");
                    if ("online".equals(status)) {
                        dot.getStyleClass().add("status-dot-online");
                    } else {
                        dot.getStyleClass().add("status-dot-offline");
                    }

                    setGraphic(container);
                }
            }
        });
    }

    // Custom message cell factory layout engine (Bubbles, Context menus, and Date capsules)
    private void setupChatListViewCellFactory() {
        chatListView.setCellFactory(lv -> new ListCell<ChatMessage>() {
            {
                setWrapText(true);
                prefWidthProperty().bind(lv.widthProperty().subtract(20));
            }

            @Override
            protected void updateItem(ChatMessage item, boolean empty) {
                super.updateItem(item, empty);

                getStyleClass().removeAll("system-message", "my-message", "other-message", "file-message");

                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setContextMenu(null);
                    setOnMouseClicked(null);
                } else {
                    setText(null);
                    setContextMenu(null);

                    String displayText = item.getText();
                    String cleanFilename = "";

                    // SMART-FIX: File detection criteria enhanced to support message history mapping
                    boolean isFile = item.getFileBase64() != null || (displayText != null && displayText.startsWith("[File]: "));

                    if (displayText != null && displayText.startsWith("[File]: ")) {
                        cleanFilename = displayText.replace("[File]: ", "");
                        if (cleanFilename.matches("^\\d{13}_.*")) {
                            cleanFilename = cleanFilename.substring(14);
                        }
                        displayText = "📎 " + cleanFilename;
                    }

                    String timeStr = TimeUtil.formatMessageTime(item.getTimestamp());
                    if (timeStr.isEmpty()) {
                        timeStr = TimeUtil.formatMessageTime(java.time.LocalDateTime.now().toString());
                    }

                    javafx.scene.Node finalRowNode;

                    // --- 1. SYSTEM LOG MESSAGES ---
                    if ("System".equals(item.getSender())) {
                        Label sysLabel = new Label(displayText);
                        sysLabel.getStyleClass().add("system-message");
                        sysLabel.setWrapText(true);
                        sysLabel.maxWidthProperty().bind(lv.widthProperty().multiply(0.8));

                        HBox sysRow = new HBox(sysLabel);
                        sysRow.setAlignment(Pos.CENTER);
                        sysRow.setMaxWidth(Double.MAX_VALUE);

                        finalRowNode = sysRow;
                    }
                    // --- 2. DIALOG BUBBLE MESSAGES ---
                    else {
                        HBox cellRow = new HBox();
                        cellRow.setMaxWidth(Double.MAX_VALUE);

                        VBox bubbleWrapper = new VBox(2);

                        Label messageTextLabel = new Label(displayText);
                        messageTextLabel.setWrapText(true);
                        messageTextLabel.maxWidthProperty().bind(lv.widthProperty().multiply(0.7));

                        messageTextLabel.setOnContextMenuRequested(event -> {
                            event.consume();
                            showCustomBlurMenu(item, messageTextLabel, bubbleWrapper);
                        });

                        // OUTBOUND SELF MESSAGES (Right side alignment)
                        if (item.getSender().equals(myLogin)) {
                            messageTextLabel.getStyleClass().add("chat-bubble-mine");

                            HBox metaRow = new HBox(4);
                            metaRow.setAlignment(Pos.CENTER_RIGHT);

                            if (item.isEdited()) {
                                Label editedLabel = new Label("edited");
                                editedLabel.getStyleClass().add("chat-message-edited-right");
                                metaRow.getChildren().add(editedLabel);
                            }

                            Label timeLabel = new Label(timeStr);
                            timeLabel.getStyleClass().add("chat-message-time");
                            Label checksLabel = new Label(item.isRead() ? "✓✓" : "✓");
                            checksLabel.getStyleClass().add("chat-message-checks");
                            metaRow.getChildren().addAll(timeLabel, checksLabel);

                            bubbleWrapper.getChildren().addAll(messageTextLabel, metaRow);
                            bubbleWrapper.setAlignment(Pos.TOP_RIGHT);

                            cellRow.getChildren().add(bubbleWrapper);
                            cellRow.setAlignment(Pos.CENTER_RIGHT);
                            getStyleClass().add("my-message");
                        }
                        // INBOUND RECIPIENT MESSAGES (Left side alignment)
                        else {
                            String realSenderName = userNames.getOrDefault(item.getSender(), item.getSender());
                            if (realSenderName == null || realSenderName.trim().isEmpty()) {
                                realSenderName = item.getSender();
                            }

                            Label senderLabel = new Label(realSenderName);
                            senderLabel.getStyleClass().add("chat-message-sender");

                            messageTextLabel.getStyleClass().add("chat-bubble-other");

                            HBox metaRow = new HBox(4);
                            metaRow.setAlignment(Pos.CENTER_LEFT);

                            Label timeLabel = new Label(timeStr);
                            timeLabel.getStyleClass().add("chat-message-time");
                            metaRow.getChildren().addAll(timeLabel);

                            if (item.isEdited()) {
                                Label editedLabel = new Label("edited");
                                editedLabel.getStyleClass().add("chat-message-edited-left");
                                metaRow.getChildren().add(editedLabel);
                            }

                            bubbleWrapper.getChildren().addAll(senderLabel, messageTextLabel, metaRow);
                            bubbleWrapper.setAlignment(Pos.TOP_LEFT);

                            cellRow.getChildren().add(bubbleWrapper);
                            cellRow.setAlignment(Pos.CENTER_LEFT);
                            getStyleClass().add("other-message");
                        }

                        if (isFile) {
                            getStyleClass().add("file-message");
                            messageTextLabel.getStyleClass().add("file-message");
                        }

                        // Double-click listener for live memory streams and on-demand history network requests
                        if (isFile && !item.getSender().equals(myLogin)) {
                            final String finalCleanName = cleanFilename;
                            messageTextLabel.setOnMouseClicked((MouseEvent event) -> {
                                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                                    if (item.getFileBase64() != null) {
                                        // Live session instant memory stream download logic
                                        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
                                        fileChooser.setTitle("Save Received File");
                                        fileChooser.setInitialFileName(finalCleanName);
                                        java.io.File saveFile = fileChooser.showSaveDialog(chatListView.getScene().getWindow());
                                        if (saveFile != null) {
                                            try {
                                                byte[] decodedBytes = Base64.getDecoder().decode(item.getFileBase64());
                                                java.nio.file.Files.write(saveFile.toPath(), decodedBytes);
                                            } catch (Exception e) {
                                                logger.log(Level.SEVERE, "Failed to save file binary downstream", e);
                                            }
                                        }
                                    } else {
                                        // History mode: dispatch network request packet to server on demand via sockets
                                        if (serverConnection != null && item.getId() != null && !item.getId().isEmpty()) {
                                            serverConnection.sendMessage(XMLProtocol.buildRequestFilePacket(item.getId()));
                                        }
                                    }
                                }
                            });
                        } else if (!isFile) {
                            messageTextLabel.setOnMouseClicked(null);
                        }

                        finalRowNode = cellRow;
                    }

                    // --- 3. DYNAMIC INTER-DAY ENVELOPE (Telegram Date Capsule) ---
                    boolean showDateDivider = false;

                    if (getIndex() == 0) {
                        showDateDivider = true;
                    } else {
                        ChatMessage previousItem = getListView().getItems().get(getIndex() - 1);
                        if (previousItem != null && TimeUtil.isDifferentDay(previousItem.getTimestamp(), item.getTimestamp())) {
                            showDateDivider = true;
                        }
                    }

                    if (showDateDivider) {
                        VBox wrapperWithDate = new VBox(12);
                        wrapperWithDate.setAlignment(Pos.CENTER);
                        wrapperWithDate.setPadding(new javafx.geometry.Insets(10, 0, 0, 0));

                        Label dateLabel = new Label(TimeUtil.formatDateDivider(item.getTimestamp()));
                        dateLabel.getStyleClass().add("date-divider-capsule");

                        wrapperWithDate.getChildren().addAll(dateLabel, finalRowNode);
                        setGraphic(wrapperWithDate);
                    } else {
                        setGraphic(finalRowNode);
                    }
                }
            }
        });
    }
}