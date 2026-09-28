# E-Commerce Orders & Inventory Service

A console-based, production-style backend that simulates the order and
inventory core of an e-commerce system: products, stock, customers,
checkout, cancellation, and order history - built with **Java 17, Spring
Boot 3, Spring Data JPA/Hibernate, and PostgreSQL**.

This is **not** a REST API or web app. It's a terminal application backed by
a real layered Spring Boot architecture, built to demonstrate transaction
boundaries, row-level locking, and correct handling of concurrent checkout
requests - not just CRUD.

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Architecture](#architecture)
3. [Technologies](#technologies)
4. [Database Schema](#database-schema)
5. [Key Features](#key-features)
6. [Prerequisites](#prerequisites)
7. [1. Install and configure PostgreSQL](#1-install-and-configure-postgresql)
8. [2. Open the project in VS Code](#2-open-the-project-in-vs-code)
9. [3. Configure database credentials](#3-configure-database-credentials)
10. [4. Run the application](#4-run-the-application)
11. [Using the console menu](#using-the-console-menu)
12. [Seed data](#seed-data)
13. [Running the tests](#running-the-tests)
14. [Concurrency & locking](#concurrency--locking)
15. [Transaction boundaries](#transaction-boundaries)
16. [Design decisions](#design-decisions)
17. [Troubleshooting](#troubleshooting)
18. [Project structure](#project-structure)

---

## Project Overview

The service lets you, entirely from a terminal menu:

- Create, view, and update **products**
- Add stock and view **inventory** (total / reserved / available)
- Create and view **customers**
- **Check out** an order for a customer with multiple line items
- **Cancel** a confirmed order and have its stock restored
- View a single **order's details** or a customer's full **order history**

The checkout flow is the heart of the project: it validates the customer
and every product, locks each product's inventory row, checks available
stock, deducts it, and persists the order - all inside one database
transaction, so a failure on the *third* item rolls back everything,
including the successful deductions for the first two.

## Architecture

```
        Terminal (stdin/stdout)
               │
               ▼
        ConsoleController        <- menu, input parsing, printing only
               │
               ▼
          Service Layer          <- business rules, @Transactional
               │
               ▼
        Repository Layer         <- Spring Data JPA
               │
               ▼
           PostgreSQL
```

The controller never talks to a repository directly, and no business logic
lives in the entities - both by design, so the rules are all easy to find
in the `service` package.

```
src/main/java/com/example/ecommerce/
├── EcommerceApplication.java
├── config/          SeedDataLoader (startup sample data)
├── entity/          Product, Inventory, Customer, Order, OrderItem
├── repository/      Spring Data JPA repositories
├── service/         ProductService, InventoryService, CustomerService, OrderService
├── controller/       ConsoleController (the only entry point)
├── dto/             CreateProductRequest, CreateOrderRequest, OrderItemRequest
├── exception/       Custom exceptions + GlobalExceptionHandler
├── enums/           OrderStatus
└── util/            ConsoleInputUtil, RequestValidator
```

## Technologies

| Concern              | Choice                                   |
|----------------------|-------------------------------------------|
| Language             | Java 17                                   |
| Framework            | Spring Boot 3.3                           |
| Persistence          | Spring Data JPA + Hibernate 6             |
| Database             | PostgreSQL (no H2 - a real DB is required)|
| Build tool           | Maven                                     |
| Validation           | Jakarta Bean Validation (Hibernate Validator) |
| Testing              | JUnit 5, Mockito, Spring Boot Test        |
| Logging              | SLF4J + Logback (bundled with Spring Boot)|

Lombok is **intentionally not used** - see [Design decisions](#design-decisions).

## Database Schema

Hibernate creates/updates these tables automatically at startup
(`spring.jpa.hibernate.ddl-auto=update`); nothing needs to be created by
hand. The conceptual schema:

```sql
CREATE TABLE product (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR NOT NULL,
    description   VARCHAR(2000),
    price         NUMERIC(12,2) NOT NULL CHECK (price > 0),
    sku           VARCHAR NOT NULL UNIQUE,
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP NOT NULL
);

CREATE TABLE inventory (
    id                 BIGSERIAL PRIMARY KEY,
    product_id         BIGINT NOT NULL UNIQUE REFERENCES product(id),
    quantity           INTEGER NOT NULL CHECK (quantity >= 0),
    reserved_quantity  INTEGER NOT NULL CHECK (reserved_quantity >= 0),
    updated_at         TIMESTAMP NOT NULL
);

CREATE TABLE customer (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR NOT NULL,
    email       VARCHAR NOT NULL UNIQUE,
    phone       VARCHAR,
    created_at  TIMESTAMP NOT NULL
);

CREATE TABLE orders (   -- named "orders": ORDER is a reserved SQL keyword
    id            BIGSERIAL PRIMARY KEY,
    customer_id   BIGINT NOT NULL REFERENCES customer(id),
    total_amount  NUMERIC(12,2) NOT NULL,
    status        VARCHAR(20) NOT NULL,   -- PENDING | CONFIRMED | CANCELLED
    created_at    TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP NOT NULL
);

CREATE TABLE order_item (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT NOT NULL REFERENCES orders(id),
    product_id  BIGINT NOT NULL REFERENCES product(id),
    quantity    INTEGER NOT NULL,
    unit_price  NUMERIC(12,2) NOT NULL,   -- price captured at purchase time
    subtotal    NUMERIC(12,2) NOT NULL
);
```

Relationships: `Product ↔ Inventory` (one-to-one), `Customer → Order`
(one-to-many), `Order → OrderItem` (one-to-many), `OrderItem → Product`
(many-to-one). `unit_price` is copied onto each `OrderItem` at checkout so
a later change to `Product.price` never rewrites history.

## Key Features

- **Product management** - create/view/update, unique SKU enforced in Java and in the database
- **Inventory management** - add stock, view total/reserved/available quantity per product
- **Customer management** - create/view, unique email enforced in Java and in the database
- **Checkout** - multi-item orders, all-or-nothing via `@Transactional`, row-locked inventory checks (see [Concurrency & locking](#concurrency--locking))
- **Order cancellation** - atomically restores every item's stock and flips status to `CANCELLED`
- **Order lookup & customer history**
- **Centralized console-level exception handling** - a business error prints a clean message and returns you to the menu instead of crashing the app
- **Seed data** - a handful of sample products and customers on first run only

---

## Prerequisites

- **JDK 17+** (`java -version` to check)
- **PostgreSQL 13+** running locally (or reachable over the network)
- **VS Code**
- Maven - either installed globally (`mvn -version`), or none at all: the
  VS Code Maven extension (see below) bundles its own Maven runtime, so a
  separate install is optional if you're only using VS Code.

## 1. Install and configure PostgreSQL

If you don't already have PostgreSQL:

- **Windows/macOS:** download from https://www.postgresql.org/download/, or `brew install postgresql` on macOS.
- **Linux (Debian/Ubuntu):** `sudo apt install postgresql`

Then create the two databases the project uses - one for the app, one
disposable one for the integration tests:

```bash
psql -U postgres
```
```sql
CREATE DATABASE ecommerce_db;
CREATE DATABASE ecommerce_db_test;
```

(`ecommerce_db_test` can be dropped and recreated any time - the tests
never touch `ecommerce_db`.)

## 2. Open the project in VS Code

1. Unzip the project and open the folder in VS Code (`File > Open Folder...`).
2. Install the recommended extensions - VS Code should prompt you
   automatically ("This workspace has extension recommendations"); if not,
   open the Extensions panel and install:
   - **Extension Pack for Java** (`vscjava.vscode-java-pack`) - Java language support, debugger, test runner, Maven
   - **Spring Boot Extension Pack** (`vmware.vscode-spring-boot`) - Spring Boot Dashboard, `application.properties` support
3. Wait for the bottom-right status bar to finish "Java: Loading
   projects..." / "Importing Maven projects" - this is VS Code resolving
   `pom.xml` and downloading dependencies (needs an internet connection the
   first time). No manual `mvn install` is required for this step.

## 3. Configure database credentials

Defaults live in `src/main/resources/application.properties` and point at
`localhost:5432/ecommerce_db` with username/password `postgres`/`postgres`.
Override them without editing the file, in any of these ways:

- **Easiest for VS Code:** edit the `env` block already provided in
  `.vscode/launch.json` to match your actual username/password.
- **Terminal:** export environment variables before running:
  ```bash
  export DB_URL=jdbc:postgresql://localhost:5432/ecommerce_db
  export DB_USERNAME=postgres
  export DB_PASSWORD=your_password
  ```
- **Simplest:** just edit the default values directly in
  `application.properties`.

## 4. Run the application

Because this app reads from `System.in` via `Scanner`, run it somewhere
that gives you an interactive terminal - **not** VS Code's plain "Run"
button on some setups, which can swallow keyboard input. Any of these work
reliably:

**Option A - VS Code Debug view (recommended)**
Open the Run and Debug panel (`Ctrl+Shift+D` / `Cmd+Shift+D`) and launch
**"Run EcommerceApplication"** - this repo already ships that configuration
in `.vscode/launch.json` with `"console": "integratedTerminal"`, which
correctly supports typing into the running app.

**Option B - Spring Boot Dashboard**
With the Spring Boot Extension Pack installed, open the "Spring Boot
Dashboard" panel in the sidebar, find `ecommerce-orders-inventory`, and
click the play icon.

**Option C - Integrated terminal**
```bash
mvn spring-boot:run
```
(or, without Maven installed globally, use the VS Code Maven panel's
"spring-boot:run" entry under Plugins, which uses the extension's bundled
Maven.)

**Option D - Build a jar and run it directly**
```bash
mvn clean package -DskipTests
java -jar target/ecommerce-orders-inventory-1.0.0.jar
```

On first successful startup you'll see the seed data get inserted (once)
and then the main menu:

```
========================================
        E-COMMERCE ORDER SYSTEM
========================================
1. Product Management
2. Inventory Management
3. Customer Management
4. Create Order
5. View Order
6. Cancel Order
7. Customer Order History
8. View Inventory
9. Exit
Choose an option:
```

## Using the console menu

A typical first session:

1. **8 → View Inventory** to see the seeded products and their stock (Laptop, Wireless Mouse, Mechanical Keyboard, Monitor).
2. **3 → 1 → Create Customer**, or just use one of the two seeded customers (IDs `1` and `2`).
3. **4 → Create Order** - enter the customer ID, then repeatedly enter a product ID and quantity (enter `0` as the product ID to finish the list).
4. **5 → View Order** with the order ID printed after checkout to see the full breakdown.
5. **6 → Cancel Order** to cancel it and confirm (via **8**) that stock was restored.
6. **7 → Customer Order History** to see every order for a customer and drill into one.

Any mistake (unknown product ID, insufficient stock, invalid email, etc.)
prints a single `ERROR: ...` line and returns you to the menu - the app
never crashes on bad input.

## Seed data

On first run only (i.e., when the `product` table is empty),
`SeedDataLoader` inserts:

| Product             | SKU      | Price  | Stock |
|---------------------|----------|--------|-------|
| Laptop              | LAP-001  | 75000  | 10    |
| Wireless Mouse       | MOU-001  | 1500   | 20    |
| Mechanical Keyboard  | KEY-001  | 4500   | 5     |
| Monitor             | MON-001  | 18000  | 8     |

and two sample customers. Restarting the app never duplicates this data.

## Running the tests

```bash
mvn test
```

- **Unit tests** (`ProductServiceTest`, `InventoryServiceTest`,
  `OrderServiceTest`) use Mockito to mock repositories - no database
  needed.
- **Integration tests** (`OrderIntegrationTest`,
  `ConcurrencyCheckoutTest`) boot the full Spring context against a real
  PostgreSQL database and require `ecommerce_db_test` to exist (see step
  1 above). They:
  - prove that a multi-item order with one out-of-stock item rolls back
    *every* inventory deduction, not just the failing item;
  - prove that cancelling a confirmed order restores its inventory;
  - run two checkout attempts concurrently against 5 units of stock (4 +
    3 requested) and assert that exactly one succeeds and the final
    quantity is never negative and never reflects both sales.

If `ecommerce_db_test` isn't reachable, only the two integration test
classes will fail; the unit tests are unaffected. You can also run just
the unit tests:

```bash
mvn test -Dtest="ProductServiceTest,InventoryServiceTest,OrderServiceTest"
```

## Concurrency & locking

Checkout calls
`InventoryRepository.findByProductIdForUpdate(productId)`, which issues a
`SELECT ... FOR UPDATE` (via `@Lock(LockModeType.PESSIMISTIC_WRITE)`)
before reading a product's available quantity.

**Without this lock:**

```
Transaction A reads stock = 5
Transaction B reads stock = 5
A buys 4, B buys 4 -> both think it's available
Result: 8 units "sold" when only 5 existed
```

**With PESSIMISTIC_WRITE:**

```
Transaction A locks the inventory row
Transaction B blocks, waiting for the row lock
A updates stock and commits, releasing the lock
B then reads the UPDATED stock and fails if it's not enough
```

`ConcurrencyCheckoutTest` exercises exactly this with two real concurrent
transactions.

## Transaction boundaries

`OrderService.createOrder` and `OrderService.cancelOrder` are the only
methods annotated `@Transactional` for a real reason: each spans several
repository calls (validate customer → lock + check + deduct inventory per
item → save Order → save OrderItems) that must commit or roll back as one
unit. The annotation lives at the **service** layer - not on a repository
method (the atomicity requirement is about the sequence of calls, not any
single call) and not on the console controller (which should stay a thin
presentation layer with no persistence concerns). See the code comments on
`OrderService` for the full reasoning.

## Design decisions

A few choices worth knowing about if you're reading the code:

- **No Lombok.** Entities and DTOs use plain hand-written getters/setters.
  This keeps the project buildable and debuggable in any editor without
  requiring annotation-processing/Lombok plugin setup - one less thing to
  configure before you can run it in VS Code.
- **No entity `toString()`/`equals()`/`hashCode()` overrides.** Bidirectional
  JPA relationships (e.g. `Order` ↔ `OrderItem`) can recurse infinitely if
  you print the entity graph directly. Instead, `ConsoleController` always
  prints specific fields it fetched itself, never the entity object.
- **`reservedQuantity` exists in the schema but is always `0` here.** It's
  there to support a future two-phase reserve → confirm/release workflow
  (useful if payment becomes asynchronous). This project's checkout is a
  single synchronous `@Transactional` method, so stock is deducted
  directly from `quantity` - see the comment on `Inventory.java`.
- **Table is named `orders`, not `order`** - `ORDER` is a reserved SQL
  keyword in PostgreSQL.
- **Jakarta Bean Validation is invoked manually**, via the small
  `RequestValidator` helper, because this is a console app with no Spring
  MVC request pipeline to trigger `@Valid` automatically.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `Connection to localhost:5432 refused` | PostgreSQL isn't running, or is on a different port/host - check `DB_URL`. |
| `FATAL: database "ecommerce_db" does not exist` | Run the `CREATE DATABASE` commands from step 1. |
| `FATAL: password authentication failed` | Update `DB_USERNAME`/`DB_PASSWORD` (env vars, `launch.json`, or `application.properties`). |
| Typing into the running app does nothing / app seems stuck | You're running it in a mode that doesn't forward stdin. Use the provided "Run EcommerceApplication" launch config (`integratedTerminal`), Spring Boot Dashboard, or `mvn spring-boot:run` in a terminal. |
| Integration tests fail with a connection error but unit tests pass | `ecommerce_db_test` doesn't exist yet, or isn't reachable - create it (step 1) or set `TEST_DB_URL`/`TEST_DB_USERNAME`/`TEST_DB_PASSWORD`. |
| VS Code shows red squiggly errors everywhere right after opening | The Maven import hasn't finished yet - wait for the status bar to settle, or run "Java: Clean Java Language Server Workspace" from the command palette. |

## Project structure

```
ecommerce-orders-inventory/
├── .vscode/
│   ├── extensions.json      recommended VS Code extensions
│   ├── launch.json          ready-to-use run/debug configuration
│   └── settings.json
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/com/example/ecommerce/...   (see Architecture above)
    │   └── resources/application.properties
    └── test/
        ├── java/com/example/ecommerce/
        │   ├── OrderIntegrationTest.java
        │   ├── ConcurrencyCheckoutTest.java
        │   └── service/
        │       ├── ProductServiceTest.java
        │       ├── InventoryServiceTest.java
        │       └── OrderServiceTest.java
        └── resources/application.properties   (points at ecommerce_db_test)
```
