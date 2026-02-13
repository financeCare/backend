package com.example.capstone.dto;

import com.example.capstone.entity.UserSetting;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class UserSettingResponseDto {
    private UserSetting userSettingList;
    private List<DeviceListDto> userDeviceList;
}
