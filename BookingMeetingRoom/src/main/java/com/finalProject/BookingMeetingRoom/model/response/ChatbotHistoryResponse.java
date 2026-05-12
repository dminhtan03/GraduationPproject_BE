package com.finalProject.BookingMeetingRoom.model.response;

import com.finalProject.BookingMeetingRoom.common.enums.SenderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotHistoryResponse {
	private String sessionId;
	private Long messageCount;
	private LocalDateTime startedAt;
	private LocalDateTime lastMessageAt;
	private SenderType lastSender;
	private String lastMessage;
}
