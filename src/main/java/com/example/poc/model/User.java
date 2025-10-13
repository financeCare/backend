package com.example.poc.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.sql.Timestamp;

@Entity
@Table(name = "users")
public class User {
    @Id
    @Column(name = "id")
    private Long userId;
    private String email;
    @Column(name = "password_hash")
    private String passwordHash;
    @Column(name = "create_at")
    private Timestamp createAt;
    @Column(name = "email_verified")
    private boolean emailVerified;
}
