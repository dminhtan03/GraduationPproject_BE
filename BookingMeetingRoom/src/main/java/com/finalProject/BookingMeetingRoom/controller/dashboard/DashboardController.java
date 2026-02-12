package com.finalProject.BookingMeetingRoom.controller.dashboard;

import com.finalProject.BookingMeetingRoom.common.payload.Response;
import com.finalProject.BookingMeetingRoom.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

//    @GetMapping(value = "")
//    public ResponseEntity<?> getDashboard(
//            Authentication connectedUser
//    ) {
//        return ResponseEntity.ok(Response.ofSucceeded(dashboardService.getDashboard(connectedUser)));
//    }

    @GetMapping(value = "/seats-map")
    public ResponseEntity<?> getRoomsMap(){
        return ResponseEntity.ok(Response.ofSucceeded(dashboardService.getRoomsMapDashboard()));
    }

//    @GetMapping(value = "/summary")
//    @PreAuthorize("hasAnyAuthority(@authorityConstant.ADMIN)")
//    public ResponseEntity<?> getAdminDashboard(){
//        return ResponseEntity.ok(Response.ofSucceeded(dashboardService.getDashboardSummary()));
//    }

//    @GetMapping(value = "/overview")
//    @PreAuthorize("hasAnyAuthority(@authorityConstant.ADMIN)")
//    public ResponseEntity<?> getOverviewDashboard(){
//        return ResponseEntity.ok(Response.ofSucceeded(dashboardService.getDashboardOverview()));
//    }

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

}