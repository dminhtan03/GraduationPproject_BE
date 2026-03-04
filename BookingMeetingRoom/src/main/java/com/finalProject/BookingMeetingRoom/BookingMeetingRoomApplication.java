package com.finalProject.BookingMeetingRoom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BookingMeetingRoomApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookingMeetingRoomApplication.class, args);
	}

}
