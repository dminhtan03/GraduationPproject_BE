package com.finalProject.BookingMeetingRoom.controller.room;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.finalProject.BookingMeetingRoom.common.payload.Response;
import com.finalProject.BookingMeetingRoom.model.request.AcademicScheduleCreateRequest;
import com.finalProject.BookingMeetingRoom.model.request.AcademicScheduleUpdateRequest;
import com.finalProject.BookingMeetingRoom.service.AcademicScheduleService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

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
        // start+ chức năng import lịch học cố định từ Excel (trả về lỗi đầy đủ + vẫn import các dòng hợp lệ)
        var result = scheduleService.importSchedulesFromExcel(file);
        return ResponseEntity.ok(Response.ofSucceeded(result));
        // end+ chức năng import lịch học cố định từ Excel (trả về lỗi đầy đủ + vẫn import các dòng hợp lệ)
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

    /**
     * API Xóa hàng loạt lịch học.
     */
    @DeleteMapping("/bulk")
    public ResponseEntity<?> deleteSchedules(@RequestBody List<String> scheduleIds) {
        scheduleService.deleteSchedules(scheduleIds);
        return ResponseEntity.ok(Response.ofSucceeded("Đã xóa các lịch học được chọn thành công"));
    }

    /**
     * API Cập nhật hàng loạt lịch học.
     */
    @PutMapping("/bulk")
    public ResponseEntity<?> bulkUpdateSchedules(
            @RequestParam List<String> scheduleIds,
            @Valid @RequestBody AcademicScheduleUpdateRequest request) {
        scheduleService.bulkUpdateSchedules(scheduleIds, request);
        return ResponseEntity.ok(Response.ofSucceeded("Đã cập nhật các lịch học được chọn thành công"));
    }
}
