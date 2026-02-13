package com.example.capstone.controller;

import com.example.capstone.dto.TransactionRequest;
import com.example.capstone.dto.TransactionResponse;
import com.example.capstone.dto.UserSettingDto;
import com.example.capstone.dto.UserSettingResponseDto;
import com.example.capstone.entity.Transaction;
import com.example.capstone.entity.User;
import com.example.capstone.entity.UserSetting;
import com.example.capstone.service.TransactionService;
import com.example.capstone.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/user-setting")
@RequiredArgsConstructor
public class UserSettingController {

    private final UserService userService;

    @GetMapping
    public UserSettingResponseDto getUserSetting(@RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.replace("Bearer ", "");
        return userService.getUserSetting(token);
    }

    @PutMapping
    public UserSetting setUserUserSetting(@RequestHeader("Authorization") String authorizationHeader, @RequestBody UserSettingDto userSettingDto) {
        String token = authorizationHeader.replace("Bearer ", "");
        return userService.setUserSetting(token,userSettingDto);
    }
}
