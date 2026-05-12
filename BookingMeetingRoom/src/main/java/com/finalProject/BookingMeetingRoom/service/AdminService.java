package com.finalProject.BookingMeetingRoom.service;

import org.springframework.security.core.Authentication;
import java.util.List;

public interface AdminService {
    void forceReturn(List<String> roomId, Authentication connectedUser);
}
