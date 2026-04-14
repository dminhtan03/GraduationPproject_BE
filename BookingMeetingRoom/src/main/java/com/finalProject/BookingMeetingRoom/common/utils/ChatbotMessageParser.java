package com.finalProject.BookingMeetingRoom.common.utils;

import com.finalProject.BookingMeetingRoom.common.enums.ChatbotIntent;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.text.Normalizer;
import java.util.Set;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatbotMessageParser {

    // Supports room codes like: AL-102, A-203, V5-020 (prefix may include digits).
    // Require a real separator (- or _) to avoid false positives like "of 20" -> "OF-20".
    private static final Pattern ROOM_CODE_PATTERN = Pattern.compile("(?i)\\b([a-z]{1,5}\\d{0,3})\\s*[-_]\\s*(\\d{1,4})\\b");
    private static final Pattern ROOM_CODE_WITH_KEYWORD_PATTERN = Pattern.compile("(?i)(?:room|phòng|phong)\\s*([a-z]{1,5}\\d{0,3})\\s*(\\d{1,4})\\b");

        private static final Set<String> ROOM_PREFIX_STOPWORDS = Set.of(
            "of", "in", "on", "at", "to", "from", "for", "with", "as"
    );

        private static final Pattern CAPACITY_KEYWORD_NUMBER_PATTERN = Pattern.compile(
            "(?i)(?:capacity|accommodate|accomodate|seats|seat|sức chứa|suc chua)\\D{0,15}(\\d{1,3})\\b"
        );

        private static final Pattern CAPACITY_NUMBER_SUFFIX_PATTERN = Pattern.compile(
            "(?i)\\b(\\d{1,3})\\s*(?:\\+|or more|plus)?\\s*(?:people|persons|seats|người|nguoi)\\b"
        );

        private static final Pattern CAPACITY_RANGE_SUFFIX_PATTERN = Pattern.compile(
            "(?i)\\b(\\d{1,3})\\s*[-–]\\s*(\\d{1,3})\\s*(?:people|persons|seats|người|nguoi)\\b"
        );

        private static final Pattern CAPACITY_APPROX_PATTERN = Pattern.compile(
            "(?i)\\b(?:khoảng|khoang|tầm|tam|about|around)\\s*(\\d{1,3})\\s*(?:người|nguoi|people|persons)?\\b"
        );

    private static final Pattern RANGE_TIME_PATTERN = Pattern.compile(
            "(?i)(?:from|từ|tu)\\s*(\\d{1,2})(?:[:h](\\d{2}))?\\s*(am|pm|sáng|sang|chieu|chiều|toi|tối)?\\s*(?:to|đến|den|tới|toi|-)\\s*(\\d{1,2})(?:[:h](\\d{2}))?\\s*(am|pm|sáng|sang|chieu|chiều|toi|tối)?");

        // Range expressed as: "at 6PM to 8PM" / "lúc 18h đến 20h"
    private static final Pattern AT_RANGE_TIME_PATTERN = Pattern.compile(
            "(?i)(?:at|lúc|luc|vào|vao)\\s*(\\d{1,2})(?:[:h](\\d{2}))?\\s*(am|pm|sáng|sang|chieu|chiều|toi|tối)?\\s*(?:to|đến|den|tới|toi|-)\\s*(\\d{1,2})(?:[:h](\\d{2}))?\\s*(am|pm|sáng|sang|chieu|chiều|toi|tối)?");

    private static final Pattern SINGLE_TIME_PATTERN = Pattern.compile(
            "(?i)(?:at|lúc|luc|as of|after|from|sau|vào|vao)\\s*(\\d{1,2})(?:[:h](\\d{2}))?\\s*(am|pm|sáng|sang|chieu|chiều|toi|tối)?");

        private static final Pattern BARE_TIME_PATTERN = Pattern.compile("(?i)\\b(\\d{1,2})(?:[:h](\\d{2}))?\\s*(am|pm|sáng|sang|chieu|chiều|toi|tối)?\\b");
        private static final Pattern DURATION_HOURS_PATTERN = Pattern.compile("(?i)(?:for|trong|khoảng|khoang)?\\s*(\\d{1,2})\\s*(?:tiếng|tieng|gio|giờ|hours?|hrs?)\\b");
        private static final Pattern DURATION_MINUTES_PATTERN = Pattern.compile("(?i)(?:for|trong|khoảng|khoang)?\\s*(\\d{1,3})\\s*(?:phút|phut|minutes?|mins?)\\b");

    public record ParseResult(
            ChatbotIntent intent,
            String normalizedMessage,
            String roomCode,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            boolean endTimeDefaulted,
            Integer minCapacity
    ) {
        public Optional<LocalDateTime> startDateTime() {
            return (date != null && startTime != null)
                    ? Optional.of(LocalDateTime.of(date, startTime))
                    : Optional.empty();
        }

        public Optional<LocalDateTime> endDateTime() {
            return (date != null && endTime != null)
                    ? Optional.of(LocalDateTime.of(date, endTime))
                    : Optional.empty();
        }
    }

    public ParseResult parse(String message) {
        String normalized = normalize(message);
        String folded = foldText(normalized);

        LocalDate date = extractDate(normalized, folded);
        String roomCode = extractRoomCode(normalized);
        TimeExtraction timeExtraction = extractTimes(normalized);

        Integer minCapacity = extractMinCapacity(normalized);
        ChatbotIntent intent = detectIntent(normalized, folded, roomCode, date, timeExtraction, minCapacity);

        return new ParseResult(
                intent,
                normalized,
                roomCode,
                date,
                timeExtraction.start,
                timeExtraction.end,
                timeExtraction.endDefaulted,
                minCapacity
        );
    }

    private ChatbotIntent detectIntent(
            String normalized,
            String folded,
            String roomCode,
            LocalDate date,
            TimeExtraction timeExtraction,
            Integer minCapacity
    ) {
        boolean hasRoom = roomCode != null;
        boolean hasTime = timeExtraction != null && (timeExtraction.start != null || timeExtraction.end != null);
        boolean hasDate = date != null;

        boolean hasAvailabilityHint = containsAnyEither(normalized, folded,
                "today available rooms",
                "available rooms today",
                "rooms available today",
                "rooms are available",
                "available as of",
                "hôm nay còn phòng",
                "hom nay con phong",
                "phòng trống hôm nay",
                "phong trong hom nay",
                "còn phòng trống",
                "con phong trong",
                "phòng nào trống",
                "phong nao trong",
                "còn phòng không",
                "con phong khong",
                "room available",
                "any room available",
                "room free",
                "con trong",
                "rảnh",
                "ranh",
                "trống") ;

        if (hasAvailabilityHint && minCapacity == null) {
            return ChatbotIntent.CHECK_AVAILABLE_ROOMS_TODAY;
        }

        boolean hasSuggestHint = containsAnyEither(normalized, folded,
                "suggest",
                "recommend",
                "gợi ý",
                "goi y",
                "đề xuất",
                "de xuat");

        if (minCapacity != null && hasSuggestHint) {
            return ChatbotIntent.SUGGEST_ROOMS_BY_CAPACITY;
        }

        boolean hasBookingHint = containsAnyEither(normalized, folded,
                "book",
                "reserve",
                "đặt",
                "dat ",
                "mượn",
                "muon",
                "đặt giúp",
                "dat giup",
                "giữ",
                "giu ",
                "schedule",
                "arrange",
                "chốt",
                "chot");

        if (hasBookingHint) {
            return ChatbotIntent.BOOK_ROOM;
        }

        if ((RANGE_TIME_PATTERN.matcher(normalized).find() || AT_RANGE_TIME_PATTERN.matcher(normalized).find() || SINGLE_TIME_PATTERN.matcher(normalized).find())
                && (hasRoom || minCapacity != null || containsAnyEither(normalized, folded, "phòng", "phong", "room"))) {
            return ChatbotIntent.BOOK_ROOM;
        }

        if (hasTime && minCapacity != null && containsAnyEither(normalized, folded, "phòng", "phong", "room")) {
            return ChatbotIntent.BOOK_ROOM;
        }

        if (hasAvailabilityHint && minCapacity != null) {
            return ChatbotIntent.SUGGEST_ROOMS_BY_CAPACITY;
        }

        if (hasRoom && hasTime) {
            return ChatbotIntent.BOOK_ROOM;
        }

        // Heuristic: if a room code is present and a time is present, it is likely a booking request.
        if (ROOM_CODE_PATTERN.matcher(normalized).find() && (RANGE_TIME_PATTERN.matcher(normalized).find() || SINGLE_TIME_PATTERN.matcher(normalized).find())) {
            return ChatbotIntent.BOOK_ROOM;
        }

        return ChatbotIntent.FALLBACK;
    }

    private Integer extractMinCapacity(String normalized) {
        if (normalized == null || normalized.isBlank()) return null;

        Matcher m2 = CAPACITY_NUMBER_SUFFIX_PATTERN.matcher(normalized);
        if (m2.find()) {
            return safeParseInt(m2.group(1));
        }

        Matcher mRange = CAPACITY_RANGE_SUFFIX_PATTERN.matcher(normalized);
        if (mRange.find()) {
            Integer a = safeParseInt(mRange.group(1));
            Integer b = safeParseInt(mRange.group(2));
            if (a == null) return b;
            if (b == null) return a;
            return Math.max(a, b);
        }

        Matcher m3 = CAPACITY_APPROX_PATTERN.matcher(normalized);
        if (m3.find()) {
            return safeParseInt(m3.group(1));
        }

        Matcher m1 = CAPACITY_KEYWORD_NUMBER_PATTERN.matcher(normalized);
        if (m1.find()) {
            return safeParseInt(m1.group(1));
        }

        return null;
    }

    private Integer safeParseInt(String s) {
        if (s == null) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate extractDate(String normalized, String folded) {
        if (containsAnyEither(normalized, folded, "today", "hôm nay", "hom nay")) {
            return LocalDate.now();
        }

        if (containsAnyEither(normalized, folded,
                "tomorrow",
                "tomorow", // common misspelling
                "tmr",
                "ngày mai",
                "ngay mai")) {
            return LocalDate.now().plusDays(1);
        }

        // Colloquial Vietnamese shorthand: "mai", "sang mai", "chieu mai", "toi mai".
        if (normalized.matches(".*\\bmai\\b.*")
                || normalized.matches(".*\\b(?:sáng|sang|chiều|chieu|tối|toi)\\s+mai\\b.*")) {
            return LocalDate.now().plusDays(1);
        }

        if (containsAnyEither(normalized, folded, "day after tomorrow", "ngày kia", "ngay kia")) {
            return LocalDate.now().plusDays(2);
        }

        // Minimal support for ISO date in the message: yyyy-mm-dd
        Pattern iso = Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b");
        Matcher m = iso.matcher(normalized);
        if (m.find()) {
            try {
                int y = Integer.parseInt(m.group(1));
                int mo = Integer.parseInt(m.group(2));
                int d = Integer.parseInt(m.group(3));
                return LocalDate.of(y, mo, d);
            } catch (DateTimeException ignored) {
                // keep trying other date formats
            }
        }

        Pattern dmy = Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})\\b");
        Matcher dmyMatcher = dmy.matcher(normalized);
        if (dmyMatcher.find()) {
            try {
                int d = Integer.parseInt(dmyMatcher.group(1));
                int mo = Integer.parseInt(dmyMatcher.group(2));
                int y = Integer.parseInt(dmyMatcher.group(3));
                return LocalDate.of(y, mo, d);
            } catch (DateTimeException ignored) {
                return null;
            }
        }

        return null;
    }

    private String extractRoomCode(String normalized) {
        Matcher m = ROOM_CODE_PATTERN.matcher(normalized);
        while (m.find()) {
            String lettersRaw = m.group(1);
            String lettersLower = lettersRaw.toLowerCase(Locale.ROOT);
            if (ROOM_PREFIX_STOPWORDS.contains(lettersLower)) {
                continue;
            }

            String letters = lettersRaw.toUpperCase(Locale.ROOT);
            String digits = m.group(2);
            return letters + "-" + digits;
        }

        Matcher byKeyword = ROOM_CODE_WITH_KEYWORD_PATTERN.matcher(normalized);
        if (byKeyword.find()) {
            String letters = byKeyword.group(1).toUpperCase(Locale.ROOT);
            String digits = byKeyword.group(2);
            return letters + "-" + digits;
        }

        return null;
    }

    private record TimeExtraction(LocalTime start, LocalTime end, boolean endDefaulted) {}

    private TimeExtraction extractTimes(String normalized) {
        Matcher atRange = AT_RANGE_TIME_PATTERN.matcher(normalized);
        if (atRange.find()) {
            String startToken = atRange.group(3);
            String endToken = atRange.group(6) != null ? atRange.group(6) : startToken;
            LocalTime start = toTime(atRange.group(1), atRange.group(2), startToken);
            LocalTime end = toTime(atRange.group(4), atRange.group(5), endToken);
            return new TimeExtraction(start, end, false);
        }

        Matcher range = RANGE_TIME_PATTERN.matcher(normalized);
        if (range.find()) {
            String startToken = range.group(3);
            String endToken = range.group(6) != null ? range.group(6) : startToken;
            LocalTime start = toTime(range.group(1), range.group(2), startToken);
            LocalTime end = toTime(range.group(4), range.group(5), endToken);
            return new TimeExtraction(start, end, false);
        }

        Matcher single = SINGLE_TIME_PATTERN.matcher(normalized);
        if (single.find()) {
            LocalTime start = toTime(single.group(1), single.group(2), single.group(3));
            LocalTime derivedEnd = applyDurationIfAny(start, normalized);
            if (derivedEnd != null) {
                return new TimeExtraction(start, derivedEnd, true);
            }
            return new TimeExtraction(start, start != null ? start.plusHours(1) : null, true);
        }

        // Fallback: if the message contains a room code and any bare time, treat the first time as start time.
        if (ROOM_CODE_PATTERN.matcher(normalized).find()) {
            Matcher bare = BARE_TIME_PATTERN.matcher(normalized);
            if (bare.find()) {
                LocalTime start = toTime(bare.group(1), bare.group(2), bare.group(3));
                LocalTime derivedEnd = applyDurationIfAny(start, normalized);
                if (derivedEnd != null) {
                    return new TimeExtraction(start, derivedEnd, true);
                }
                return new TimeExtraction(start, start != null ? start.plusHours(1) : null, true);
            }
        }

        return new TimeExtraction(null, null, false);
    }

    private LocalTime toTime(String hourStr, String minuteStr, String meridiemToken) {
        if (hourStr == null) return null;

        int hour = Integer.parseInt(hourStr);
        int minute = minuteStr != null ? Integer.parseInt(minuteStr) : 0;

        String token = meridiemToken != null ? meridiemToken.toLowerCase(Locale.ROOT) : "";

        boolean isPm = token.contains("pm") || token.contains("chiều") || token.contains("chieu") || token.contains("tối") || token.contains("toi");
        boolean isAm = token.contains("am") || token.contains("sáng") || token.contains("sang");

        if (isPm && hour < 12) hour += 12;
        if (isAm && hour == 12) hour = 0;

        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return null;
        }

        return LocalTime.of(hour, minute);
    }

    private LocalTime applyDurationIfAny(LocalTime start, String normalized) {
        if (start == null || normalized == null || normalized.isBlank()) return null;

        Matcher h = DURATION_HOURS_PATTERN.matcher(normalized);
        if (h.find()) {
            Integer hour = safeParseInt(h.group(1));
            if (hour != null && hour > 0 && hour <= 12) {
                return start.plusHours(hour);
            }
        }

        Matcher m = DURATION_MINUTES_PATTERN.matcher(normalized);
        if (m.find()) {
            Integer minute = safeParseInt(m.group(1));
            if (minute != null && minute > 0 && minute <= 720) {
                return start.plusMinutes(minute);
            }
        }

        return null;
    }

    private String normalize(String message) {
        String s = message == null ? "" : message.trim();
        // Convert colloquial Vietnamese time forms to parser-friendly tokens.
        s = s.replaceAll("(?i)\\b(\\d{1,2})h(\\d{2})\\b", "$1:$2");
        s = s.replaceAll("(?i)\\b(\\d{1,2})h\\b", "$1:00");
        s = s.replaceAll("(?i)\\b(\\d{1,2})\\s*gi(?:o|ờ)\\s*(\\d{1,2})\\b", "$1:$2");
        s = s.replaceAll("(?i)\\b(\\d{1,2})\\s*gi(?:o|ờ)\\b", "$1h");

        // Normalize Vietnamese diacritics variants minimally for matching: keep original too.
        // Also normalize multiple spaces.
        return s.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String foldText(String text) {
        if (text == null) return "";
        String folded = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        return folded.replaceAll("\\s+", " ").trim();
    }

    private boolean containsAnyEither(String normalized, String folded, String... needles) {
        for (String n : needles) {
            if (n == null || n.isBlank()) continue;
            String needleNorm = n.toLowerCase(Locale.ROOT);
            String needleFold = foldText(n);
            if ((normalized != null && normalized.contains(needleNorm))
                    || (folded != null && folded.contains(needleFold))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String normalized, String... needles) {
        for (String n : needles) {
            if (normalized.contains(n)) return true;
        }
        return false;
    }
}
