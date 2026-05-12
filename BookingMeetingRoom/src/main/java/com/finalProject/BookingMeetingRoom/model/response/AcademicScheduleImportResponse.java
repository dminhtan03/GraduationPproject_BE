package com.finalProject.BookingMeetingRoom.model.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// start+ chức năng import lịch học cố định từ Excel (trả về số lượng import + danh sách lỗi)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AcademicScheduleImportResponse {
    private int importedCount;
    private List<String> errors;
}
// end+ chức năng import lịch học cố định từ Excel (trả về số lượng import + danh sách lỗi)
