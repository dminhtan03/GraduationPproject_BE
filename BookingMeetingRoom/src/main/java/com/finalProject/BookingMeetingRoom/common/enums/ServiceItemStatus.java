package com.finalProject.BookingMeetingRoom.common.enums;

// start+ chức năng service item status (trạng thái xử lý dịch vụ)
public enum ServiceItemStatus {
    PENDING,       // Vừa được đặt, chờ admin xác nhận
    CONFIRMED,     // Admin đã xác nhận, đang chuẩn bị
    IN_PROGRESS,   // Đang thực hiện cung cấp dịch vụ
    DONE,          // Hoàn thành
    CANCELLED      // Huỷ
}
// end+ chức năng service item status
