package com.finalProject.BookingMeetingRoom.service.aiplatform;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves Vietnamese relative date expressions to ISO dates (YYYY-MM-DD).
 */
public class VietnamDateResolver {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static String resolve(String raw, LocalDate today) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("null")) return null;

        String s = raw.toLowerCase().trim()
                .replace("năm nay", String.valueOf(today.getYear()))
                .replace("  ", " ");

        // 1. Already ISO YYYY-MM-DD with valid year (>= 2024)
        if (s.matches("\\d{4}-\\d{2}-\\d{2}")) {
            try {
                LocalDate d = LocalDate.parse(s);
                if (d.getYear() >= 2024) return d.format(ISO);
            } catch (Exception ignored) {}
        }

        // 2. Relative weekday: "thứ N tuần này/sau/tới..."  (check before explicit)
        LocalDate weekday = tryParseRelativeWeekday(s, today);
        if (weekday != null) return weekday.format(ISO);

        // 3. Explicit dates: "ngày 4 tháng 6 năm 2026", "4/6/2026", "4/6"
        LocalDate explicit = tryParseExplicit(s, today);
        if (explicit != null) return explicit.format(ISO);

        // 4. Simple relative: ngày mai, cuối tuần, tháng sau...
        LocalDate simple = tryParseSimple(s, today);
        if (simple != null) return simple.format(ISO);

        return null;
    }

    // ── Relative weekday ────────────────────────────────────────────────────

    private static LocalDate tryParseRelativeWeekday(String s, LocalDate today) {
        DayOfWeek dow = extractDayOfWeek(s);
        if (dow == null) return null;

        int weekOffset;
        if (s.contains("2 tuần") || s.contains("hai tuần") || s.contains("tuần sau nữa")) {
            weekOffset = 2;
        } else if (s.contains("tuần sau") || s.contains("tuần tới") || s.contains("tuần kế")
                || s.contains("tuần tiếp") || s.contains("next week")) {
            weekOffset = 1;
        } else if (s.contains("tuần này") || s.contains("tuần hiện") || s.contains("this week")) {
            weekOffset = 0;
        } else {
            // No qualifier → if the weekday already passed this week, use next week
            LocalDate thisWeekDay = today
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .with(TemporalAdjusters.nextOrSame(dow));
            weekOffset = !thisWeekDay.isAfter(today) && !thisWeekDay.equals(today) ? 1 : 0;
        }

        LocalDate monday = today
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .plusWeeks(weekOffset);
        return monday.with(TemporalAdjusters.nextOrSame(dow));
    }

    private static DayOfWeek extractDayOfWeek(String s) {
        if (s.contains("chủ nhật") || s.contains("cn ") || s.endsWith("cn")) return DayOfWeek.SUNDAY;
        if (s.contains("thứ 7") || s.contains("thu 7") || s.contains("thứ bảy") || s.contains("t7")) return DayOfWeek.SATURDAY;
        if (s.contains("thứ 6") || s.contains("thu 6") || s.contains("thứ sáu") || s.contains("t6")) return DayOfWeek.FRIDAY;
        if (s.contains("thứ 5") || s.contains("thu 5") || s.contains("thứ năm") || s.contains("t5")) return DayOfWeek.THURSDAY;
        if (s.contains("thứ 4") || s.contains("thu 4") || s.contains("thứ tư") || s.contains("t4")) return DayOfWeek.WEDNESDAY;
        if (s.contains("thứ 3") || s.contains("thu 3") || s.contains("thứ ba") || s.contains("t3")) return DayOfWeek.TUESDAY;
        if (s.contains("thứ 2") || s.contains("thu 2") || s.contains("thứ hai") || s.contains("t2")) return DayOfWeek.MONDAY;
        return null;
    }

    // ── Explicit dates ──────────────────────────────────────────────────────

    private static LocalDate tryParseExplicit(String s, LocalDate today) {
        // "ngày 4 tháng 6 năm 2026", "4 tháng 6 2026", "4/6/2026"
        Matcher m = Pattern.compile(
                "(?:ngày\\s*)?(\\d{1,2})\\s*(?:tháng\\s*|/|-)(\\d{1,2})(?:\\s*(?:năm\\s*|/|-)(\\d{4}))?")
                .matcher(s);
        if (m.find()) {
            try {
                int day   = Integer.parseInt(m.group(1));
                int month = Integer.parseInt(m.group(2));
                int year  = m.group(3) != null ? Integer.parseInt(m.group(3)) : today.getYear();
                if (day > 31 || month > 12) return null;
                LocalDate d = LocalDate.of(year, month, day);
                // No year given and date passed → next year
                if (m.group(3) == null && d.isBefore(today)) d = d.plusYears(1);
                return d;
            } catch (Exception ignored) {}
        }

        // "trước/sau 10h ngày 5 tháng 6" — strip time prefix then retry
        String stripped = s.replaceAll("(?:trước|sau|lúc|vào lúc|trước lúc)\\s*\\d{1,2}h(?:\\d{2})?\\s*", "").trim();
        if (!stripped.equals(s)) return tryParseExplicit(stripped, today);

        return null;
    }

    // ── Simple relative ─────────────────────────────────────────────────────

    private static LocalDate tryParseSimple(String s, LocalDate today) {
        if (s.contains("hôm nay") || s.contains("today")) return today;
        if (s.contains("ngày mai") || s.contains("hôm sau") || s.contains("tomorrow")) return today.plusDays(1);
        if (s.contains("ngày kia") || s.contains("ngày mốt")) return today.plusDays(2);
        if (s.contains("cuối tuần") || s.contains("weekend")) {
            LocalDate friday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusDays(4);
            return friday.isBefore(today) ? friday.plusWeeks(1) : friday;
        }
        if (s.contains("tháng sau") || s.contains("tháng tới") || s.contains("next month"))
            return today.plusMonths(1).withDayOfMonth(1);
        if (s.contains("năm sau") || s.contains("next year"))
            return today.plusYears(1).withDayOfYear(1);

        // "N ngày nữa/sau/tới"
        Matcher m = Pattern.compile("(\\d+)\\s*ngày\\s*(?:nữa|sau|tới)").matcher(s);
        if (m.find()) {
            try { return today.plusDays(Long.parseLong(m.group(1))); } catch (Exception ignored) {}
        }

        // "N tuần nữa/sau" (without weekday)
        m = Pattern.compile("(\\d+)\\s*tuần\\s*(?:nữa|sau|tới)").matcher(s);
        if (m.find() && extractDayOfWeek(s) == null) {
            try { return today.plusWeeks(Long.parseLong(m.group(1))); } catch (Exception ignored) {}
        }

        // "N tháng nữa/sau"
        m = Pattern.compile("(\\d+)\\s*tháng\\s*(?:nữa|sau|tới)").matcher(s);
        if (m.find()) {
            try { return today.plusMonths(Long.parseLong(m.group(1))); } catch (Exception ignored) {}
        }

        return null;
    }
}
