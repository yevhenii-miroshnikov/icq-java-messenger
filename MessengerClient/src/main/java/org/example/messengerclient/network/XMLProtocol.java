package org.example.messengerclient.network;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.logging.Logger;

public class XMLProtocol {
    private static final Logger logger = Logger.getLogger(XMLProtocol.class.getName());

    public static String buildAuthPacket(String username, String password) {
        return "<packet type=\"auth\"><username>" + username + "</username><password>" + password + "</password></packet>";
    }

    public static String buildAuthSuccessPacket(String name, String phone) {
        return "<packet type=\"auth_success\"><name>" + name + "</name><phone>" + phone + "</phone></packet>";
    }

    public static String buildUpdateProfilePacket(String newName, String newPhone) {
        return "<packet type=\"update_profile\">" +
                "<name>" + newName + "</name>" +
                "<phone>" + newPhone + "</phone></packet>";
    }

    public static String buildProfileUpdatedPacket(String name, String phone) {
        return "<packet type=\"profile_updated\">" +
                "<name>" + name + "</name>" +
                "<phone>" + phone + "</phone></packet>";
    }

    public static String buildChangePasswordPacket(String oldPasswordHash, String newPasswordHash) {
        return "<packet type=\"change_password\">" +
                "<old_password>" + oldPasswordHash + "</old_password>" +
                "<new_password>" + newPasswordHash + "</new_password></packet>";
    }

    public static String buildPasswordChangedPacket() {
        return "<packet type=\"password_changed\"></packet>";
    }

    public static String buildRegisterPacket(String name, String username, String phone, String password) {
        return "<packet type=\"register\">" +
                "<name>" + name + "</name>" +
                "<username>" + username + "</username>" +
                "<phone>" + phone + "</phone>" +
                "<password>" + password + "</password></packet>";
    }

    public static String buildSearchPacket(String query) {
        return "<packet type=\"search\"><query>" + query + "</query></packet>";
    }

    public static String buildSearchResultPacket(String status, java.util.Map<String, String> users) {
        StringBuilder sb = new StringBuilder();
        sb.append("<packet type=\"search_result\"><status>").append(status).append("</status><users>");
        if (users != null) {
            for (java.util.Map.Entry<String, String> entry : users.entrySet()) {
                sb.append("<user><username>").append(entry.getKey()).append("</username>")
                        .append("<name>").append(entry.getValue()).append("</name></user>");
            }
        }
        sb.append("</users></packet>");
        return sb.toString();
    }

    public static String buildMessagePacket(String id, String sender, String receiver, String text, String timestamp, String isRead, String readTime, String isEdited) {
        String safeText = text.replace("\n", " ");
        return "<packet type=\"message\" id=\"" + id + "\">" +
                "<sender>" + sender + "</sender>" +
                "<receiver>" + receiver + "</receiver>" +
                "<timestamp>" + timestamp + "</timestamp>" +
                "<is_read>" + isRead + "</is_read>" +
                "<read_time>" + readTime + "</read_time>" +
                "<is_edited>" + isEdited + "</is_edited>" +
                "<text>" + safeText + "</text></packet>";
    }

    public static String buildSystemPacket(String text) {
        return "<packet type=\"system\"><text>" + text + "</text></packet>";
    }

    public static String buildErrorPacket(String text) {
        return "<packet type=\"error\"><text>" + text + "</text></packet>";
    }

    public static String buildDeletePacket(String messageId) {
        return "<packet type=\"delete\" id=\"" + messageId + "\"></packet>";
    }

    public static String buildEditPacket(String id, String text) {
        String safeText = text.replace("\n", " ");
        return "<packet type=\"edit\" id=\"" + id + "\"><text>" + safeText + "</text></packet>";
    }

    public static String buildTypingPacket(String sender, String receiver) {
        return "<packet type=\"typing\"><sender>" + sender + "</sender><receiver>" + receiver + "</receiver></packet>";
    }

    public static String buildFilePacket(String id, String sender, String receiver, String filename, String base64Content) {
        return "<packet type=\"file\" id=\"" + id + "\">" +
                "<sender>" + sender + "</sender>" +
                "<receiver>" + receiver + "</receiver>" +
                "<filename>" + filename + "</filename>" +
                "<content>" + base64Content + "</content></packet>";
    }

    public static String buildStatusPacket(String username, String name, String state, String lastSeen) {
        return "<packet type=\"status\">" +
                "<username>" + username + "</username>" +
                "<name>" + name + "</name>" +
                "<state>" + state + "</state>" +
                "<last_seen>" + lastSeen + "</last_seen></packet>";
    }

    public static String buildReadReceiptPacket(String messageId) {
        return "<packet type=\"read_receipt\" id=\"" + messageId + "\"></packet>";
    }

    // Dispatches an on-demand request transaction for a specific file entity from history
    public static String buildRequestFilePacket(String messageId) {
        return "<packet type=\"request_file\" id=\"" + messageId + "\"></packet>";
    }

    // Returns a standalone file transfer payload containment mapping base64 asset streams
    public static String buildFileResponsePacket(String id, String filename, String base64Content) {
        return "<packet type=\"file_response\" id=\"" + id + "\" filename=\"" + filename + "\"><content>" + base64Content + "</content></packet>";
    }

    public static class ParsedPacket {
        public String type = "";
        public String id = "";
        public String name = "";
        public String username = "";
        public String phone = "";
        public String password = "";
        public String newPassword = "";
        public String query = "";
        public String status = "";
        public String sender = "";
        public String receiver = "";
        public String text = "";
        public String filename = "";
        public String fileContent = "";
        public String state = "";
        public String timestamp = "";
        public boolean isRead = false;
        public String readTime = "";
        public String lastSeen = "";
        public boolean isEdited = false;
        public java.util.Map<String, String> foundUsersList = new java.util.HashMap<>();
    }

