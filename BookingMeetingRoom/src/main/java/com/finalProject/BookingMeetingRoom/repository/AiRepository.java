package com.finalProject.BookingMeetingRoom.repository;

import com.finalProject.BookingMeetingRoom.model.entity.Chatbot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiRepository extends JpaRepository<Chatbot, String> {

	Optional<Chatbot> findFirstByActiveTrueOrderByCreatedAtAsc();
}
