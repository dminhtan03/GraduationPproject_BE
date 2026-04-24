package com.finalProject.BookingMeetingRoom.service.room;

import com.finalProject.BookingMeetingRoom.service.impl.RoomImageServiceImpl;
import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.entity.RoomImage;
import com.finalProject.BookingMeetingRoom.model.response.RoomImageResponse;
import com.finalProject.BookingMeetingRoom.repository.RoomImageRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomImageServiceImplTest {

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    @Mock
    private RoomImageRepository roomImageRepository;

    @Mock
    private RoomRepository roomRepository;

    @InjectMocks
    private RoomImageServiceImpl roomImageService;

    @BeforeEach
    void setUp() {
        lenient().when(cloudinary.uploader()).thenReturn(uploader);
    }

    @Test
    void uploadImage_shouldThrow_whenFileIsEmpty() {
        MultipartFile file = new MockMultipartFile("file", new byte[0]);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> roomImageService.uploadImage("room-1", file));
        assertEquals("File is empty", ex.getMessage());
    }

    @Test
    void uploadImage_shouldThrow_whenContentTypeInvalid() {
        MultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> roomImageService.uploadImage("room-1", file));
        assertEquals("Only JPG, JPEG, PNG, WEBP allowed", ex.getMessage());
    }

    @Test
    void uploadImage_shouldThrow_whenRoomNotFound() {
        MultipartFile file = new MockMultipartFile("file", "a.png", "image/png", "img".getBytes());
        when(roomRepository.findById("room-1")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> roomImageService.uploadImage("room-1", file));
        assertEquals("Room not found: room-1", ex.getMessage());
    }

    @Test
    void uploadImage_shouldSaveAndReturnResponse_whenSuccess() throws Exception {
        Room room = new Room();
        room.setId("room-1");
        MultipartFile file = new MockMultipartFile("file", "a.png", "image/png", "img".getBytes());

        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        when(uploader.upload(any(byte[].class), any(Map.class))).thenReturn(Map.of(
                "secure_url", "https://img.example/1.png",
                "public_id", "meeting-room/room-1/img-1"
        ));

        RoomImageResponse response = roomImageService.uploadImage("room-1", file);

        assertNotNull(response.getId());
        assertEquals("room-1", response.getRoomId());
        assertEquals("https://img.example/1.png", response.getImageUrl());
        assertEquals("meeting-room/room-1/img-1", response.getPublicId());

        verify(roomImageRepository).save(any(RoomImage.class));
    }

    @Test
    void uploadImage_shouldThrow_whenCloudinaryUploadFails() throws Exception {
        Room room = new Room();
        room.setId("room-2");
        MultipartFile file = new MockMultipartFile("file", "a.png", "image/png", "img".getBytes());

        when(roomRepository.findById("room-2")).thenReturn(Optional.of(room));
        when(uploader.upload(any(byte[].class), any(Map.class))).thenThrow(new IOException("io"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> roomImageService.uploadImage("room-2", file));
        assertEquals("Upload image to Cloudinary failed", ex.getMessage());
    }

    @Test
    void replaceImage_shouldThrow_whenImageNotFound() {
        MultipartFile file = new MockMultipartFile("file", "a.png", "image/png", "img".getBytes());
        when(roomImageRepository.findById("img-1")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> roomImageService.replaceImage("img-1", file));
        assertEquals("RoomImage not found: img-1", ex.getMessage());
    }

    @Test
    void replaceImage_shouldDestroyOldAndUploadNew_whenOldPublicIdExists() throws Exception {
        Room room = new Room();
        room.setId("room-3");

        RoomImage image = new RoomImage();
        image.setId("img-3");
        image.setPublicId("old-public-id");
        image.setRoom(room);

        MultipartFile file = new MockMultipartFile("file", "b.png", "image/png", "img2".getBytes());

        when(roomImageRepository.findById("img-3")).thenReturn(Optional.of(image));
        when(uploader.upload(any(byte[].class), any(Map.class))).thenReturn(Map.of(
                "secure_url", "https://img.example/new.png",
                "public_id", "meeting-room/room-3/new"
        ));

        RoomImageResponse response = roomImageService.replaceImage("img-3", file);

        verify(uploader).destroy(any(String.class), any(Map.class));
        verify(roomImageRepository).save(image);

        assertEquals("https://img.example/new.png", response.getImageUrl());
        assertEquals("meeting-room/room-3/new", response.getPublicId());
    }

    @Test
    void replaceImage_shouldSkipDestroy_whenOldPublicIdBlank() throws Exception {
        Room room = new Room();
        room.setId("room-4");

        RoomImage image = new RoomImage();
        image.setId("img-4");
        image.setPublicId(" ");
        image.setRoom(room);

        MultipartFile file = new MockMultipartFile("file", "c.png", "image/png", "img3".getBytes());

        when(roomImageRepository.findById("img-4")).thenReturn(Optional.of(image));
        when(uploader.upload(any(byte[].class), any(Map.class))).thenReturn(Map.of(
                "secure_url", "https://img.example/4.png",
                "public_id", "meeting-room/room-4/new"
        ));

        roomImageService.replaceImage("img-4", file);

        verify(uploader, never()).destroy(any(String.class), any(Map.class));
        verify(roomImageRepository).save(image);
    }

    @Test
    void replaceImage_shouldThrow_whenUploadFails() throws Exception {
        Room room = new Room();
        room.setId("room-5");

        RoomImage image = new RoomImage();
        image.setId("img-5");
        image.setPublicId("old");
        image.setRoom(room);

        MultipartFile file = new MockMultipartFile("file", "c.png", "image/png", "img3".getBytes());

        when(roomImageRepository.findById("img-5")).thenReturn(Optional.of(image));
        when(uploader.upload(any(byte[].class), any(Map.class))).thenThrow(new IOException("io"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> roomImageService.replaceImage("img-5", file));
        assertEquals("Replace image failed", ex.getMessage());
    }

    @Test
    void deleteImage_shouldThrow_whenImageNotFound() {
        when(roomImageRepository.findById("img-6")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> roomImageService.deleteImage("img-6"));
        assertEquals("RoomImage not found: img-6", ex.getMessage());
    }

    @Test
    void deleteImage_shouldDestroyAndDelete_whenPublicIdPresent() throws Exception {
        RoomImage image = new RoomImage();
        image.setId("img-7");
        image.setPublicId("public-7");

        when(roomImageRepository.findById("img-7")).thenReturn(Optional.of(image));

        roomImageService.deleteImage("img-7");

        verify(uploader).destroy(any(String.class), any(Map.class));
        verify(roomImageRepository).delete(image);
    }

    @Test
    void deleteImage_shouldDeleteOnly_whenPublicIdBlank() throws Exception {
        RoomImage image = new RoomImage();
        image.setId("img-8");
        image.setPublicId(" ");

        when(roomImageRepository.findById("img-8")).thenReturn(Optional.of(image));

        roomImageService.deleteImage("img-8");

        verify(uploader, never()).destroy(any(String.class), any(Map.class));
        verify(roomImageRepository).delete(image);
    }

    @Test
    void deleteImage_shouldThrow_whenDestroyFails() throws Exception {
        RoomImage image = new RoomImage();
        image.setId("img-9");
        image.setPublicId("public-9");

        when(roomImageRepository.findById("img-9")).thenReturn(Optional.of(image));
        doThrow(new IOException("io")).when(uploader).destroy(any(String.class), any(Map.class));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> roomImageService.deleteImage("img-9"));
        assertEquals("Delete image on Cloudinary failed", ex.getMessage());
        verify(roomImageRepository, never()).delete(any(RoomImage.class));
    }

    @Test
    void getImagesByRoom_shouldMapEntityToResponse() {
        Room room = new Room();
        room.setId("room-10");

        RoomImage image = new RoomImage();
        image.setId("img-10");
        image.setImageUrl("https://img.example/10.png");
        image.setPublicId("p10");
        image.setRoom(room);

        when(roomImageRepository.findByRoomId("room-10")).thenReturn(List.of(image));

        List<RoomImageResponse> responses = roomImageService.getImagesByRoom("room-10");

        assertEquals(1, responses.size());
        assertEquals("img-10", responses.get(0).getId());
        assertEquals("room-10", responses.get(0).getRoomId());
    }
}


