package com.finalProject.BookingMeetingRoom.controller.room;

import com.finalProject.BookingMeetingRoom.common.payload.Response;
import com.finalProject.BookingMeetingRoom.service.AcademicScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/academic-schedules")
@RequiredArgsConstructor
public class AcademicScheduleController {

    private final AcademicScheduleService scheduleService;

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