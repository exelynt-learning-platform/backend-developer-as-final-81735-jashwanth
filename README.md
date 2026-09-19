# Resource Booking System

Secure RESTful Resource Booking System built with Java 17, Spring Boot, Spring Security, JWT, Spring Data JPA/Hibernate, PostgreSQL, Bean Validation, Swagger/OpenAPI, Maven, JUnit, MockMvc, and Spring Security Test.

## Features

- JWT authentication with `POST /auth/login`
- Stateless Spring Security configuration
- BCrypt password hashing
- `ADMIN` and `USER` role-based access control
- ADMIN full CRUD access to resources and reservations
- USER read-only access to resources
- USER can create reservations and update/view only their own reservations
- Reservation identity always comes from the authenticated JWT; request bodies never accept a user id
- Reservation statuses: `PENDING`, `CONFIRMED`, `CANCELLED`
- Decimal reservation price calculation
- Reservation overlap protection
- Filtering by status, minimum price, and maximum price
- Pagination using `page` and `size`
- Optional sorting using `sort=field,asc|desc`
- Structured validation and error responses
- PostgreSQL persistence with JPA/Hibernate
- Swagger/OpenAPI documentation
- Environment-configurable seed users; no plaintext seed passwords are stored in source code
- H2-based integration tests so `mvn clean test` does not require a local PostgreSQL instance

## Project Structure

```text
src/main/java/com/example/booking
├── config
├── controller
├── dto
├── entity
├── exception
├── repository
├── security
└── service
```

## Database Setup

Create the PostgreSQL database:

```sql
CREATE DATABASE resource_booking;
```

## Required Environment Variables

The application deliberately does not keep database passwords, JWT secrets, or seed passwords in source code.

PowerShell example:

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/resource_booking"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="your_postgres_password"
$env:JWT_SECRET="replace_with_a_long_random_secret_at_least_32_bytes"
$env:JWT_EXPIRATION_MS="86400000"
$env:PORT="8080"
```

## Optional Seed Users

To create test ADMIN and USER accounts automatically, enable seeding and provide credentials through environment variables:

```powershell
$env:SEED_USERS_ENABLED="true"
$env:SEED_ADMIN_USERNAME="admin"
$env:SEED_ADMIN_PASSWORD="choose_a_strong_admin_password"
$env:SEED_USER_USERNAME="user"
$env:SEED_USER_PASSWORD="choose_a_strong_user_password"
```

When `SEED_USERS_ENABLED=false` (the default), no seed credentials are created. This prevents hardcoded passwords from being committed to the repository.

## Build and Test

```bash
mvn clean test
```

The test profile uses an in-memory H2 database in PostgreSQL compatibility mode. Tests cover authentication, JWT validation, RBAC, resource CRUD, reservation ownership, USER own-reservation updates, ADMIN access, cancellation, overlap prevention, filtering, pagination, sorting, validation, error responses, and Swagger availability.

## Run

After setting the required PostgreSQL and JWT environment variables:

```bash
mvn spring-boot:run
```

Application:

```text
http://localhost:8080
```

Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

OpenAPI JSON:

```text
http://localhost:8080/v3/api-docs
```

## Authentication

### Login

`POST /auth/login`

```json
{
  "username": "user",
  "password": "the_password_set_in_SEED_USER_PASSWORD"
}
```

Example response:

```json
{
  "token": "JWT_TOKEN",
  "tokenType": "Bearer",
  "username": "user",
  "role": "USER"
}
```

Use the token on protected endpoints:

```text
Authorization: Bearer JWT_TOKEN
```

## Resource API

### USER + ADMIN

```text
GET /resources
GET /resources/{id}
```

### ADMIN only

```text
POST /resources
PUT /resources/{id}
DELETE /resources/{id}
```

Example resource request:

```json
{
  "name": "Conference Room B",
  "description": "Large meeting room",
  "type": "ROOM",
  "pricePerHour": 650.00,
  "available": true
}
```

## Reservation API

### Create Reservation — USER or ADMIN

`POST /reservations`

The authenticated username is obtained from the JWT. No `userId` is accepted in the request.

```json
{
  "resourceId": 1,
  "startTime": "2030-01-10T10:00:00",
  "endTime": "2030-01-10T12:00:00"
}
```

New reservations start with `PENDING` status. Price is calculated from resource hourly price and reservation duration.

### List Reservations — USER or ADMIN

```text
GET /reservations
```

ADMIN receives all matching reservations. USER receives only reservations owned by the authenticated JWT user, even when filters are supplied.

Filters:

```text
GET /reservations?status=PENDING
GET /reservations?minPrice=100
GET /reservations?maxPrice=1000
GET /reservations?minPrice=100&maxPrice=1000
```

Pagination:

```text
GET /reservations?page=0&size=10
```

Sorting:

```text
GET /reservations?sort=price,desc
GET /reservations?sort=startTime,asc
```

Supported sort fields:

```text
id
price
startTime
endTime
status
```

### Get Reservation — USER or ADMIN

```text
GET /reservations/{id}
```

USER can access only their own reservation. ADMIN can access any reservation.

### Update Reservation — USER or ADMIN

```text
PUT /reservations/{id}
```

USER can update only their own reservation. ADMIN can update any reservation. Ownership is checked in the service layer.

Full update example:

```json
{
  "resourceId": 1,
  "startTime": "2030-01-10T11:00:00",
  "endTime": "2030-01-10T13:00:00",
  "status": "CONFIRMED"
}
```

Status-only cancellation is supported, including for a reservation that has already started:

```json
{
  "status": "CANCELLED"
}
```

### Delete Reservation — ADMIN only

```text
DELETE /reservations/{id}
```

USER cannot delete reservations. USER can cancel their own reservation through the update endpoint.

## Reservation Status

Allowed values:

```text
PENDING
CONFIRMED
CANCELLED
```

## HTTP Status Codes

```text
200 OK
201 CREATED
204 NO CONTENT
400 BAD REQUEST
401 UNAUTHORIZED
403 FORBIDDEN
404 NOT FOUND
409 CONFLICT
500 INTERNAL SERVER ERROR
```

## Security Design

- JWT is validated on every protected request.
- Invalid or expired JWTs return `401 Unauthorized`.
- Session policy is stateless.
- Passwords are stored using BCrypt.
- No plaintext database, JWT, or seed passwords are committed in application source configuration.
- Role authorization is enforced in Spring Security before controller execution.
- Reservation ownership is enforced in the service layer.
- `userId` is absent from reservation create/update requests, preventing identity spoofing.
- USER cannot modify resources or delete reservations.
- ADMIN can manage all resources and reservations.
