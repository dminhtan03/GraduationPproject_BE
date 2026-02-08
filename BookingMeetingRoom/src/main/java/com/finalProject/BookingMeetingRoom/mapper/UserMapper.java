package com.finalProject.BookingMeetingRoom.mapper;

import com.finalProject.BookingMeetingRoom.model.request.RegistrationRequest;
import com.finalProject.BookingMeetingRoom.model.response.RegistrationResponse;
import com.finalProject.BookingMeetingRoom.model.response.UserResponse;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.model.entity.UserInfo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {
    @Mapping(target = "id", ignore = true)
    UserInfo toUserInfo(RegistrationRequest registrationRequest);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "enabled", constant = "false")
    @Mapping(target = "loginCount", constant = "0")
    @Mapping(target = "locked", constant = "false")
    @Mapping(target = "userInfo", source = "registrationRequest")
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "refreshTokens", ignore = true)
    User toUser(RegistrationRequest registrationRequest);

    RegistrationResponse toRegistrationResponse(UserInfo userInfo);

    UserResponse toUserResponse(UserInfo userInfo);
}
