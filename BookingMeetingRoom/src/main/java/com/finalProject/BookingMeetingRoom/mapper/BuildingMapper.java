package com.finalProject.BookingMeetingRoom.mapper;

import com.finalProject.BookingMeetingRoom.model.dto.BuildingDto;
import com.finalProject.BookingMeetingRoom.model.entity.Building;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface BuildingMapper {
    BuildingDto toDto(Building building);
}