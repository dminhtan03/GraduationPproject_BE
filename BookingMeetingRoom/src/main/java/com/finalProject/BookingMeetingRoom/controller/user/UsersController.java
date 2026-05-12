package com.finalProject.BookingMeetingRoom.controller.user;

import com.finalProject.BookingMeetingRoom.common.payload.Response;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GET /api/v1/users — list all users for ai-platform find_users_by_name().
 * Returns id + fullName so ai-platform can match user by name.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UsersController {

    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<?> listUsers(@RequestParam(required = false) String q) {
        var users = userRepository.findAll();
        List<Map<String, Object>> result = users.stream()
                .filter(u -> u.getUserInfo() != null && u.getUserInfo().getEmail() != null)
                .filter(u -> {
                    if (q == null || q.isBlank()) return true;
                    String lower = q.toLowerCase();
                    String fullName = getFullName(u).toLowerCase();
                    String email = (u.getUserInfo().getEmail() != null
                            ? u.getUserInfo().getEmail() : "").toLowerCase();
                    return fullName.contains(lower) || email.contains(lower);
                })
                .map(u -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", u.getId());
                    m.put("fullName", getFullName(u));
                    m.put("email", u.getUserInfo() != null ? u.getUserInfo().getEmail() : null);
                    return m;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(Response.ofSucceeded(result));
    }

    private String getFullName(com.finalProject.BookingMeetingRoom.model.entity.User u) {
        if (u.getUserInfo() == null) return "";
        String first = u.getUserInfo().getFirstName() != null ? u.getUserInfo().getFirstName() : "";
        String last = u.getUserInfo().getLastName() != null ? u.getUserInfo().getLastName() : "";
        return (first + " " + last).trim();
    }
}
