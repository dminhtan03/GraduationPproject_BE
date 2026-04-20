package com.finalProject.BookingMeetingRoom.service.impl;

import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.AcademicSchedule;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.request.AcademicScheduleCreateRequest;
import com.finalProject.BookingMeetingRoom.model.request.AcademicScheduleUpdateRequest;
import com.finalProject.BookingMeetingRoom.model.response.AcademicScheduleResponse;
import com.finalProject.BookingMeetingRoom.repository.AcademicScheduleRepository;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import com.finalProject.BookingMeetingRoom.service.AcademicScheduleService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AcademicScheduleServiceImpl implements AcademicScheduleService {

    private static final Logger logger = LoggerFactory.getLogger(AcademicScheduleServiceImpl.class);
    private final AcademicScheduleRepository scheduleRepository;
    private final RoomRepository roomRepository;
    private final ReservationRepository reservationRepository;
    private final ObjectProvider<com.finalProject.BookingMeetingRoom.service.ReservationService> reservationServiceProvider;

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
            List<String> errors = new ArrayList<>();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String roomCode = getCellValueAsString(row.getCell(0));
                if (roomCode.isEmpty()) continue;

                try {
                    LocalTime startTime = parseLocalTime(row.getCell(1));
                    LocalTime endTime = parseLocalTime(row.getCell(2));
                    String days = getCellValueAsString(row.getCell(3));
                    LocalDate fromDate = parseLocalDate(row.getCell(4));
                    LocalDate toDate = parseLocalDate(row.getCell(5));
                    String desc = getCellValueAsString(row.getCell(6));

                    if (!startTime.isBefore(endTime)) {
                        errors.add(String.format("Dòng %d: Giờ bắt đầu (%s) phải trước giờ kết thúc (%s)", i + 1, startTime, endTime));
                        continue;
                    }

                    Room room = roomRepository.findByLocationCode(roomCode)
                            .orElseThrow(() -> new CustomException(ResponseCode.ROOM_NOT_FOUND, "Phòng " + roomCode + " không tồn tại"));

                    // Kiểm tra trùng lặp với dữ liệu đã có trong database
                    try {
                        validateNoOverlap(room.getId(), fromDate, toDate, startTime, endTime, days, null);
                    } catch (CustomException e) {
                        errors.add(String.format("Dòng %d: %s", i + 1, e.getMessage()));
                        continue;
                    }

                    // Kiểm tra trùng lặp với các bản ghi khác trong cùng file excel
                    boolean internalOverlap = false;
                    for (AcademicSchedule existingInBatch : schedulesToSave) {
                        if (existingInBatch.getRoom().getId().equals(room.getId())) {
                            if (!(fromDate.isAfter(existingInBatch.getToDate()) || toDate.isBefore(existingInBatch.getFromDate()))) {
                                if (startTime.isBefore(existingInBatch.getEndTime()) && endTime.isAfter(existingInBatch.getStartTime())) {
                                    String[] newDays = days.split(",");
                                    String[] batchDays = existingInBatch.getDaysOfWeek().split(",");
                                    for (String nDay : newDays) {
                                        for (String bDay : batchDays) {
                                            if (nDay.trim().equalsIgnoreCase(bDay.trim())) {
                                                errors.add(String.format("Dòng %d: Trùng lịch học ngay trong file Excel cho phòng %s vào %s", i + 1, roomCode, nDay.trim()));
                                                internalOverlap = true;
                                                break;
                                            }
                                        }
                                        if (internalOverlap) break;
                                    }
                                }
                            }
                        }
                        if (internalOverlap) break;
                    }

                    if (internalOverlap) continue;

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
                } catch (Exception e) {
                    errors.add(String.format("Dòng %d: Lỗi định dạng hoặc dữ liệu không hợp lệ (%s)", i + 1, e.getMessage()));
                }
            }

            if (!errors.isEmpty()) {
                throw new CustomException(ResponseCode.ACADEMIC_SCHEDULE_OVERLAP, String.join("\n", errors));
            }

            for (AcademicSchedule schedule : schedulesToSave) {
                forceCancelOverlappingReservations(
                        schedule.getRoom().getId(),
                        schedule.getFromDate(),
                        schedule.getToDate(),
                        schedule.getStartTime(),
                        schedule.getEndTime(),
                        schedule.getDaysOfWeek()
                );
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
    @Transactional
    public void deleteSchedules(List<String> scheduleIds) {
        if (scheduleIds != null && !scheduleIds.isEmpty()) {
            scheduleRepository.deleteAllById(scheduleIds);
            updateRoomStatusesBySchedule();
        }
    }

    @Override
    @Transactional
    public void bulkUpdateSchedules(List<String> scheduleIds, AcademicScheduleUpdateRequest request) {
        if (scheduleIds == null || scheduleIds.isEmpty()) return;

        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "Giờ bắt đầu phải trước giờ kết thúc");
        }

        List<AcademicSchedule> schedules = scheduleRepository.findAllById(scheduleIds);
        String daysOfWeek = String.join(",", request.getDaysOfWeek()).toUpperCase();

        for (AcademicSchedule schedule : schedules) {
            validateNoOverlap(schedule.getRoom().getId(), request.getFromDate(), request.getToDate(),
                    request.getStartTime(), request.getEndTime(), daysOfWeek, schedule.getId());

            forceCancelOverlappingReservations(
                    schedule.getRoom().getId(),
                    request.getFromDate(),
                    request.getToDate(),
                    request.getStartTime(),
                    request.getEndTime(),
                    daysOfWeek
            );

            schedule.setStartTime(request.getStartTime());
            schedule.setEndTime(request.getEndTime());
            schedule.setDaysOfWeek(daysOfWeek);
            schedule.setFromDate(request.getFromDate());
            schedule.setToDate(request.getToDate());
            if (request.getDescription() != null && !request.getDescription().isEmpty()) {
                schedule.setDescription(request.getDescription());
            }
        }

        scheduleRepository.saveAll(schedules);
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
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "Giờ bắt đầu phải trước giờ kết thúc");
        }

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new CustomException(ResponseCode.ROOM_NOT_FOUND));

        String daysOfWeek = String.join(",", request.getDaysOfWeek()).toUpperCase();
        validateNoOverlap(room.getId(), request.getFromDate(), request.getToDate(),
                request.getStartTime(), request.getEndTime(), daysOfWeek, null);

        forceCancelOverlappingReservations(
                room.getId(),
                request.getFromDate(),
                request.getToDate(),
                request.getStartTime(),
                request.getEndTime(),
                daysOfWeek
        );

        AcademicSchedule schedule = AcademicSchedule.builder()
                .id(UUID.randomUUID().toString())
                .room(room)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .daysOfWeek(daysOfWeek)
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
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "Giờ bắt đầu phải trước giờ kết thúc");
        }

        AcademicSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new CustomException(ResponseCode.INTERNAL_SERVER_ERROR, "Schedule not found"));

        String daysOfWeek = String.join(",", request.getDaysOfWeek()).toUpperCase();
        validateNoOverlap(schedule.getRoom().getId(), request.getFromDate(), request.getToDate(),
                request.getStartTime(), request.getEndTime(), daysOfWeek, scheduleId);

        forceCancelOverlappingReservations(
                schedule.getRoom().getId(),
                request.getFromDate(),
                request.getToDate(),
                request.getStartTime(),
                request.getEndTime(),
                daysOfWeek
        );

        schedule.setStartTime(request.getStartTime());
        schedule.setEndTime(request.getEndTime());
        schedule.setDaysOfWeek(daysOfWeek);
        schedule.setFromDate(request.getFromDate());
        schedule.setToDate(request.getToDate());
        schedule.setDescription(request.getDescription());

        scheduleRepository.save(schedule);
        updateRoomStatusesBySchedule();
    }

    private void validateNoOverlap(String roomId, LocalDate fromDate, LocalDate toDate, LocalTime startTime, LocalTime endTime, String daysOfWeek, String currentScheduleId) {
        List<AcademicSchedule> potentialOverlaps = scheduleRepository.findPotentialOverlappingSchedules(
                roomId, fromDate, toDate, startTime, endTime);

        String[] newDays = daysOfWeek.split(",");

        for (AcademicSchedule existing : potentialOverlaps) {
            // Bỏ qua bản ghi hiện tại nếu đang thực hiện cập nhật
            if (currentScheduleId != null && existing.getId().equals(currentScheduleId)) {
                continue;
            }

            String[] existingDays = existing.getDaysOfWeek().split(",");
            for (String newDay : newDays) {
                String nDay = newDay.trim();
                if (nDay.isEmpty()) continue;
                for (String existingDay : existingDays) {
                    if (nDay.equalsIgnoreCase(existingDay.trim())) {
                        throw new CustomException(ResponseCode.ACADEMIC_SCHEDULE_OVERLAP,
                                String.format("Phòng %s đã có lịch học vào %s (từ %s đến %s)",
                                        existing.getRoom().getLocationCode(),
                                        nDay.toUpperCase(),
                                        existing.getStartTime(),
                                        existing.getEndTime()));
                    }
                }
            }
        }
    }

    private void forceCancelOverlappingReservations(
            String roomId,
            LocalDate fromDate,
            LocalDate toDate,
            LocalTime startTime,
            LocalTime endTime,
            String daysOfWeek
    ) {
        Authentication authentication = SecurityContextHolder.getContext() != null
                ? SecurityContextHolder.getContext().getAuthentication()
                : null;
        if (authentication == null || authentication.getName() == null) {
            return;
        }
        String principalName = authentication.getName();
        if (principalName.isBlank() || "anonymousUser".equalsIgnoreCase(principalName)) {
            return;
        }

        Set<DayOfWeek> targetDays = parseDaysOfWeek(daysOfWeek);
        if (targetDays.isEmpty()) {
            return;
        }

        LocalDate date = fromDate;
        while (!date.isAfter(toDate)) {
            if (targetDays.contains(date.getDayOfWeek())) {
                LocalDateTime scheduleStart = date.atTime(startTime);
                LocalDateTime scheduleEnd = date.atTime(endTime);

                List<Reservation> overlapping = reservationRepository.findOverlappingReservations(
                        roomId,
                        scheduleStart,
                        scheduleEnd
                );

                for (Reservation reservation : overlapping) {
                    if (reservation.getStatus() == ReservationStatus.RESERVED
                            || reservation.getStatus() == ReservationStatus.IN_USE) {
                        reservationServiceProvider.getObject().forceCancelReservation(
                                reservation.getId(),
                                "Cancelled due to adding/updating the fixed class schedule.",
                                authentication
                        );
                    }
                }
            }
            date = date.plusDays(1);
        }
    }

    private Set<DayOfWeek> parseDaysOfWeek(String daysOfWeek) {
        Set<DayOfWeek> result = new HashSet<>();
        if (daysOfWeek == null || daysOfWeek.isBlank()) {
            return result;
        }

        String[] tokens = daysOfWeek.split(",");
        for (String token : tokens) {
            String t = token == null ? "" : token.trim().toUpperCase();
            if (t.isEmpty()) continue;
            result.add(parseDayToken(t));
        }
        return result;
    }

    private DayOfWeek parseDayToken(String token) {
        switch (token) {
            case "MON": return DayOfWeek.MONDAY;
            case "TUE": return DayOfWeek.TUESDAY;
            case "WED": return DayOfWeek.WEDNESDAY;
            case "THU": return DayOfWeek.THURSDAY;
            case "FRI": return DayOfWeek.FRIDAY;
            case "SAT": return DayOfWeek.SATURDAY;
            case "SUN": return DayOfWeek.SUNDAY;
            default: return DayOfWeek.valueOf(token);
        }
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
