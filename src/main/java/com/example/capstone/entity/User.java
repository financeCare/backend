package com.example.capstone.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.awt.print.Book;
import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
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
    @Column(name = "email_confirm")
    private Boolean emailConfirm;
    @Column(name = "refresh_token")
    private String refreshToken;

    public User(String username, String passwordHash, Date dob, String email, Boolean emailConfirm, String refreshToken) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.dob = dob;
        this.email = email;
        this.emailConfirm = emailConfirm;
        this.refreshToken = refreshToken;
    }

}
