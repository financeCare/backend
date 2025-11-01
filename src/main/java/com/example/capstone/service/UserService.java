package com.example.capstone.service;

import com.example.capstone.dto.RegisterRequest;
import com.example.capstone.entity.User;
import com.example.capstone.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public void registerUser(RegisterRequest request){
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        userRepository.save(new User(request.getUsername(),encodedPassword,request.getDob(),request.getEmail()));
    }

}
