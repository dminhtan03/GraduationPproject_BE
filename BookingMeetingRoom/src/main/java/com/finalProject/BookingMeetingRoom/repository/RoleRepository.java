package com.finalProject.BookingMeetingRoom.repository;

import com.finalProject.BookingMeetingRoom.model.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, String> {
    Optional<Role> findRoleByName(String name);
}
