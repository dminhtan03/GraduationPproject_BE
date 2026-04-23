package com.finalProject.BookingMeetingRoom.service;

import java.util.List;

import org.springframework.security.core.Authentication;

import com.finalProject.BookingMeetingRoom.model.request.ReservationSeriesCreateRequest;
import com.finalProject.BookingMeetingRoom.model.response.ReservationSeriesResponse;

// start+ chức năng đặt phòng lặp lại (service)
public interface ReservationSeriesService {
    ReservationSeriesResponse createSeries(ReservationSeriesCreateRequest request, Authentication authentication);
    List<ReservationSeriesResponse> getMySeries(Authentication authentication);
    void cancelSeries(String seriesId, Authentication authentication);
    void syncSeriesNow(String seriesId, Authentication authentication);
}
// end+ chức năng đặt phòng lặp lại (service)
