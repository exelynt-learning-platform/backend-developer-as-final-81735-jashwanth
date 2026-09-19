package com.example.booking.config;

import com.example.booking.entity.AppUser;
import com.example.booking.entity.BookableResource;
import com.example.booking.entity.Role;
import com.example.booking.repository.ResourceRepository;
import com.example.booking.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

@Configuration
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class DataInitializer {

    @Bean
    CommandLineRunner seedData(
            UserRepository userRepository,
            ResourceRepository resourceRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.seed.admin-username}") String adminUsername,
            @Value("${app.seed.admin-password}") String adminPassword,
            @Value("${app.seed.user-username}") String userUsername,
            @Value("${app.seed.user-password}") String userPassword) {

        return args -> {
            requireSeedCredential("SEED_ADMIN_PASSWORD", adminPassword);
            requireSeedCredential("SEED_USER_PASSWORD", userPassword);

            if (!userRepository.existsByUsername(adminUsername)) {
                userRepository.save(new AppUser(
                        adminUsername,
                        passwordEncoder.encode(adminPassword),
                        Role.ADMIN));
            }

            if (!userRepository.existsByUsername(userUsername)) {
                userRepository.save(new AppUser(
                        userUsername,
                        passwordEncoder.encode(userPassword),
                        Role.USER));
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

    private void requireSeedCredential(String variableName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(variableName + " must be set when seed users are enabled");
        }
    }
}
