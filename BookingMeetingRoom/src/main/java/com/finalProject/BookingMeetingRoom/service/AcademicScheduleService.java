package com.finalProject.BookingMeetingRoom.service;

import com.finalProject.BookingMeetingRoom.model.entity.AcademicSchedule;
import com.finalProject.BookingMeetingRoom.model.request.AcademicScheduleCreateRequest;
import com.finalProject.BookingMeetingRoom.model.request.AcademicScheduleUpdateRequest;
import com.finalProject.BookingMeetingRoom.model.response.AcademicScheduleResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
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

    /**
     * Xóa hàng loạt lịch học.
     */
    void deleteSchedules(List<String> scheduleIds);

    /**
     * Cập nhật hàng loạt lịch học.
     */
    void bulkUpdateSchedules(List<String> scheduleIds, AcademicScheduleUpdateRequest request);

    /**
     * Tìm kiếm lịch học cố định với các tham số filter.
     */
    Page<AcademicScheduleResponse> searchSchedules(
            String roomName,
            String floorId,
            String buildingId,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable);

    /**
     * Thêm lịch học thủ công.
     */
    void createSchedule(AcademicScheduleCreateRequest request);

    /**
     * Cập nhật lịch học.
     */
    void updateSchedule(String scheduleId, AcademicScheduleUpdateRequest request);
}