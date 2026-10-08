# ICQ Java Messenger

An academic client-server messaging project built with Java. It is split into a JavaFX desktop client and a TCP server that persists account and message data in PostgreSQL.

## Project Context

This project was developed as university coursework to practice client-server development, socket communication, multithreading, persistence, and JavaFX UI integration.

## 📖 Table of Contents

- [Implemented Features](#implemented-features)
- [Project Structure](#project-structure)
- [Technology Stack](#technology-stack)
- [Architecture and Design Notes](#architecture-and-design-notes)
- [Run Locally](#run-locally)
- [Verification](#verification)
- [Scope and Limitations](#scope-and-limitations)
- [Kurzbeschreibung (DE)](#kurzbeschreibung-de)
- [Contact](#contact)

## Implemented Features

- Account registration and login, profile updates, and password changes.
- User search by username or display-name prefix, plus online/offline status and last-seen time.
- Direct messages, a recent history of up to 50 messages per account, read receipts, message edits and deletion, and typing indicators.
- File transfer over the socket connection. File contents are stored under `MessengerServer/server_files`; message and account records are stored in PostgreSQL.

The server accepts connections on TCP port `8080`. The client login screen defaults to `localhost` and allows another server host to be entered.

## Project Structure

- `MessengerClient` — JavaFX views and controllers, client socket connection, and XML packet serialization/parsing.
- `MessengerServer` — socket server, one handler thread per connected client, XML packet handling, and persistence through Hibernate.
- `MessengerServer/src/main/java/org/example/models` — Hibernate entities for users and messages.

The client reads incoming socket data on a daemon thread. Its login and chat controllers use `Platform.runLater` to apply received updates on the JavaFX application thread.

## Technology Stack

- Java 21; JavaFX 21.0.6 for the desktop UI.
- TCP sockets on port 8080, with a project-specific XML packet format.
- PostgreSQL through Hibernate ORM 6.4.4.Final.
- Maven builds both modules. The client includes the Maven wrapper; the server run goal is configured in its `pom.xml`.

## Architecture and Design Notes

- The server starts one handler thread for each client connection.
- Client and server exchange project-specific XML packets over TCP.
- Hibernate maps user and message entities to PostgreSQL; the local configuration uses `hbm2ddl.auto=update`.
- The client receives socket data on a background thread and uses `Platform.runLater` to apply UI updates on the JavaFX application thread.

## Run Locally

### Requirements

- JDK 21.
- PostgreSQL running locally.
- Maven installed for the server module. The client can use its included wrapper.

The client and server are separate Maven projects; build them from their own directories.

```powershell
Set-Location MessengerServer
mvn package
```

```powershell
Set-Location MessengerClient
.\mvnw.cmd package
```

### Prepare the Database

Create a local PostgreSQL role and database. In `psql`, for example:

```sql
CREATE ROLE messenger_app LOGIN;
\password messenger_app
CREATE DATABASE messenger_db OWNER messenger_app;
```

Set the database connection variables in the same PowerShell window that will run the server. Replace the password placeholder with the local password set above. `MESSENGER_DB_URL` is optional and defaults to `jdbc:postgresql://localhost:5432/messenger_db`.

```powershell
$env:MESSENGER_DB_URL = "jdbc:postgresql://localhost:5432/messenger_db"
$env:MESSENGER_DB_USERNAME = "messenger_app"
$env:MESSENGER_DB_PASSWORD = "<your-local-password>"
```

### Start the Server

From the repository root, in that same PowerShell window:

```powershell
Set-Location MessengerServer
mvn exec:java
```

The Hibernate configuration uses `hbm2ddl.auto=update`, so Hibernate may create or update the mapped tables when it initializes. This is a local development setup, not a database migration strategy.

### Start the Client

Open another PowerShell window at the repository root:

```powershell
Set-Location MessengerClient
.\mvnw.cmd javafx:run
```

Register a user, then sign in. For a server on another machine, enter its host address on the login screen and allow TCP port 8080 through the local network firewall.

## Verification

- Both Maven modules were packaged with Eclipse Temurin JDK 21.0.8 and Maven 3.8.5.
- A local protocol smoke run with two TCP clients and PostgreSQL 16 covered registration, login, search, message delivery, read receipts, persisted history after reconnect, typing, editing/deletion, and file upload/retrieval.
- The JavaFX client was launched separately and its login window opened.

## Scope and Limitations

This is an academic prototype for local use, not a production-ready or security-hardened messenger. The socket connection is plain TCP without TLS. Passwords are represented by client-generated, unsalted SHA-256 digests; this is not a password-hashing scheme suitable for real accounts. XML values are assembled by string concatenation, and the DOM parsers are not explicitly hardened for untrusted input. Use a disposable local database and do not expose the server to the public internet.

<a id="kurzbeschreibung-de"></a>
## 🇩🇪 Kurzbeschreibung

Dieses akademische Java-Projekt entstand im Rahmen des Hochschulkurses Plattformübergreifende Programmierung. Es besteht aus einem JavaFX-Desktop-Client und einem mehrthreadigen TCP-Server mit XML-basiertem Nachrichtenprotokoll sowie PostgreSQL-Persistenz über Hibernate ORM. Das Repository wurde durch reproduzierbares Setup, technische Dokumentation und einen lokalen Smoke-Test portfoliofähig aufbereitet.

<a id="contact"></a>
## 📬 Contact

- **GitHub:** [@yevhenii-miroshnikov](https://github.com/yevhenii-miroshnikov)
- **LinkedIn:** [yevhenii-miroshnikov](https://www.linkedin.com/in/yevhenii-miroshnikov)
