package com.finalProject.BookingMeetingRoom.mapper;

import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.model.projection.MyReservationProjection;
import com.finalProject.BookingMeetingRoom.model.response.AdminReservationResponse;
import com.finalProject.BookingMeetingRoom.model.response.MyReservationResponse;
import com.finalProject.BookingMeetingRoom.model.response.ReservationResponse;
import com.finalProject.BookingMeetingRoom.mapper.RoomMapper;
import com.finalProject.BookingMeetingRoom.mapper.FloorMapper;
import com.finalProject.BookingMeetingRoom.mapper.BuildingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationMapperFacade {

    private final ReservationMapper reservationMapper;
    private final RoomMapper roomMapper;
    private final FloorMapper floorMapper;
    private final BuildingMapper buildingMapper;

    // [ADDED] Mapper to convert Reservation to AdminReservationResponse
    public AdminReservationResponse toAdminResponse(Reservation reservation) {
        return AdminReservationResponse.builder()
                .reservationId(reservation.getId())
                .startTime(reservation.getStartTime())
                .endTime(reservation.getEndTime())
                .status(reservation.getStatus())
                .roomName(reservation.getRoom().getLocationCode())
                .floorName(reservation.getRoom().getFloor().getName())
                .buildingName(reservation.getRoom().getFloor().getBuilding().getName())
                .userName(reservation.getUser().getUserInfo().getFullName())
                .userEmail(reservation.getUser().getUserInfo().getEmail())
                .userPhoneNumber(reservation.getUser().getUserInfo() != null ? reservation.getUser().getUserInfo().getPhoneNumber() : null)
                .build();
    }

    public ReservationResponse toResponse(Reservation reservation) {
        if (reservation == null) return null;

        var room = reservation.getRoom();
        var roomDto = room != null ? roomMapper.toDto(room) : null;
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
        response.setReason(reservation.getReason());
        response.setCancelBy(reservation.getCancelBy());

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
        response.setPurpose(myReservationProjection.getPurpose());
        response.setNote(myReservationProjection.getNote());
        response.setSelectedDate(myReservationProjection.getSelectedDate());
        response.setStartTime(myReservationProjection.getStartTime());
        response.setEndTime(myReservationProjection.getEndTime());
        response.setDuration(myReservationProjection.getDuration());
        var isFeedback = myReservationProjection.getIsFeedback();
        response.setIsFeedback(isFeedback != null && isFeedback == 1L);
        return response;
    }

}
