package com.finalProject.BookingMeetingRoom.controller.dashboard;

import com.finalProject.BookingMeetingRoom.common.payload.Response;
import com.finalProject.BookingMeetingRoom.model.response.UserDashboardResponse;
import com.finalProject.BookingMeetingRoom.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

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
        return ResponseEntity.ok(Response.ofSucceeded("User unlocked successfully"));
    }

}