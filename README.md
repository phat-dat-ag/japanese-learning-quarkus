# Japanese Learning Service

A backend microservice for the Japanese Learning application, built with Java 17, Quarkus, Hibernate Reactive Panache, and MySQL.

## Modules

- **Flashcard:** retrieves vocabulary lists and detailed flashcards.
- **Vocabulary:** imports vocabulary JSON and provides JLPT levels and lessons.
- **Common:** shared API responses, business exceptions, and request tracing.

## Run Locally

Start MySQL and apply the Flyway migrations in `src/main/resources/db/migration/` separately. The application does not generate the database schema.

Set these environment variables, or add them to a local `.env` file:

```env
DB_USERNAME=your_username
DB_PASSWORD=your_password
DB_REACTIVE_URL=mysql://localhost:3306/japanese_learning
```

Start the service with the Maven Wrapper:

```sh
./mvnw quarkus:dev
```

On Windows PowerShell, use `.\mvnw.cmd` instead of `./mvnw`. The default address is `http://localhost:8080`.

## API

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | `/api/v1/flashcards` | List flashcards; filter by `level`, `lesson`, `page`, and `size` |
| GET | `/api/v1/flashcards/{id}` | Retrieve a flashcard |
| GET | `/api/v1/jlpt-levels` | List JLPT levels |
| GET | `/api/v1/lessons?level=N5` | List lessons by level |
| POST | `/api/vocabularies/import` | Import JSON using the multipart field `file` |

Success and error responses include trace and correlation IDs. Clients may supply `X-Trace-Id` and `X-Correlation-Id` headers.

## Test and Build

```sh
./mvnw test
./mvnw package
```

Build output is written to `target/`.
