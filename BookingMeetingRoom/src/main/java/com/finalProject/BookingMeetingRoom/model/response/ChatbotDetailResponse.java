package com.finalProject.BookingMeetingRoom.model.response;

import com.finalProject.BookingMeetingRoom.common.enums.SenderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotDetailResponse {
	private String sessionId;
	private Long messageCount;
	private LocalDateTime startedAt;
	private LocalDateTime lastMessageAt;
	private SenderType lastSender;
	private String lastMessage;
	private List<ChatMessageItem> messages;

	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ChatMessageItem {
		private String id;
		private SenderType sender;
		private String message;
		private LocalDateTime createdAt;
	}
}
