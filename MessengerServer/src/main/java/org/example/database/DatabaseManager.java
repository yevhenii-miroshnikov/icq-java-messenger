package org.example.database;

import org.example.models.Message;
import org.example.models.User;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.query.Query;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseManager {
    private static final Logger logger = Logger.getLogger(DatabaseManager.class.getName());
    private static SessionFactory sessionFactory;

    static {
        try {
            sessionFactory = new Configuration().configure().buildSessionFactory();
            logger.info("Database connection successfully established!");
        } catch (Throwable ex) {
            logger.log(Level.SEVERE, "Failed to initialize SessionFactory", ex);
            throw new ExceptionInInitializerError(ex);
        }
    }

    // User authentication against stored password hash
    public static boolean authenticateUser(String username, String passwordHash) {
        try (Session session = sessionFactory.openSession()) {
            Query<User> query = session.createQuery("FROM User WHERE username = :username", User.class);
            query.setParameter("username", username);
            User user = query.uniqueResult();

            if (user != null) {
                return user.getPassword().equals(passwordHash);
            }
            return false;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during user authentication", e);
            return false;
        }
    }

    // Registers a new user if the username is unique
    public static boolean registerUser(String name, String username, String phone, String passwordHash) {
        try (Session session = sessionFactory.openSession()) {
            Transaction transaction = session.beginTransaction();

            Query<User> query = session.createQuery("FROM User WHERE username = :username", User.class);
            query.setParameter("username", username);

            if (query.uniqueResult() != null) {
                transaction.rollback();
                logger.warning("Registration attempt for an already taken username: " + username);
                return false;
            }

            User newUser = new User(name, username, phone, passwordHash);
            session.persist(newUser);
            transaction.commit();
            logger.info("Successfully registered new user: @" + username);
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during user registration", e);
            return false;
        }
    }

    // Finds user entity by strict username matching
    public static User findUserByUsername(String username) {
        try (Session session = sessionFactory.openSession()) {
            Query<User> query = session.createQuery("FROM User WHERE username = :username", User.class);
            query.setParameter("username", username);
            return query.uniqueResult();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error finding user by username", e);
            return null;
        }
    }

    // Persists a new message instance to the database
    public static Message saveMessage(String senderUsername, String receiverUsername, String text) {
        try (Session session = sessionFactory.openSession()) {
            Transaction transaction = session.beginTransaction();
            Message msg = new Message(senderUsername, receiverUsername, text);
            session.persist(msg);
            transaction.commit();
            return msg;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error saving message", e);
            return null;
        }
    }

    // Fetches limited private message history for a specific user
    public static List<Message> getUserMessagesHistory(String username, int limit) {
        try (Session session = sessionFactory.openSession()) {
            Query<Message> query = session.createQuery(
                    "FROM Message WHERE sender = :username OR receiver = :username ORDER BY timestamp DESC",
                    Message.class
            );
            query.setParameter("username", username);
            query.setMaxResults(limit);

            List<Message> messages = query.list();
            Collections.reverse(messages);
            return messages;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error retrieving private message history", e);
            return Collections.emptyList();
        }
    }

    // Fetches explicit text property of a message by its unique database identifier
    public static String getMessageTextById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            Message msg = session.get(Message.class, id);
            return (msg != null) ? msg.getText() : null;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error fetching message text by id from database", e);
            return null;
        }
    }

    // Deletes message entity and handles corresponding physical file removal if present
    public static void deleteMessage(Long id) {
        try (Session session = sessionFactory.openSession()) {
            Transaction transaction = session.beginTransaction();
            Message msg = session.get(Message.class, id);

            if (msg != null) {
                if (msg.getText().startsWith("[File]: ")) {
                    String filename = msg.getText().replaceAll("^\\[File\\]: ", "");
                    java.io.File fileToDelete = new java.io.File("server_files", filename);
                    if (fileToDelete.exists()) {
                        fileToDelete.delete();
                        logger.info("Physical file deleted from server: " + filename);
                    }
                }
                session.remove(msg);
            }
            transaction.commit();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error deleting message lifecycle entity", e);
        }
    }

    // Updates user's last activity timestamp
    public static void updateLastSeen(String username) {
        try (Session session = sessionFactory.openSession()) {
            Transaction transaction = session.beginTransaction();
            Query<User> query = session.createQuery("FROM User WHERE username = :username", User.class);
            query.setParameter("username", username);
            User user = query.uniqueResult();

            if (user != null) {
                user.setLastSeen(LocalDateTime.now());
                session.merge(user);
            }
            transaction.commit();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error updating lastSeen status for @" + username, e);
        }
    }

    // Marks a message as read and records the current timestamp
    public static Message markMessageAsRead(Long messageId) {
        try (Session session = sessionFactory.openSession()) {
            Transaction transaction = session.beginTransaction();
            Message msg = session.get(Message.class, messageId);

            if (msg != null && !msg.isRead()) {
                msg.setRead(true);
                msg.setReadTime(LocalDateTime.now());
                session.merge(msg);
            }
            transaction.commit();
            return msg;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error updating read status for message id: " + messageId, e);
            return null;
        }
    }

    // Edits stored message text content
    public static Message editMessage(Long messageId, String newText) {
        try (Session session = sessionFactory.openSession()) {
            Transaction transaction = session.beginTransaction();
            Message msg = session.get(Message.class, messageId);

            if (msg != null) {
                msg.setText(newText);
                msg.setEdited(true);
                session.merge(msg);
            }
            transaction.commit();
            return msg;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error editing message for id: " + messageId, e);
            return null;
        }
    }

    // Performs case-insensitive live prefix matching for user lookup
    public static List<User> searchUsersByPrefix(String prefix, int limit) {
        try (Session session = sessionFactory.openSession()) {
            Query<User> query = session.createQuery(
                    "FROM User WHERE lower(username) LIKE lower(:prefix) OR lower(name) LIKE lower(:prefix)",
                    User.class
            );
            query.setParameter("prefix", prefix.toLowerCase() + "%");
            query.setMaxResults(limit);
            return query.list();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during live prefix user search", e);
            return Collections.emptyList();
        }
    }

    // Updates editable profile attributes for a specific user
    public static boolean updateUserProfile(String username, String newName, String newPhone) {
        try (Session session = sessionFactory.openSession()) {
            Transaction transaction = session.beginTransaction();
            Query<User> query = session.createQuery("FROM User WHERE username = :username", User.class);
            query.setParameter("username", username);
            User user = query.uniqueResult();

            if (user != null) {
                user.setName(newName);
                user.setPhone(newPhone);
                session.merge(user);
                transaction.commit();
                logger.info("Profile successfully updated for @" + username);
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error updating user profile for @" + username, e);
            return false;
        }
    }

    // Updates account security credentials after strict validation of current hash
    public static boolean updateUserPassword(String username, String oldPasswordHash, String newPasswordHash) {
        try (Session session = sessionFactory.openSession()) {
            Transaction transaction = session.beginTransaction();
            Query<User> query = session.createQuery("FROM User WHERE username = :username", User.class);
            query.setParameter("username", username);
            User user = query.uniqueResult();

            if (user != null && user.getPassword().equals(oldPasswordHash)) {
                user.setPassword(newPasswordHash);
                session.merge(user);
                transaction.commit();
                logger.info("Password successfully changed in database for @" + username);
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error changing password for user @" + username, e);
            return false;
        }
    }
}