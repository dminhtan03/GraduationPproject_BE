package com.finalProject.BookingMeetingRoom.service.aiplatform;

import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class AiIntentService {

    private static final Set<String> SUMMARY_KEYWORDS = Set.of(
            "hom nay", "today", "ngay hom nay", "lich hom nay",
            "hom nay co gi", "hom nay toi co", "hom nay lam gi",
            "viec hom nay", "task hom nay", "meeting hom nay",
            "hop hom nay", "tom tat", "tong quan ngay",
            "toi co nhung gi", "nhung viec hom nay", "cac viec hom nay"
    );

    private static final Set<String> TASK_KEYWORDS = Set.of(
            "task", "viec", "cong viec", "giao", "lam", "deadline",
            "nhiem vu", "tao", "them", "xoa", "cap nhat", "create", "add",
            "delete", "update"
    );

    private static final Set<String> MEETING_KEYWORDS = Set.of(
            "hop", "meeting", "cuoc hop", "bien ban", "transcript", "audio", "ghi am"
    );

    private static final Set<String> OFF_TOPIC_KEYWORDS = Set.of(
            "thu do", "dan so", "dien tich", "ai la tong thong", "bao nhieu hanh tinh",
            "tieu su", "sinh nam bao nhieu", "bai hat", "ca si", "phim hay",
            "dien vien", "thoi tiet", "nhiet do ngoai troi", "nau an", "cong thuc nau",
            "tro choi", "minecraft", "lien minh", "translate to", "ke chuyen", "bai tho",
            "joke"
    );

    public String classify(String text) {
        if (text == null || text.isBlank()) {
            return "unknown";
        }
        String lower = fold(text);

        if (containsAny(lower, SUMMARY_KEYWORDS)) {
            return "summary";
        }
        if (containsAny(lower, MEETING_KEYWORDS)) {
            return "meeting";
        }
        if (containsAny(lower, TASK_KEYWORDS)) {
            return "task";
        }
        if (containsAny(lower, OFF_TOPIC_KEYWORDS) || looksLikeMath(lower)) {
            return "off_topic";
        }
        return "unknown";
    }

    private boolean containsAny(String text, Set<String> keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeMath(String text) {
        return text.matches("^\\s*[\\d\\s\\+\\-\\*\\/x×÷\\(\\)\\.]+[=\\?]\\s*$")
                || text.matches("^\\s*\\d+\\s*[\\+\\-\\*\\/x×÷]\\s*\\d+\\s*$")
                || text.matches(".*bang bao nhieu.*")
                || text.matches(".*bao nhieu la.*");
    }

    private String fold(String text) {
        String lower = text.toLowerCase();
        return java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
