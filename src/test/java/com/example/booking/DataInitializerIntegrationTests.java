package com.example.booking;

import com.example.booking.entity.Role;
import com.example.booking.repository.ResourceRepository;
import com.example.booking.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:seed_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "app.seed.enabled=true",
        "app.seed.admin-username=seed-admin",
        "app.seed.admin-password=Seed-Admin-Password-123!",
        "app.seed.user-username=seed-user",
        "app.seed.user-password=Seed-User-Password-123!"
})
class DataInitializerIntegrationTests {

    @Autowired UserRepository userRepository;
    @Autowired ResourceRepository resourceRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void configuredSeedUsersAreCreatedWithBcryptPasswordsAndResources() {
        var admin = userRepository.findByUsername("seed-admin").orElseThrow();
        var user = userRepository.findByUsername("seed-user").orElseThrow();

        assertEquals(Role.ADMIN, admin.getRole());
        assertEquals(Role.USER, user.getRole());
        assertTrue(passwordEncoder.matches("Seed-Admin-Password-123!", admin.getPassword()));
        assertTrue(passwordEncoder.matches("Seed-User-Password-123!", user.getPassword()));
        assertTrue(resourceRepository.count() >= 2);
    }
}
