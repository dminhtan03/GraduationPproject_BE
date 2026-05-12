package com.finalProject.BookingMeetingRoom.service;

import com.finalProject.BookingMeetingRoom.model.request.ApproveDraftRequest;
import com.finalProject.BookingMeetingRoom.model.request.MeetingRequest;
import com.finalProject.BookingMeetingRoom.model.response.AssignmentDraftResponse;
import com.finalProject.BookingMeetingRoom.model.response.MeetingResponse;
import com.finalProject.BookingMeetingRoom.model.response.ProcessRecordingResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface MeetingService {
    MeetingResponse createMeeting(MeetingRequest request, Authentication auth);
    List<MeetingResponse> listMeetings(Authentication auth);
    MeetingResponse getMeeting(String meetingId, Authentication auth);
    MeetingResponse uploadRecording(String meetingId, String filePath, Authentication auth);
    List<AssignmentDraftResponse> aiExtractAssignments(String meetingId, String audioPath, Authentication auth);
    List<AssignmentDraftResponse> listDrafts(String meetingId, Authentication auth);
    AssignmentDraftResponse updateDraft(String draftId, ApproveDraftRequest request, Authentication auth);
    Map<String, String> approveDraft(String draftId, Authentication auth);
    ProcessRecordingResponse processRecording(MultipartFile audioFile, String reservationId, String title, Authentication auth);
}
