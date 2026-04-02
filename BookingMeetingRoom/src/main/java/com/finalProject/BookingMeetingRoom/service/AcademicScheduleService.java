package com.finalProject.BookingMeetingRoom.service;

import com.finalProject.BookingMeetingRoom.model.entity.AcademicSchedule;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

public interface AcademicScheduleService {

    /**
     * Kiểm tra xem một phòng có đang bận lịch học trong khoảng thời gian cho trước hay không.
     */
    boolean isRoomBusyWithLearning(String roomId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * Import lịch học từ file Excel.
     */
    void importSchedulesFromExcel(MultipartFile file);

    /**
     * Lấy tất cả lịch học của một phòng.
     */
    List<AcademicSchedule> getSchedulesByRoomId(String roomId);

    /**
     * Xóa một lịch học.
     */
    void deleteSchedule(String scheduleId);
}