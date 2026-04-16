package com.finalProject.BookingMeetingRoom.service.amenity;

import com.finalProject.BookingMeetingRoom.common.filter.JwtAuthFilter;
import com.finalProject.BookingMeetingRoom.controller.amenity.AmenityController;
import com.finalProject.BookingMeetingRoom.model.entity.Amenity;
import com.finalProject.BookingMeetingRoom.repository.AmenityRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AmenityController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class)
)
class AmenityControllerWebMvcTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    AmenityRepository amenityRepository;

    @Test
    void createAmenityShouldReturn200_whenNew() throws Exception {
        when(amenityRepository.existsByName("Projector")).thenReturn(false);

        mockMvc.perform(post("/api/v1/amenities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Projector\"}"))
                .andExpect(status().isOk());

        verify(amenityRepository).save(any(Amenity.class));
    }

    @Test
    void createAmenityShouldReturn409_whenAlreadyExists() throws Exception {
        when(amenityRepository.existsByName("Projector")).thenReturn(true);

        mockMvc.perform(post("/api/v1/amenities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Projector\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void createAmenityShouldReturn400_whenNameBlank() throws Exception {
        mockMvc.perform(post("/api/v1/amenities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAmenityShouldReturn400_whenNameWhitespace() throws Exception {
        mockMvc.perform(post("/api/v1/amenities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAmenityShouldReturn200_whenDifferentName() throws Exception {
        when(amenityRepository.existsByName("Máy chiếu 4K 🚀")).thenReturn(false);

        mockMvc.perform(post("/api/v1/amenities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Máy chiếu 4K 🚀\"}"))
                .andExpect(status().isOk());

        verify(amenityRepository).save(any(Amenity.class));
    }

    @Test
    void updateAmenityShouldReturn200_whenValid() throws Exception {
        Amenity amenity = new Amenity();
        amenity.setId("A1");
        amenity.setName("Bảng trắng");
        when(amenityRepository.findById("A1")).thenReturn(Optional.of(amenity));
        when(amenityRepository.existsByName("Máy chiếu 4K 🚀")).thenReturn(false);

        mockMvc.perform(put("/api/v1/amenities/A1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Máy chiếu 4K 🚀\"}"))
                .andExpect(status().isOk());

        verify(amenityRepository).save(any(Amenity.class));
    }

    @Test
    void updateAmenityShouldReturn409_whenNewNameAlreadyExists() throws Exception {
        Amenity amenity = new Amenity();
        amenity.setId("A1");
        amenity.setName("Old");
        when(amenityRepository.findById("A1")).thenReturn(Optional.of(amenity));
        when(amenityRepository.existsByName("Projector")).thenReturn(true);

        mockMvc.perform(put("/api/v1/amenities/A1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Projector\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void updateAmenityShouldReturn404_whenNotFound() throws Exception {
        when(amenityRepository.findById("A404")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/v1/amenities/A404")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateAmenityShouldReturn400_whenNameBlank() throws Exception {
        Amenity amenity = new Amenity();
        amenity.setId("A1");
        amenity.setName("Old");
        when(amenityRepository.findById("A1")).thenReturn(Optional.of(amenity));

        mockMvc.perform(put("/api/v1/amenities/A1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateAmenityShouldReturn200_whenSameNameIgnoreCaseEvenIfExistsByNameTrue() throws Exception {
        Amenity amenity = new Amenity();
        amenity.setId("A1");
        amenity.setName("Projector");
        when(amenityRepository.findById("A1")).thenReturn(Optional.of(amenity));
        when(amenityRepository.existsByName("projector")).thenReturn(true);

        mockMvc.perform(put("/api/v1/amenities/A1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"projector\"}"))
                .andExpect(status().isOk());

        verify(amenityRepository).save(any(Amenity.class));
    }

    @Test
    void deleteAmenityShouldReturn200_whenExists() throws Exception {
        when(amenityRepository.existsById("A1")).thenReturn(true);

        mockMvc.perform(delete("/api/v1/amenities/A1"))
                .andExpect(status().isOk());

        verify(amenityRepository).deleteById("A1");
    }

    @Test
    void deleteAmenityShouldReturn200_whenDifferentIdExists() throws Exception {
        when(amenityRepository.existsById("A2")).thenReturn(true);

        mockMvc.perform(delete("/api/v1/amenities/A2"))
                .andExpect(status().isOk());

        verify(amenityRepository).deleteById("A2");
    }

    @Test
    void deleteAmenityShouldReturn404_whenNotFound() throws Exception {
        when(amenityRepository.existsById("A404")).thenReturn(false);

        mockMvc.perform(delete("/api/v1/amenities/A404"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteAmenityShouldNotCallDelete_whenNotFound() throws Exception {
        when(amenityRepository.existsById("A404")).thenReturn(false);

        mockMvc.perform(delete("/api/v1/amenities/A404"))
                .andExpect(status().isNotFound());

        verify(amenityRepository, never()).deleteById(any());
    }

    @Test
    void deleteAmenityShouldReturn500_whenDeleteFails() throws Exception {
        when(amenityRepository.existsById("A1")).thenReturn(true);
        doThrow(new RuntimeException("in use")).when(amenityRepository).deleteById("A1");

        mockMvc.perform(delete("/api/v1/amenities/A1"))
                .andExpect(status().isInternalServerError());
    }
}
