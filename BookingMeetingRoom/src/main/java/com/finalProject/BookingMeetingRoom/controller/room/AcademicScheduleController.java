package com.finalProject.BookingMeetingRoom.controller.room;

import com.finalProject.BookingMeetingRoom.common.payload.Response;
import com.finalProject.BookingMeetingRoom.model.request.AcademicScheduleCreateRequest;
import com.finalProject.BookingMeetingRoom.model.request.AcademicScheduleUpdateRequest;
import com.finalProject.BookingMeetingRoom.service.AcademicScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/academic-schedules")
@RequiredArgsConstructor
public class AcademicScheduleController {

    private final AcademicScheduleService scheduleService;

    /**
     * API Lấy danh sách lịch học cố định với search.
     */
    @GetMapping
    public ResponseEntity<?> searchSchedules(
            @RequestParam(required = false) String roomName,
            @RequestParam(required = false) String floorId,
            @RequestParam(required = false) String buildingId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Pageable pageable) {
        return ResponseEntity.ok(Response.ofSucceeded(scheduleService.searchSchedules(roomName, floorId, buildingId, fromDate, toDate, pageable)));
    }

    /**
     * API Thêm lịch học thủ công.
     */
    @PostMapping
    public ResponseEntity<?> createSchedule(@Valid @RequestBody AcademicScheduleCreateRequest request) {
        scheduleService.createSchedule(request);
        return ResponseEntity.ok(Response.ofSucceeded("Đã thêm lịch học thành công"));
    }

    /**
     * API Cập nhật lịch học.
     */
    @PutMapping("/{scheduleId}")
    public ResponseEntity<?> updateSchedule(
            @PathVariable String scheduleId,
            @Valid @RequestBody AcademicScheduleUpdateRequest request) {
        scheduleService.updateSchedule(scheduleId, request);
        return ResponseEntity.ok(Response.ofSucceeded("Đã cập nhật lịch học thành công"));
    }

    /**
     * API Import lịch học từ file Excel.
     */
    @PostMapping("/import")
    public ResponseEntity<?> importSchedules(@RequestParam("file") MultipartFile file) {
        scheduleService.importSchedulesFromExcel(file);
        return ResponseEntity.ok(Response.ofSucceeded("Lịch học đã được nhập thành công từ file Excel"));
    }

    /**
     * API Lấy danh sách lịch học của một phòng.
     */
    @GetMapping("/room/{roomId}")
    public ResponseEntity<?> getSchedules(@PathVariable String roomId) {
        return ResponseEntity.ok(Response.ofSucceeded(scheduleService.getSchedulesByRoomId(roomId)));
    }

    /**
     * API Xóa lịch học.
     */
    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<?> deleteSchedule(@PathVariable String scheduleId) {
        scheduleService.deleteSchedule(scheduleId);
        return ResponseEntity.ok(Response.ofSucceeded("Đã xóa lịch học thành công"));
    }
}