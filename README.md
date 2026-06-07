# ICQ Cyberpunk Java Messenger (Monorepo)

[![Java Version](https://img.shields.io/badge/Java-21-orange.svg?style=flat-square&logo=openjdk)](https://openjdk.org/)
[![JavaFX](https://img.shields.io/badge/JavaFX-21.0.6-blue.svg?style=flat-square&logo=java)](https://openjfx.io/)
[![Hibernate](https://img.shields.io/badge/Hibernate-6.x-green.svg?style=flat-square&logo=hibernate)](https://hibernate.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg?style=flat-square&logo=postgresql)](https://www.postgresql.org/)
[![Maven](https://img.shields.io/badge/Maven-3.x-red.svg?style=flat-square&logo=apachemaven)](https://maven.apache.org/)

A cross-platform, multithreaded client-server instant messaging application styled with a futuristic cyberpunk interface. The system relies on low-level network interactions via raw TCP sockets, uses a custom application layer protocol based on XML serialization, and manages data persistence and message history using Hibernate ORM.

---

## 🏗️ Architecture & Core Features

* **Multithreaded TCP Server (`java.net.ServerSocket`):** Each active client connection is isolated inside its own dedicated execution thread (`ClientHandler`). The server strictly manages session uniqueness, preventing duplicate concurrent logins from the same user profile.
* **Custom XML Protocol:** Data exchange between client and server is completely unified through strictly typed XML packets mapping exactly to internal system parsing routines.
* **On-Demand File Transfer:** To optimize runtime memory footprints and network bandwidth, binary file assets are not preloaded into the client's chat history. When a user requests a file, the client dispatches an asynchronous network request, the server reads the file from disk, encodes it to Base64, and streams it back exclusively to the requester.
* **Object-Relational Mapping (Hibernate ORM):** The persistent lifecycle of users (`User`) and messages (`Message`) is mapped directly onto a PostgreSQL database. All database operations are securely monitored via a single thread-safe `SessionFactory`.
* **Asynchronous GUI (JavaFX + MVC):** The socket listening loop runs continuously on a background worker thread, while interface updates are safely delegated to the main UI thread via `Platform.runLater` to avoid UI locking.

---

## 📂 Repository Layout

The project is organized using a monorepo structure:

```text
ICQ-Messenger/
│
├── MessengerServer/
│   ├── server_files/      # Local server storage for file transmissions
│   ├── src/               # Server source code & hibernate configurations
│   └── pom.xml
│
├── MessengerClient/
│   ├── src/               # JavaFX view templates (FXML), styles, and controllers
│   └── pom.xml
│
└── README.md

```

---

## 💻 Tech Stack

| Component | Technology | Version / Type |
| --- | --- | --- |
| **Language** | Java | JDK 21 |
| **GUI Framework** | JavaFX | 21.0.6 |
| **Database** | PostgreSQL | 16 |
| **ORM Framework** | Hibernate | 6.x |
| **Build Tool** | Maven | 3.x |
| **Network Layer** | TCP Sockets | `java.net.Socket` |
| **Data Protocol** | XML | Custom Schema |
| **Encoding** | Base64 | Binary Stream Transfer |

---

## ⚙️ Setup & Deployment Guide

### 1. Database Configuration

Ensure your PostgreSQL server is active, then create the target database schema:

```sql
CREATE DATABASE messenger_db;

```

Configure database connection credentials inside the central Hibernate descriptor:
`MessengerServer/src/main/resources/hibernate.cfg.xml`

```xml
<property name="hibernate.connection.url">jdbc:postgresql://localhost:5432/messenger_db</property>
<property name="hibernate.connection.username">postgres</property>
<property name="hibernate.connection.password">your_password</property>

```

### 2. Compile & Launch the Server

Navigate into the server core directory, build the executable module via Maven, and run it:

```bash
cd MessengerServer
mvn clean package
java -jar target/MessengerServer-1.0-SNAPSHOT.jar

```

### 3. Launch the Client Applications

Open a separate terminal window for every client instance, navigate to the client engine, and boot the application interface:

```bash
cd MessengerClient
mvn clean javafx:run

```

---

## 📜 Network Protocol Specifications (XML Samples)

### User Authentication & Updates

**Login Request:**

```xml
<packet type="login">
    <username>john_doe</username>
    <password>secure123</password>
</packet>

```

**Profile Details Update:**

```xml
<packet type="profile_updated">
    <name>John Doe</name>
    <phone>+123456789</phone>
</packet>

```

### Live Messaging Loop

**Text Message Transmission:**

```xml
<packet type="message">
    <id>msg_94827</id>
    <sender>john_doe</sender>
    <receiver>alice_smith</receiver>
    <text>Hello from the client!</text>
    <timestamp>2026-06-07T18:15:00</timestamp>
</packet>

```

**Read Receipt Notification:**

```xml
<packet type="read_receipt">
    <id>msg_94827</id>
</packet>

```

**Live Typing Indicator:**

```xml
<packet type="typing">
    <sender>john_doe</sender>
    <receiver>alice_smith</receiver>
</packet>

```

**Message State Modification (Edit / Delete):**

```xml
<packet type="edit">
    <id>msg_94827</id>
    <text>Updated text message content</text>
</packet>

```

```xml
<packet type="delete">
    <id>msg_94827</id>
</packet>

```

### On-Demand File Operations

**Binary History Fetching Request:**

```xml
<packet type="request_file" id="msg_94830"></packet>

```

**Streaming File Asset Delivery Response:**

```xml
<packet type="file_response" filename="report.pdf">
    <fileContent>JVBERi0xLjQKJcOkw7zDp3d...[BASE64_STREAM_CONTENT]...</fileContent>
</packet>

```

---

## 🔒 Concurrency Model

The server engine utilizes an isolated thread-per-client runtime topology:

```text
ServerSocket (Main Thread)
      │
      ├── ClientHandler #1 (Thread-1) ──► Parsing XML / DB Operations
      ├── ClientHandler #2 (Thread-2) ──► Parsing XML / DB Operations
      └── ClientHandler #3 (Thread-3) ──► Parsing XML / DB Operations

```

Active system sessions are synchronized globally across handlers using thread-safe collections to prevent race conditions during broad multi-user state updates.

---

## 📁 File Transfer Workflow

```text
User Actions              Network Layer              Server Storage
═══════════════════════════════════════════════════════════════════
Double-Click File  ──►  Sends 'request_file'  ──►  Intercepts Packet
                                                           │
                                                           ▼
Saves to Local Disk ◄──  Decodes Base64 Stream ◄──  Reads File Assets

```

---

## 📄 Academic License

This software system was developed exclusively for academic research and evaluation within the cross-platform application design curriculum covering:

* Low-level TCP/IP stream sockets programming.
* Concurrent thread-safe server execution models.
* Responsive desktop application structures utilizing JavaFX and MVC patterns.
* Object-Relational Database mappings managed via Hibernate ORM engines.
