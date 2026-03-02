package com.finalProject.BookingMeetingRoom.repository;

import com.finalProject.BookingMeetingRoom.model.entity.Chatbot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRepository extends JpaRepository<Chatbot, String> {
}
