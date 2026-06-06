package org.example.messengerclient.models;

public class ChatMessage {
    private String id;
    private String sender;
    private String receiver;
    private String text;
    private String fileBase64;
    private boolean isEdited = false;

    // Temporal timestamps and read confirmation metadata for UI state tracking
    private String timestamp;
    private boolean isRead;
    private String readTime;

    // Overloaded constructor for basic system messages
    public ChatMessage(String id, String sender, String receiver, String text) {
        this(id, sender, receiver, text, null, java.time.LocalDateTime.now().toString(), false, "");
    }

    // Overloaded constructor for standalone binary file transfers
    public ChatMessage(String id, String sender, String receiver, String text, String fileBase64) {
        this(id, sender, receiver, text, fileBase64, java.time.LocalDateTime.now().toString(), false, "");
    }

    // Primary constructor handling complete message entity mapping and network telemetry parameters
    public ChatMessage(String id, String sender, String receiver, String text, String fileBase64, String timestamp, boolean isRead, String readTime) {
        this.id = id;
        this.sender = sender;
        this.receiver = receiver;
        this.text = text;
        this.fileBase64 = fileBase64;
        this.timestamp = (timestamp == null || timestamp.isEmpty()) ? java.time.LocalDateTime.now().toString() : timestamp;
        this.isRead = isRead;
        this.readTime = readTime;
        this.isEdited = false;
    }

    public String getId() { return id; }
    public String getSender() { return sender; }
    public String getReceiver() { return receiver; }
    public String getText() { return text; }

    public boolean isEdited() { return isEdited; }
    public void setEdited(boolean edited) { this.isEdited = edited; }

    public void setText(String text) {
        this.text = text;
    }

    public String getFileBase64() { return fileBase64; }

    public String getTimestamp() { return timestamp; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { this.isRead = read; }

    public String getReadTime() { return readTime; }
    public void setReadTime(String readTime) { this.readTime = readTime; }
}