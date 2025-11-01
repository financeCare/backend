package com.example.capstone.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue
    @Column(name = "user_id")
    private UUID userId;
    private String username;
    @Column(name = "password_hash")
    private String passwordHash;
    @Column(name = "dob")
    private Date dob;
    private String email;

    public User(String username, String passwordHash, Date dob, String email) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.dob = dob;
        this.email = email;
    }

    public User() {}
}
