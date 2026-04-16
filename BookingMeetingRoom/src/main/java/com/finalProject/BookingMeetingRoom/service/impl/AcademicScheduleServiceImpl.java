package com.finalProject.BookingMeetingRoom.service.impl;

import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.model.entity.AcademicSchedule;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.request.AcademicScheduleCreateRequest;
import com.finalProject.BookingMeetingRoom.model.request.AcademicScheduleUpdateRequest;
import com.finalProject.BookingMeetingRoom.model.response.AcademicScheduleResponse;
import com.finalProject.BookingMeetingRoom.repository.AcademicScheduleRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import com.finalProject.BookingMeetingRoom.service.AcademicScheduleService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AcademicScheduleServiceImpl implements AcademicScheduleService {

    private static final Logger logger = LoggerFactory.getLogger(AcademicScheduleServiceImpl.class);
    private final AcademicScheduleRepository scheduleRepository;
    private final RoomRepository roomRepository;

    @Override
    public boolean isRoomBusyWithLearning(String roomId, LocalDateTime startTime, LocalDateTime endTime) {
        // [HYBRID] Logic kiểm tra xem khoảng thời gian (startTime - endTime) có trùng với lịch học cố định không
        LocalDate date = startTime.toLocalDate();
        LocalTime start = startTime.toLocalTime();
        LocalTime end = endTime.toLocalTime();
        String dayOfWeek = date.getDayOfWeek().name();

        List<AcademicSchedule> schedules = scheduleRepository.findSchedulesByRoomAndDate(roomId, date);

        for (AcademicSchedule schedule : schedules) {
            // Kiểm tra xem thứ hiện tại có nằm trong các ngày áp dụng của luật không
            // Dùng regex hoặc split để kiểm tra chính xác hơn tránh lỗi trùng chuỗi (vd: MONDAY vs TUESDAY)
            String[] days = schedule.getDaysOfWeek().split(",");
            boolean isTodayInSchedule = false;
            for (String day : days) {
                if (day.trim().equalsIgnoreCase(dayOfWeek)) {
                    isTodayInSchedule = true;
                    break;
                }
            }

            if (isTodayInSchedule) {
                // Kiểm tra xem khoảng thời gian hiện tại có giao thoa với giờ học không
                // Logic: (start < schedule_end) && (end > schedule_start)
                if (start.isBefore(schedule.getEndTime()) && end.isAfter(schedule.getStartTime())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    @Transactional
    public void importSchedulesFromExcel(MultipartFile file) {
        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            List<AcademicSchedule> schedulesToSave = new ArrayList<>();
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                // Cấu trúc file Excel giả định:
                // Cột 0: RoomCode (vd: R001)
                // Cột 1: StartTime (vd: 07:00)
                // Cột 2: EndTime (vd: 17:00)
                // Cột 3: DaysOfWeek (vd: MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY)
                // Cột 4: FromDate (vd: 2026-03-31)
                // Cột 5: ToDate (vd: 2026-06-30)
                // Cột 6: Description (vd: Lớp học K17)

                String roomCode = getCellValueAsString(row.getCell(0));
                if (roomCode.isEmpty()) continue;

                LocalTime startTime = parseLocalTime(row.getCell(1));
                LocalTime endTime = parseLocalTime(row.getCell(2));
                String days = getCellValueAsString(row.getCell(3));
                LocalDate fromDate = parseLocalDate(row.getCell(4));
                LocalDate toDate = parseLocalDate(row.getCell(5));
                String desc = getCellValueAsString(row.getCell(6));

                Room room = roomRepository.findByLocationCode(roomCode)
                        .orElseThrow(() -> new CustomException(ResponseCode.ROOM_NOT_FOUND));

                AcademicSchedule schedule = AcademicSchedule.builder()
                        .id(UUID.randomUUID().toString())
                        .room(room)
                        .startTime(startTime)
                        .endTime(endTime)
                        .daysOfWeek(days.toUpperCase())
                        .fromDate(fromDate)
                        .toDate(toDate)
                        .description(desc)
                        .build();

                schedulesToSave.add(schedule);
            }

            scheduleRepository.saveAll(schedulesToSave);
            logger.info("Imported {} academic schedules from excel", schedulesToSave.size());
            updateRoomStatusesBySchedule();

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error importing academic schedules: " + e.getMessage(), e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    private LocalTime parseLocalTime(Cell cell) {
        if (cell == null) return LocalTime.MIDNIGHT;
        if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getLocalDateTimeCellValue().toLocalTime();
        }
        String val = getCellValueAsString(cell);
        if (val.isEmpty()) return LocalTime.MIDNIGHT;
        try {
            return LocalTime.parse(val);
        } catch (Exception e) {
            // Trường hợp Excel lưu định dạng 7:00 thay vì 07:00
            if (val.length() == 4 && val.contains(":")) {
                return LocalTime.parse("0" + val);
            }
            throw e;
        }
    }

    private LocalDate parseLocalDate(Cell cell) {
        if (cell == null) return LocalDate.now();
        if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String val = getCellValueAsString(cell);
        if (val.isEmpty()) return LocalDate.now();
        return LocalDate.parse(val);
    }

    @Override
    public List<AcademicSchedule> getSchedulesByRoomId(String roomId) {
        return scheduleRepository.findByRoomId(roomId);
    }

    @Override
    @Transactional
    public void deleteSchedule(String scheduleId) {
        scheduleRepository.deleteById(scheduleId);
        updateRoomStatusesBySchedule();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AcademicScheduleResponse> searchSchedules(String roomName, String floorId, String buildingId, LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        return scheduleRepository.searchSchedules(roomName, floorId, buildingId, fromDate, toDate, pageable)
                .map(s -> AcademicScheduleResponse.builder()
                        .id(s.getId())
                        .roomId(s.getRoom().getId())
                        .roomName(s.getRoom().getLocationCode())
                        .floorName(s.getRoom().getFloor() != null ? s.getRoom().getFloor().getName() : "N/A")
                        .buildingName(s.getRoom().getFloor() != null && s.getRoom().getFloor().getBuilding() != null
                                ? s.getRoom().getFloor().getBuilding().getName() : "N/A")
                        .startTime(s.getStartTime())
                        .endTime(s.getEndTime())
                        .daysOfWeek(s.getDaysOfWeek())
                        .fromDate(s.getFromDate())
                        .toDate(s.getToDate())
                        .description(s.getDescription())
                        .createdAt(s.getCreatedAt())
                        .build());
    }

    @Override
    @Transactional
    public void createSchedule(AcademicScheduleCreateRequest request) {
        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new CustomException(ResponseCode.ROOM_NOT_FOUND));

        AcademicSchedule schedule = AcademicSchedule.builder()
                .id(UUID.randomUUID().toString())
                .room(room)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .daysOfWeek(String.join(",", request.getDaysOfWeek()).toUpperCase())
                .fromDate(request.getFromDate())
                .toDate(request.getToDate())
                .description(request.getDescription())
                .build();

        scheduleRepository.save(schedule);
        updateRoomStatusesBySchedule();
    }

    @Override
    @Transactional
    public void updateSchedule(String scheduleId, AcademicScheduleUpdateRequest request) {
        AcademicSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new CustomException(ResponseCode.INTERNAL_SERVER_ERROR, "Schedule not found"));

        schedule.setStartTime(request.getStartTime());
        schedule.setEndTime(request.getEndTime());
        schedule.setDaysOfWeek(String.join(",", request.getDaysOfWeek()).toUpperCase());
        schedule.setFromDate(request.getFromDate());
        schedule.setToDate(request.getToDate());
        schedule.setDescription(request.getDescription());

        scheduleRepository.save(schedule);
        updateRoomStatusesBySchedule();
    }

    /**
     * Tự động cập nhật trạng thái phòng dựa trên lịch học cố định.
     * Chạy mỗi phút để đảm bảo trạng thái phòng luôn chính xác.
     */
    @Scheduled(fixedRate = 60000) // 1 phút
    @Transactional
    public void updateRoomStatusesBySchedule() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        LocalTime currentTime = now.toLocalTime();
        String currentDayOfWeek = today.getDayOfWeek().name();

        List<Room> allRooms = roomRepository.findAll();
        List<AcademicSchedule> activeSchedulesToday = scheduleRepository.findAllActiveSchedulesToday(today);

        for (Room room : allRooms) {
            // Chỉ cập nhật trạng thái cho những phòng đang AVAILABLE hoặc LEARNING
            // Để tránh ghi đè các trạng thái BROKEN hoặc UNAVAILABLE do Admin cài đặt thủ công
            if (room.getStatus() != RoomStatus.AVAILABLE && room.getStatus() != RoomStatus.LEARNING) {
                continue;
            }

            boolean isCurrentlyLearning = false;
            for (AcademicSchedule schedule : activeSchedulesToday) {
                if (schedule.getRoom().getId().equals(room.getId())) {
                    // Kiểm tra thứ trong tuần
                    String[] days = schedule.getDaysOfWeek().split(",");
                    boolean isTodayInSchedule = false;
                    for (String day : days) {
                        if (day.trim().equalsIgnoreCase(currentDayOfWeek)) {
                            isTodayInSchedule = true;
                            break;
                        }
                    }

                    if (isTodayInSchedule) {
                        // Kiểm tra xem thời gian hiện tại có nằm trong khoảng [startTime, endTime] không
                        if (!currentTime.isBefore(schedule.getStartTime()) && currentTime.isBefore(schedule.getEndTime())) {
                            isCurrentlyLearning = true;
                            break;
                        }
                    }
                }
            }

            // Cập nhật trạng thái phòng
            if (isCurrentlyLearning && room.getStatus() != RoomStatus.LEARNING) {
                room.setStatus(RoomStatus.LEARNING);
                roomRepository.save(room);
                logger.info("Room {} status changed to LEARNING due to academic schedule", room.getLocationCode());
            } else if (!isCurrentlyLearning && room.getStatus() == RoomStatus.LEARNING) {
                room.setStatus(RoomStatus.AVAILABLE);
                roomRepository.save(room);
                logger.info("Room {} status changed back to AVAILABLE (academic schedule ended)", room.getLocationCode());
            }
        }
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    // Trả về định dạng ISO để parse sau
                    return cell.getLocalDateTimeCellValue().toString();
                }
                return String.valueOf((int) cell.getNumericCellValue());
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default: return "";
        }
    }
}