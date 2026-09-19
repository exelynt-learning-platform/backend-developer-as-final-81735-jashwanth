package com.example.booking.config;

import com.example.booking.entity.AppUser;
import com.example.booking.entity.BookableResource;
import com.example.booking.entity.Role;
import com.example.booking.repository.ResourceRepository;
import com.example.booking.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner seedData(
            UserRepository userRepository,
            ResourceRepository resourceRepository,
            PasswordEncoder passwordEncoder) {

        return args -> {

            if (!userRepository.existsByUsername("admin")) {
                AppUser admin = new AppUser(
                        "admin",
                        passwordEncoder.encode("Admin@123"),
                        Role.ADMIN
                );

                userRepository.save(admin);
            }

            if (!userRepository.existsByUsername("user")) {
                AppUser user = new AppUser(
                        "user",
                        passwordEncoder.encode("User@123"),
                        Role.USER
                );

                userRepository.save(user);
            }

            if (resourceRepository.count() == 0) {

                BookableResource room = new BookableResource();
                room.setName("Conference Room A");
                room.setDescription("Meeting room with projector");
                room.setType("ROOM");
                room.setPricePerHour(new BigDecimal("500.00"));
                room.setAvailable(true);

                resourceRepository.save(room);

                BookableResource vehicle = new BookableResource();
                vehicle.setName("Company Car");
                vehicle.setDescription("Four-seater company vehicle");
                vehicle.setType("VEHICLE");
                vehicle.setPricePerHour(new BigDecimal("800.00"));
                vehicle.setAvailable(true);

                resourceRepository.save(vehicle);
            }
        };
    }
}