    public static ParsedPacket parse(String xml) {
        ParsedPacket packet = new ParsedPacket();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            Element root = doc.getDocumentElement();

            packet.type = root.getAttribute("type");
            packet.id = root.getAttribute("id");

            if ("auth".equals(packet.type)) {
                packet.username = root.getElementsByTagName("username").item(0).getTextContent();
                packet.password = root.getElementsByTagName("password").item(0).getTextContent();
            }
            else if ("auth_success".equals(packet.type) || "profile_updated".equals(packet.type) || "update_profile".equals(packet.type)) {
                if (root.getElementsByTagName("name").getLength() > 0) {
                    packet.name = root.getElementsByTagName("name").item(0).getTextContent();
                }
                if (root.getElementsByTagName("phone").getLength() > 0) {
                    packet.phone = root.getElementsByTagName("phone").item(0).getTextContent();
                }
            }
            else if ("change_password".equals(packet.type)) {
                if (root.getElementsByTagName("old_password").getLength() > 0) {
                    packet.password = root.getElementsByTagName("old_password").item(0).getTextContent();
                }
                if (root.getElementsByTagName("new_password").getLength() > 0) {
                    packet.newPassword = root.getElementsByTagName("new_password").item(0).getTextContent();
                }
            }
            else if ("register".equals(packet.type)) {
                packet.name = root.getElementsByTagName("name").item(0).getTextContent();
                packet.username = root.getElementsByTagName("username").item(0).getTextContent();
                if (root.getElementsByTagName("phone").getLength() > 0) {
                    packet.phone = root.getElementsByTagName("phone").item(0).getTextContent();
                }
                packet.password = root.getElementsByTagName("password").item(0).getTextContent();
            }
            else if ("search".equals(packet.type)) {
                packet.query = root.getElementsByTagName("query").item(0).getTextContent();
            }
            else if ("search_result".equals(packet.type)) {
                packet.status = root.getElementsByTagName("status").item(0).getTextContent();
                org.w3c.dom.NodeList userNodes = root.getElementsByTagName("user");
                for (int i = 0; i < userNodes.getLength(); i++) {
                    org.w3c.dom.Element userEl = (org.w3c.dom.Element) userNodes.item(i);
                    String uname = userEl.getElementsByTagName("username").item(0).getTextContent();
                    String name = userEl.getElementsByTagName("name").item(0).getTextContent();
                    packet.foundUsersList.put(uname, name);
                }
            }
            else if ("message".equals(packet.type)) {
                packet.sender = root.getElementsByTagName("sender").item(0).getTextContent();
                packet.receiver = root.getElementsByTagName("receiver").item(0).getTextContent();
                packet.text = root.getElementsByTagName("text").item(0).getTextContent();

                if (root.getElementsByTagName("timestamp").getLength() > 0) {
                    packet.timestamp = root.getElementsByTagName("timestamp").item(0).getTextContent();
                }
                if (root.getElementsByTagName("is_read").getLength() > 0) {
                    packet.isRead = Boolean.parseBoolean(root.getElementsByTagName("is_read").item(0).getTextContent());
                }
                if (root.getElementsByTagName("read_time").getLength() > 0) {
                    packet.readTime = root.getElementsByTagName("read_time").item(0).getTextContent();
                }
                if (root.getElementsByTagName("is_edited").getLength() > 0) {
                    packet.isEdited = Boolean.parseBoolean(root.getElementsByTagName("is_edited").item(0).getTextContent());
                }
            }
            else if ("system".equals(packet.type) || "error".equals(packet.type)) {
                packet.text = root.getElementsByTagName("text").item(0).getTextContent();
            }
            else if ("typing".equals(packet.type)) {
                packet.sender = root.getElementsByTagName("sender").item(0).getTextContent();
                packet.receiver = root.getElementsByTagName("receiver").item(0).getTextContent();
            }
            else if ("file".equals(packet.type)) {
                packet.sender = root.getElementsByTagName("sender").item(0).getTextContent();
                packet.receiver = root.getElementsByTagName("receiver").item(0).getTextContent();
                packet.filename = root.getElementsByTagName("filename").item(0).getTextContent();
                packet.fileContent = root.getElementsByTagName("content").item(0).getTextContent();
            }
            else if ("status".equals(packet.type)) {
                packet.username = root.getElementsByTagName("username").item(0).getTextContent();
                if (root.getElementsByTagName("name").getLength() > 0) {
                    packet.name = root.getElementsByTagName("name").item(0).getTextContent();
                }
                packet.state = root.getElementsByTagName("state").item(0).getTextContent();

                if (root.getElementsByTagName("last_seen").getLength() > 0) {
                    packet.lastSeen = root.getElementsByTagName("last_seen").item(0).getTextContent();
                }
            }
            else if ("read_receipt".equals(packet.type)) {
                // Packet ID is resolved during core attribute processing
            }
            else if ("edit".equals(packet.type)) {
                packet.text = root.getElementsByTagName("text").item(0).getTextContent();
            }
            else if ("file_response".equals(packet.type)) {
                packet.filename = root.getAttribute("filename");
                packet.fileContent = root.getElementsByTagName("content").item(0).getTextContent();
            }
            else if ("request_file".equals(packet.type)) {
                // Packet ID attribute is already resolved globally at the start of the method
            }
        } catch (Exception e) {
            logger.warning("XML parsing error: " + e.getMessage());
        }
        return packet;
    }
}