package com.example.booking.repository;

import com.example.booking.entity.BookableResource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceRepository extends JpaRepository<BookableResource, Long> {}
