package com.finalProject.BookingMeetingRoom.mapper;

import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.projection.MyReservationProjection;
import com.finalProject.BookingMeetingRoom.model.response.MyReservationResponse;
import com.finalProject.BookingMeetingRoom.model.response.ReservationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationMapperFacade {

    private final RoomMapper seatMapper;
    private final FloorMapper floorMapper;
    private final BuildingMapper buildingMapper;

    public ReservationResponse toResponse(Reservation reservation) {
        if (reservation == null) return null;

        var room = reservation.getRoom();
        var roomDto = room != null ? seatMapper.toDto(room) : null;
        var floorDto = (room != null && room.getFloor() != null)
                ? floorMapper.toDto(room.getFloor()) : null;
        var buildingDto = (room != null && room.getFloor() != null && room.getFloor().getBuilding() != null)
                ? buildingMapper.toDto(room.getFloor().getBuilding()) : null;

        var response = new ReservationResponse();
        response.setId(reservation.getId());
        response.setStartTime(reservation.getStartTime());
        response.setEndTime(reservation.getEndTime());
        response.setStatus(reservation.getStatus());
        response.setPurpose(reservation.getPurpose());
        response.setNote(reservation.getNote());
        response.setCheckinTime(reservation.getCheckinTime());
        response.setReturnTime(reservation.getReturnTime());
        response.setCreateAt(reservation.getCreateAt());
        response.setUpdatedAt(reservation.getUpdatedAt());

        response.setRoom(roomDto);
        response.setFloor(floorDto);
        response.setBuilding(buildingDto);

        response.setFeedbacked(reservation.getFeedback() == null && reservation.getStatus() == ReservationStatus.COMPLETED);

        return response;
    }

    public MyReservationResponse toMyResponse(MyReservationProjection myReservationProjection) {
        if (myReservationProjection == null) return null;
        var response = new MyReservationResponse();
        response.setReservationId(myReservationProjection.getReservationId());
        response.setLocationCode(myReservationProjection.getLocationCode());
        response.setFloorName(myReservationProjection.getFloorName());
        response.setBuildingName(myReservationProjection.getBuildingName());
        response.setAddress(myReservationProjection.getAddress());
        response.setReservationStatus(myReservationProjection.getReservationStatus());
        response.setSelectedDate(myReservationProjection.getSelectedDate());
        response.setStartTime(myReservationProjection.getStartTime());
        response.setEndTime(myReservationProjection.getEndTime());
        response.setDuration(myReservationProjection.getDuration());
        var isFeedback = myReservationProjection.getIsFeedback();
        response.setIsFeedback(isFeedback != null && isFeedback == 1L);
        return response;
    }

}
