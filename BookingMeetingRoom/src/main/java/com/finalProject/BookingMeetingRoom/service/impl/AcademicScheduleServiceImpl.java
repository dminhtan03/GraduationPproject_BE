package com.finalProject.BookingMeetingRoom.service.impl;

import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.model.entity.AcademicSchedule;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.repository.AcademicScheduleRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import com.finalProject.BookingMeetingRoom.service.AcademicScheduleService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error importing academic schedules: " + e.getMessage(), e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    private LocalTime parseLocalTime(Cell cell) {
        if (cell == null) return LocalTime.MIDNIGHT;
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalTime();
        }
        String val = getCellValueAsString(cell);
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
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        return LocalDate.parse(getCellValueAsString(cell));
    }

    @Override
    public List<AcademicSchedule> getSchedulesByRoomId(String roomId) {
        return scheduleRepository.findByRoomId(roomId);
    }

    @Override
    @Transactional
    public void deleteSchedule(String scheduleId) {
        scheduleRepository.deleteById(scheduleId);
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
