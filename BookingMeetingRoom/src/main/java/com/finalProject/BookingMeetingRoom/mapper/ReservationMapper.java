package com.finalProject.BookingMeetingRoom.mapper;

import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.request.ReservationRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReservationMapper {

    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "createAt", expression = "java(java.time.LocalDateTime.now())")
    Reservation toEntity(ReservationRequest request);

}