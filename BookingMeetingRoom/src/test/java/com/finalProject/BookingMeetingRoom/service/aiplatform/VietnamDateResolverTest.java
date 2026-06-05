package com.finalProject.BookingMeetingRoom.service.aiplatform;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VietnamDateResolverTest {

    @Test
    void resolve_shouldReturnNull_forNullOrNullLiteral() {
        LocalDate today = LocalDate.of(2026, 6, 5);

        assertNull(VietnamDateResolver.resolve(null, today));
        assertNull(VietnamDateResolver.resolve("null", today));
        assertNull(VietnamDateResolver.resolve(" ", today));
    }

//    @Test
//    void resolve_shouldReturnIsoDate_whenValidIso() {
//        LocalDate today = LocalDate.of(2026, 6, 5);
//
//        assertEquals("2026-06-04", VietnamDateResolver.resolve("2026-06-04", today));
//        assertNull(VietnamDateResolver.resolve("2023-01-01", today));
//    }

//    @Test
//    void resolve_shouldHandleRelativeWeekday() {
//        LocalDate today = LocalDate.of(2026, 6, 5); // Friday
//
//        assertEquals("2026-06-08", VietnamDateResolver.resolve("thứ 2 tuần tới", today));
//    }

    @Test
    void resolve_shouldHandleExplicitDates_withoutYear() {
        LocalDate today = LocalDate.of(2026, 6, 5);

        assertEquals("2027-06-04", VietnamDateResolver.resolve("4/6", today));
        assertEquals("2026-06-04", VietnamDateResolver.resolve("ngày 4 tháng 6 năm 2026", today));
    }

    @Test
    void resolve_shouldHandleSimpleRelative() {
        LocalDate today = LocalDate.of(2026, 6, 5);

        assertEquals("2026-06-06", VietnamDateResolver.resolve("ngày mai", today));
        assertEquals("2026-06-07", VietnamDateResolver.resolve("ngày kia", today));
        assertEquals("2026-07-01", VietnamDateResolver.resolve("tháng sau", today));
    }
}
