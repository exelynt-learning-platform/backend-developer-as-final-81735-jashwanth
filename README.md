# Resource Booking System

Secure RESTful Resource Booking System built with:

- Java 17
- Spring Boot
- Spring Security
- JWT authentication
- BCrypt password hashing
- Spring Data JPA / Hibernate
- PostgreSQL
- Bean Validation
- Swagger / OpenAPI
- Maven
- JUnit / Spring Security Test

## Features

- JWT login with `POST /auth/login`
- ADMIN and USER roles
- Stateless JWT security
- BCrypt password encoding
- ADMIN full CRUD for resources
- USER read-only access to resources
- Reservation creation using authenticated JWT identity
- USER can access only their own reservations
- ADMIN can access all reservations
- Reservation statuses: `PENDING`, `CONFIRMED`, `CANCELLED`
- Decimal reservation prices
- Reservation filtering by status, minimum price and maximum price
- Pagination with `page` and `size`
- Optional sorting with `sort=field,asc|desc`
- Validation and structured error responses
- PostgreSQL persistence with JPA/Hibernate
- Swagger UI
- Seed ADMIN and USER accounts
- Reservation overlap protection

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

Create PostgreSQL database:

```sql
CREATE DATABASE resource_booking;
```

Default local configuration:

```text
DB_URL=jdbc:postgresql://localhost:5432/resource_booking
DB_USERNAME=postgres
DB_PASSWORD=postgres
```

## Environment Variables

```text
DB_URL=jdbc:postgresql://localhost:5432/resource_booking
DB_USERNAME=postgres
DB_PASSWORD=your_password
JWT_SECRET=your_long_random_secret_key_at_least_32_bytes
JWT_EXPIRATION_MS=86400000
PORT=8080
```

For production, always replace the development JWT secret and database credentials.

## Run

```bash
mvn clean test
mvn spring-boot:run
```

The API runs at:

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

## Seed Credentials

```text
ADMIN
username: admin
password: Admin@123

USER
username: user
password: User@123
```

## Authentication

### Login

`POST /auth/login`

```json
{
  "username": "user",
  "password": "User@123"
}
```

Response:

```json
{
  "token": "JWT_TOKEN",
  "tokenType": "Bearer",
  "username": "user",
  "role": "USER"
}
```

Use the token:

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

Example:

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

### Create Reservation

`POST /reservations`

USER identity is obtained from the authenticated JWT. No `userId` is accepted in the request.

```json
{
  "resourceId": 1,
  "startTime": "2030-01-10T10:00:00",
  "endTime": "2030-01-10T12:00:00"
}
```

Price is calculated from the resource hourly price and reservation duration.

### List Reservations

```text
GET /reservations
```

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

ADMIN receives all reservations.

USER receives only reservations belonging to the authenticated JWT user.

### Get One Reservation

```text
GET /reservations/{id}
```

### Update Reservation

ADMIN can update any reservation.

USER can update only their own reservation.

```json
{
  "resourceId": 1,
  "startTime": "2030-01-10T11:00:00",
  "endTime": "2030-01-10T13:00:00",
  "status": "PENDING"
}
```

### Delete Reservation

```text
DELETE /reservations/{id}
```

ADMIN only.

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
500 INTERNAL SERVER ERROR
```

## Security Design

- JWT is validated on every protected request.
- Session creation policy is stateless.
- Passwords are stored using BCrypt.
- Role-based authorization uses `ROLE_ADMIN` and `ROLE_USER`.
- USER reservation ownership is derived from `Authentication.getName()`.
- `userId` is intentionally absent from the reservation create request.
- ADMIN can access all reservations.
- USER access is restricted to their own reservations.
- Resource write operations are ADMIN-only.

## Testing Checklist

```text
mvn clean test
```

Manually verify:

1. Login as ADMIN.
2. Login as USER.
3. Access resources with USER.
4. Confirm USER cannot POST/PUT/DELETE resources.
5. Create a reservation as USER.
6. Confirm reservation response contains authenticated USER.
7. Confirm USER sees only their reservations.
8. Login as ADMIN.
9. Confirm ADMIN sees all reservations.
10. Test PENDING, CONFIRMED and CANCELLED.
11. Test minPrice and maxPrice.
12. Test page and size.
13. Test sorting.
14. Test invalid dates.
15. Test invalid resource ID.
16. Test missing JWT.
17. Test invalid JWT.
18. Test BCrypt seed login.
19. Open Swagger UI.
20. Run `mvn clean test`.

## Git Commands

```bash
git add .
git commit -m "Implement secure resource booking system"
git push origin <assignment-branch>
```
