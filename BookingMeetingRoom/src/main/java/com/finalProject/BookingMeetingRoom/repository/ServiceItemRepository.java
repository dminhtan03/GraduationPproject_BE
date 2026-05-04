package com.finalProject.BookingMeetingRoom.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.finalProject.BookingMeetingRoom.model.entity.ServiceItem;

@Repository
// start+ chức năng CRUD dịch vụ đi kèm (ServiceItem repository)
public interface ServiceItemRepository extends JpaRepository<ServiceItem, String> {
    boolean existsByNameIgnoreCase(String name);
}
// end+ chức năng CRUD dịch vụ đi kèm (ServiceItem repository)
