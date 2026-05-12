package com.finalProject.BookingMeetingRoom.mapper;

import com.finalProject.BookingMeetingRoom.model.dto.NotificationDTO;
import com.finalProject.BookingMeetingRoom.model.entity.Notification;
import com.finalProject.BookingMeetingRoom.model.request.NotificationRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "read", target = "read")
    NotificationDTO toDto(Notification notification);

    Notification toEntity(NotificationRequest dto);

}
