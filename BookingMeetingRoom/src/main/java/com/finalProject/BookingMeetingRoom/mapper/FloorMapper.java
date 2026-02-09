package com.finalProject.BookingMeetingRoom.mapper;

import com.finalProject.BookingMeetingRoom.model.dto.FloorDto;
import com.finalProject.BookingMeetingRoom.model.entity.Floor;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface FloorMapper {

    FloorDto toDto(Floor floor);

}