package com.finalProject.BookingMeetingRoom.controller.dashboard;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;

import com.finalProject.BookingMeetingRoom.common.payload.Response;
import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.model.request.BuildingCreateRequest;
import com.finalProject.BookingMeetingRoom.service.DashboardService;
import com.finalProject.BookingMeetingRoom.service.ReservationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(value = "/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final ReservationService reservationService;

    @GetMapping("/overview-stats")
    @PreAuthorize("hasAnyAuthority(@authorityConstant.ADMIN)")
    public ResponseEntity<?> getOverviewStats() {
        return ResponseEntity.ok(Response.ofSucceeded(dashboardService.getOverviewStats()));
    }

    @GetMapping("/reservations")
    @PreAuthorize("hasAnyAuthority(@authorityConstant.ADMIN)")
    public ResponseEntity<?> getAllReservationsForAdmin(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String userEmail,
            @RequestParam(required = false) String roomName,
            @RequestParam(required = false) String floorName,
            @RequestParam(required = false) String buildingName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return ResponseEntity.ok(Response.ofSucceeded(reservationService.getAllReservationsForAdmin(page, size, status, userName, userEmail, roomName, floorName, buildingName, startDate, endDate)));
    }

    @GetMapping(value = "/rooms-map")
    public ResponseEntity<?> getRoomsMap(){
        return ResponseEntity.ok(Response.ofSucceeded(dashboardService.getRoomsMapDashboard()));
    }

    @GetMapping(value = "/all-buildings")
    public ResponseEntity<?> getAllBuildings(){
        return ResponseEntity.ok(Response.ofSucceeded(dashboardService.getAllBuildings()));
    }

    @GetMapping(value = "/all-floors-by-buildingId/{buildingId}")
    public  ResponseEntity<?> getAllFloorsByBuildingId(@PathVariable("buildingId") String buildingId) {
        return ResponseEntity.ok(Response.ofSucceeded(dashboardService.getAllFloorsByBuildingId(buildingId)));
    }

    @GetMapping(value = "/all-room-by-floorId/{floorId}")
    public  ResponseEntity<?> getAllRoomsByFloorId(@PathVariable("floorId") String floorId) {
        return ResponseEntity.ok(Response.ofSucceeded(dashboardService.getAllRoomsByFloorId(floorId)));
    }

    @GetMapping("/all-users")
    @PreAuthorize("hasAnyAuthority(@authorityConstant.ADMIN)")
    public ResponseEntity<?> getAllUsers(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(Response.ofSucceeded(dashboardService.getAllUsers(page, size)));
    }

    @PutMapping("/lock-user/{userId}")
    @PreAuthorize("hasAnyAuthority(@authorityConstant.ADMIN)")
    public ResponseEntity<?> lockUser(@PathVariable String userId) {
        dashboardService.lockUser(userId);
        return ResponseEntity.ok(Response.ofSucceeded("User locked successfully"));
    }

    @PutMapping("/unlock-user/{userId}")
    @PreAuthorize("hasAnyAuthority(@authorityConstant.ADMIN)")
    public ResponseEntity<?> unlockUser(@PathVariable String userId) {
        dashboardService.unlockUser(userId);
        return ResponseEntity.ok(Response.ofSucceeded("Unlock user successfully"));
    }

    // start add createBuilding api
    @PostMapping("/buildings")
    @PreAuthorize("hasAnyAuthority(@authorityConstant.ADMIN)")
    public ResponseEntity<?> createBuilding(@RequestBody BuildingCreateRequest request) {
        dashboardService.createBuilding(request);
        return ResponseEntity.ok(Response.ofSucceeded("Building created successfully"));
    }
    // end add createBuilding api
}