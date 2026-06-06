package org.example.models;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String username;

    @Column
    private String phone;

    @Column(nullable = false)
    private String password;

    // Field to track user presence status and last activity timestamp
    @Column
    private LocalDateTime lastSeen;

    public User() {}

    public User(String name, String username, String phone, String password) {
        this.name = name;
        this.username = username;
        this.phone = phone;
        this.password = password;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getUsername() { return username; }
    public String getPhone() { return phone; }
    public String getPassword() { return password; }

    public LocalDateTime getLastSeen() { return lastSeen; }
    public void setLastSeen(LocalDateTime lastSeen) { this.lastSeen = lastSeen; }

    public void setName(String name) {
        this.name = name;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}