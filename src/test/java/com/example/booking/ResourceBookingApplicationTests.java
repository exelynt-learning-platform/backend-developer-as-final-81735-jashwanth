package com.example.booking;

import com.example.booking.entity.AppUser;
import com.example.booking.entity.BookableResource;
import com.example.booking.entity.Reservation;
import com.example.booking.entity.ReservationStatus;
import com.example.booking.entity.Role;
import com.example.booking.repository.ReservationRepository;
import com.example.booking.repository.ResourceRepository;
import com.example.booking.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ResourceBookingApplicationTests {

    private static final String ADMIN_PASSWORD = "Admin-Test-Password-123!";
    private static final String USER_PASSWORD = "User-Test-Password-123!";
    private static final String OTHER_PASSWORD = "Other-Test-Password-123!";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired ResourceRepository resourceRepository;
    @Autowired ReservationRepository reservationRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private BookableResource resource;

    @BeforeEach
    void resetData() {
        reservationRepository.deleteAll();
        resourceRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(new AppUser("admin", passwordEncoder.encode(ADMIN_PASSWORD), Role.ADMIN));
        userRepository.save(new AppUser("user", passwordEncoder.encode(USER_PASSWORD), Role.USER));
        userRepository.save(new AppUser("other", passwordEncoder.encode(OTHER_PASSWORD), Role.USER));

        resource = createResource("Conference Room A", new BigDecimal("500.00"));
    }

    @Test
    void contextLoadsAndPasswordsAreStoredWithBcrypt() {
        AppUser admin = userRepository.findByUsername("admin").orElseThrow();
        assertTrue(passwordEncoder.matches(ADMIN_PASSWORD, admin.getPassword()));
        assertTrue(admin.getPassword().startsWith("$2"));
    }

    @Test
    void validAdminAndUserLoginReturnJwt() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin", ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(blankOrNullString())))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("user", USER_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(blankOrNullString())))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void badLoginAndInvalidLoginBodyReturnAppropriateErrors() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("user", "wrong-password")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", notNullValue()));
    }

    @Test
    void malformedLoginJsonReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void protectedEndpointsRejectMissingInvalidAndEmptyJwt() throws Exception {
        mockMvc.perform(get("/resources"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/resources")
                        .header("Authorization", "Bearer definitely.invalid.token"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/resources")
                        .header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCanReadResourcesButCannotWriteThem() throws Exception {
        String token = login("user", USER_PASSWORD);

        mockMvc.perform(get("/resources").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        mockMvc.perform(get("/resources/{id}", resource.getId()).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Conference Room A"));

        mockMvc.perform(post("/resources")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resourceJson("Equipment", "PROJECTOR", "250.00", true)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/resources/{id}", resource.getId())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resourceJson("Updated", "ROOM", "300.00", true)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/resources/{id}", resource.getId())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminHasFullResourceCrud() throws Exception {
        String token = login("admin", ADMIN_PASSWORD);

        String body = mockMvc.perform(post("/resources")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resourceJson("Projector", "EQUIPMENT", "200.00", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Projector"))
                .andReturn().getResponse().getContentAsString();

        long id = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(get("/resources/{id}", id).header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/resources/{id}", id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resourceJson("Projector Pro", "EQUIPMENT", "300.00", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Projector Pro"))
                .andExpect(jsonPath("$.available").value(false));

        mockMvc.perform(delete("/resources/{id}", id).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/resources/{id}", id).header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidResourceDataAndMissingResourceReturnCorrectErrors() throws Exception {
        String token = login("admin", ADMIN_PASSWORD);

        mockMvc.perform(post("/resources")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"description\":\"x\",\"type\":\"ROOM\",\"pricePerHour\":-1,\"available\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", notNullValue()));

        mockMvc.perform(get("/resources/{id}", 999999L).header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/resources/{id}", 999999L).header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void userCreatesReservationWithJwtIdentityAndDecimalPrice() throws Exception {
        String token = login("user", USER_PASSWORD);
        LocalDateTime start = future(2);
        LocalDateTime end = start.plusMinutes(90);

        mockMvc.perform(post("/reservations")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationCreateJson(resource.getId(), start, end)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("user"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.price").value(750.00));
    }

    @Test
    void userCanViewOnlyOwnReservations() throws Exception {
        String userToken = login("user", USER_PASSWORD);
        String otherToken = login("other", OTHER_PASSWORD);

        LocalDateTime firstStart = future(2);
        LocalDateTime secondStart = future(5);
        long userReservation = createReservation(userToken, resource.getId(), firstStart, firstStart.plusHours(1));
        long otherReservation = createReservation(otherToken, resource.getId(), secondStart, secondStart.plusHours(1));

        mockMvc.perform(get("/reservations").header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(userReservation));

        mockMvc.perform(get("/reservations/{id}", otherReservation).header("Authorization", bearer(userToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void userCanUpdateOwnReservation() throws Exception {
        String token = login("user", USER_PASSWORD);
        LocalDateTime start = future(2);
        long reservationId = createReservation(token, resource.getId(), start, start.plusHours(1));

        LocalDateTime newStart = future(6);
        mockMvc.perform(put("/reservations/{id}", reservationId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationUpdateJson(resource.getId(), newStart, newStart.plusHours(2), "CONFIRMED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("user"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.price").value(1000.00));
    }

    @Test
    void userCannotUpdateAnotherUsersReservationOrDeleteAnyReservation() throws Exception {
        String userToken = login("user", USER_PASSWORD);
        String otherToken = login("other", OTHER_PASSWORD);
        LocalDateTime start = future(2);
        long otherReservation = createReservation(otherToken, resource.getId(), start, start.plusHours(1));

        LocalDateTime updateStart = future(6);
        mockMvc.perform(put("/reservations/{id}", otherReservation)
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationUpdateJson(resource.getId(), updateStart, updateStart.plusHours(1), "PENDING")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/reservations/{id}", otherReservation)
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void statusOnlyCancellationIsAllowed() throws Exception {
        AppUser user = userRepository.findByUsername("user").orElseThrow();
        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setResource(resource);
        reservation.setStartTime(LocalDateTime.now().minusMinutes(30).truncatedTo(ChronoUnit.SECONDS));
        reservation.setEndTime(LocalDateTime.now().plusMinutes(30).truncatedTo(ChronoUnit.SECONDS));
        reservation.setPrice(new BigDecimal("500.00"));
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation = reservationRepository.save(reservation);

        String token = login("user", USER_PASSWORD);
        mockMvc.perform(put("/reservations/{id}", reservation.getId())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelledReservationNoLongerBlocksSameTimeSlot() throws Exception {
        String token = login("user", USER_PASSWORD);
        LocalDateTime start = future(2);
        long reservationId = createReservation(token, resource.getId(), start, start.plusHours(1));

        mockMvc.perform(put("/reservations/{id}", reservationId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/reservations")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationCreateJson(resource.getId(), start, start.plusHours(1))))
                .andExpect(status().isCreated());
    }

    @Test
    void adminCanViewUpdateAndDeleteAnyReservation() throws Exception {
        String userToken = login("user", USER_PASSWORD);
        String adminToken = login("admin", ADMIN_PASSWORD);
        LocalDateTime start = future(2);
        long reservationId = createReservation(userToken, resource.getId(), start, start.plusHours(1));

        mockMvc.perform(get("/reservations/{id}", reservationId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("user"));

        LocalDateTime updateStart = future(4);
        mockMvc.perform(put("/reservations/{id}", reservationId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationUpdateJson(resource.getId(), updateStart, updateStart.plusHours(2), "CONFIRMED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(delete("/reservations/{id}", reservationId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/reservations/{id}", reservationId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void reservationValidationRejectsInvalidTimesUnavailableResourceAndOverlap() throws Exception {
        String token = login("user", USER_PASSWORD);
        LocalDateTime start = future(2);

        mockMvc.perform(post("/reservations")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationCreateJson(resource.getId(), start.plusHours(2), start)))
                .andExpect(status().isBadRequest());

        createReservation(token, resource.getId(), start, start.plusHours(2));
        mockMvc.perform(post("/reservations")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationCreateJson(resource.getId(), start.plusHours(1), start.plusHours(3))))
                .andExpect(status().isBadRequest());

        resource.setAvailable(false);
        resourceRepository.save(resource);
        LocalDateTime later = future(8);
        mockMvc.perform(post("/reservations")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationCreateJson(resource.getId(), later, later.plusHours(1))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reservationCreateRejectsPastTimesAndMissingResource() throws Exception {
        String token = login("user", USER_PASSWORD);

        LocalDateTime past = LocalDateTime.now().minusHours(2).truncatedTo(ChronoUnit.SECONDS);
        mockMvc.perform(post("/reservations")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationCreateJson(resource.getId(), past, past.plusHours(1))))
                .andExpect(status().isBadRequest());

        LocalDateTime start = future(2);
        mockMvc.perform(post("/reservations")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationCreateJson(999999L, start, start.plusHours(1))))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateRejectsOverlapUnavailableResourceAndInvalidStatus() throws Exception {
        String token = login("user", USER_PASSWORD);
        BookableResource secondResource = createResource("Studio", new BigDecimal("700.00"));

        LocalDateTime firstStart = future(2);
        LocalDateTime secondStart = future(6);
        long first = createReservation(token, resource.getId(), firstStart, firstStart.plusHours(2));
        long second = createReservation(token, resource.getId(), secondStart, secondStart.plusHours(2));

        mockMvc.perform(put("/reservations/{id}", second)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationUpdateJson(resource.getId(), firstStart.plusMinutes(30), firstStart.plusHours(1), "PENDING")))
                .andExpect(status().isBadRequest());

        secondResource.setAvailable(false);
        resourceRepository.save(secondResource);
        LocalDateTime newStart = future(10);
        mockMvc.perform(put("/reservations/{id}", first)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationUpdateJson(secondResource.getId(), newStart, newStart.plusHours(1), "PENDING")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/reservations/{id}", first)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INVALID\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminReservationListSupportsFiltersPaginationAndSorting() throws Exception {
        String token = login("admin", ADMIN_PASSWORD);
        BookableResource cheap = createResource("Desk", new BigDecimal("100.00"));
        BookableResource expensive = createResource("Studio", new BigDecimal("900.00"));

        LocalDateTime t1 = future(2);
        LocalDateTime t2 = future(5);
        createReservation(token, cheap.getId(), t1, t1.plusHours(1));
        createReservation(token, expensive.getId(), t2, t2.plusHours(1));

        mockMvc.perform(get("/reservations")
                        .header("Authorization", bearer(token))
                        .param("minPrice", "500")
                        .param("maxPrice", "1000")
                        .param("page", "0")
                        .param("size", "1")
                        .param("sort", "price,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].price").value(900.00));

        mockMvc.perform(get("/reservations")
                        .header("Authorization", bearer(token))
                        .param("status", "PENDING")
                        .param("sort", "startTime,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void userFilteringRemainsRestrictedToAuthenticatedOwner() throws Exception {
        String userToken = login("user", USER_PASSWORD);
        String otherToken = login("other", OTHER_PASSWORD);
        BookableResource expensive = createResource("Premium Room", new BigDecimal("900.00"));

        LocalDateTime userStart = future(2);
        LocalDateTime otherStart = future(5);
        createReservation(userToken, expensive.getId(), userStart, userStart.plusHours(1));
        createReservation(otherToken, expensive.getId(), otherStart, otherStart.plusHours(1));

        mockMvc.perform(get("/reservations")
                        .header("Authorization", bearer(userToken))
                        .param("minPrice", "800")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value("user"));
    }

    @Test
    void invalidReservationQueryParametersReturnBadRequest() throws Exception {
        String token = login("admin", ADMIN_PASSWORD);

        mockMvc.perform(get("/reservations").header("Authorization", bearer(token)).param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/reservations").header("Authorization", bearer(token)).param("size", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/reservations").header("Authorization", bearer(token)).param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/reservations").header("Authorization", bearer(token)).param("minPrice", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/reservations").header("Authorization", bearer(token)).param("maxPrice", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/reservations").header("Authorization", bearer(token)).param("minPrice", "1000").param("maxPrice", "100"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/reservations").header("Authorization", bearer(token)).param("sort", "unknown,asc"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/reservations").header("Authorization", bearer(token)).param("sort", "price,sideways"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/reservations").header("Authorization", bearer(token)).param("sort", "price,asc,extra"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/reservations").header("Authorization", bearer(token)).param("status", "INVALID"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/reservations").header("Authorization", bearer(token)).param("minPrice", "not-a-number"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reservationNotFoundReturns404ForGetUpdateAndAdminDelete() throws Exception {
        String userToken = login("user", USER_PASSWORD);
        String adminToken = login("admin", ADMIN_PASSWORD);

        mockMvc.perform(get("/reservations/{id}", 999999L).header("Authorization", bearer(userToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/reservations/{id}", 999999L)
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/reservations/{id}", 999999L).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void swaggerAndOpenApiArePubliclyAccessible() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Resource Booking System API"));
    }

    private String login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private long createReservation(
            String token,
            long resourceId,
            LocalDateTime start,
            LocalDateTime end) throws Exception {
        String response = mockMvc.perform(post("/reservations")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationCreateJson(resourceId, start, end)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("id").asLong();
    }

    private BookableResource createResource(String name, BigDecimal hourlyPrice) {
        BookableResource created = new BookableResource();
        created.setName(name);
        created.setDescription(name + " description");
        created.setType("EQUIPMENT");
        created.setPricePerHour(hourlyPrice);
        created.setAvailable(true);
        return resourceRepository.save(created);
    }

    private String loginJson(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(new LoginBody(username, password));
    }

    private String resourceJson(String name, String type, String price, boolean available) throws Exception {
        return objectMapper.writeValueAsString(
                new ResourceBody(name, name + " description", type, new BigDecimal(price), available));
    }

    private String reservationCreateJson(long resourceId, LocalDateTime start, LocalDateTime end) throws Exception {
        return objectMapper.writeValueAsString(new ReservationCreateBody(resourceId, start, end));
    }

    private String reservationUpdateJson(
            long resourceId,
            LocalDateTime start,
            LocalDateTime end,
            String status) throws Exception {
        return objectMapper.writeValueAsString(new ReservationUpdateBody(resourceId, start, end, status));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private LocalDateTime future(long hours) {
        return LocalDateTime.now().plusDays(2).plusHours(hours).truncatedTo(ChronoUnit.SECONDS);
    }

    private record LoginBody(String username, String password) {}
    private record ResourceBody(
            String name,
            String description,
            String type,
            BigDecimal pricePerHour,
            boolean available) {}
    private record ReservationCreateBody(Long resourceId, LocalDateTime startTime, LocalDateTime endTime) {}
    private record ReservationUpdateBody(
            Long resourceId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String status) {}
}
