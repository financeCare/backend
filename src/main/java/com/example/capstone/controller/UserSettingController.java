package com.example.capstone.controller;

import com.example.capstone.dto.UserSettingDto;
import com.example.capstone.dto.UserSettingResponseDto;
import com.example.capstone.entity.UserSetting;
import com.example.capstone.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
    public UserSetting setUserUserSetting(@RequestHeader("Authorization") String authorizationHeader, @Valid @RequestBody UserSettingDto userSettingDto) {
        String token = authorizationHeader.replace("Bearer ", "");
        return userService.setUserSetting(token,userSettingDto);
    }
}
