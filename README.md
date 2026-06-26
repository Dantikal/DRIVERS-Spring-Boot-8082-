# SAPREMO Drivers Service

A robust microservice backend handling all driver-related logistics and mobile-app synchronizations for the SAPREMO warehouse ecosystem. The service manages driver authentications, order fulfillment (dispatching and confirming goods), driver debts, payments, returns, and offline queue synchronization for the driver's mobile application.

## Table of Contents
1. [Project Overview](#1-project-overview)
2. [Tech Stack](#2-tech-stack)
3. [Architecture](#3-architecture)
4. [Domain Model](#4-domain-model)
5. [Getting Started](#5-getting-started)
6. [Security & Authentication](#6-security--authentication)
7. [API Reference](#7-api-reference)
8. [Business Rules](#8-business-rules)
9. [Testing](#9-testing)

## 1. Project Overview
The Drivers Service acts as the central hub for the driver's mobile application and the warehouse managers' desktop views. 
- **Drivers** use the mobile app to view assigned orders, track their debt, submit payments, return unsold/defective goods, and confirm receipt of warehouse dispatches.
- **Warehouse Managers** dispatch goods to drivers and process return requests.
- **Offline Mode:** The mobile app allows drivers to fulfill actions without an internet connection. Actions are queued locally and pushed to the server when connectivity is restored.

## 2. Tech Stack
| Component | Technology |
| --- | --- |
| **Language** | Java 17 |
| **Framework** | Spring Boot 3.5.x |
| **Security** | Spring Security + JWT |
| **Database** | PostgreSQL / MySQL |
| **Messaging** | Redis (Pub/Sub via `DriverEventPublisher`) |
| **ORM** | Spring Data JPA / Hibernate |
| **Build Tool** | Apache Maven |
| **Unit Testing** | JUnit 5 + Mockito |
| **Containerization**| Docker (Multi-stage Temurin JRE) |

## 3. Architecture
The project follows a modular, layered architecture:

```text
com.drivers.modules
├── auth/                 # JWT Authentication, login, refresh, logout
├── confirmation/         # Handover process: driver confirming receipt of goods
├── drivers/              # Driver entity management, debt calculation, statuses
├── events/               # Centralized Redis Pub/Sub (DriverEventPublisher)
├── offline/              # Offline-sync queue processor for mobile app actions
├── orders/               # Order creation, modification, dispatching, rejection
├── payments/             # Idempotent debt repayment handling
└── returns/              # Processing return items, file/photo uploads, approvals
```

### Request Flow
1. **Mobile App / Client** sends a REST request.
2. **Security Filter** intercepts the request and validates the JWT Bearer token.
3. **Controller** routes the request and performs basic DTO validation.
4. **Idempotency Guard** checks if the unique operation key was already processed (preventing double actions).
5. **Service** executes the core business logic (updating debt, validating state transitions).
6. **Repository** persists changes to the database.
7. **DriverEventPublisher** broadcasts state changes to Redis (`orders:updated`, `payments:received`) to inform the rest of the SAPREMO system.

## 4. Domain Model
- **Driver:** The core entity. Possesses a dynamically calculated `debt`.
- **DriverOrder:** Represents a dispatch of goods. Status flows: `NEW` → `DISPATCHED` → `CONFIRMED` / `REJECTED`.
- **DriverPayment:** Represents money handed over by the driver. Always decreases driver debt.
- **ReturnRequest:** Goods returned to the warehouse. Status flows: `PENDING` → `ACCEPTED` / `REJECTED`. Decreases debt *only* if accepted.
- **OfflineQueue:** Stores batched JSON payloads from the mobile app for guaranteed eventual processing.

## 5. Getting Started
### Prerequisites
- Java 17+
- Maven 3.8+
- Redis running on localhost:6379
- Target Database running locally
- Docker (optional)

### Running Locally
1. Configure your database and Redis connections in `src/main/resources/application.yml` (or `.properties`).
2. Start the application:
   ```bash
   ./mvnw spring-boot:run
   ```

### Docker Setup
The project includes a highly optimized, multi-stage `Dockerfile`.
```bash
docker compose up --build
```

## 6. Security & Authentication
The service uses stateless JWT authentication to secure the mobile app API.

- **Login:** Mobile app sends credentials to `/api/drivers/auth/login` and receives a JWT.
- **Token Usage:** The token is sent via the `Authorization: Bearer <token>` header for all protected routes.
- **Data Isolation:** Drivers can only read/write their own data and debt. Warehouse Managers and Admins have elevated privileges to interact with any driver's state.

## 7. API Reference (Key Modules)
*Note: Base URL is typically `/api/drivers`. Protected endpoints require a valid JWT token.*

### The OpenAPI spec is available at `/swagger-ui.html`.###

### Auth (`/auth`)
- `POST /login` - Authenticate driver and issue JWT.
- `POST /refresh` - Refresh an expired token.

### Offline Sync (`/offline`)
- `POST /sync` - Push a batch of offline actions to the server queue to be processed chronologically.

### Orders (`/orders`)
- `GET /` - Retrieve the driver's active orders.
- `PATCH /{id}/confirm` - Confirm receipt of a dispatched order.
- `PATCH /{id}/reject` - Reject a dispatched order.

### Returns (`/returns`)
- `POST /` - Create a new return request (supports Multipart photo uploads for damaged goods).
- `PATCH /{id}/accept` - (Manager/Admin) Accept return and deduct the amount from the driver's debt.

### Payments (`/payments`)
- `POST /` - Submit a payment to decrease debt. *Requires an Idempotency-Key header.*

## 8. Business Rules
| Rule | Description |
| --- | --- |
| **Debt Calculation** | Debt strictly increases when goods are `DISPATCHED`. It decreases *only* when a payment is logged or a return is `ACCEPTED`. |
| **Idempotency** | Core mutations (Payments, Returns, Order Updates) require an idempotency key. If a mobile app loses connection and retries the request, the server returns the cached response without double-processing the debt. |
| **Offline Synchronization** | The mobile app caches actions locally. When internet is restored, it pushes them to `/offline/sync`. The server unpacks the actions and executes them as if they occurred in real-time. |
| **Event-Driven Architecture** | Instead of synchronous HTTP calls to other services, this service broadcasts state changes to Redis topics (e.g., `returns:processed`), allowing the Warehouse service to react asynchronously. |

## 9. Testing
The service is thoroughly covered by unit tests using **JUnit 5** and **Mockito**, specifically verifying logic, state transitions, and idempotency boundaries.

```bash
# Run the complete test suite
./mvnw clean test
```

### Key Test Coverage
- **OrderServiceTest:** Verifies order status progression, item modification limits, and Redis event broadcasting.
- **PaymentsServiceTest:** Ensures debt reduction is mathematically correct and idempotency locks prevent double-charging.
- **ReturnServiceTest:** Validates that rejected returns do not alter debt, and accepted returns broadcast correctly.
- **Confirmation & Offline Tests:** Validates the handover mechanisms and offline JSON payload extractions.
