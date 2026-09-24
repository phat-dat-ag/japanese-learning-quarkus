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

## Docker

Build from this service directory (only Docker is required on the host):

```sh
docker build -t japanese-learning-vocabulary:local .
```

The root Dockerfile builds the default Quarkus fast-jar with Java 17 and the
pinned Maven Wrapper, then copies only `quarkus-app` into the UBI 9 OpenJDK 17
runtime. It runs as non-root UID 185 on `0.0.0.0:8080`. Maven and the JDK are
build-stage tools; the final image contains the Java runtime. Tests are run
separately using the commands below and are skipped during image packaging.

Supply these existing variables at runtime, for example through a local,
untracked `.env.docker` passed with `--env-file`:

| Variable | Container value |
| --- | --- |
| `DB_USERNAME` | Required MySQL application username |
| `DB_PASSWORD` | Required MySQL application password |
| `DB_REACTIVE_URL` | Required, e.g. `mysql://mysql:3306/japanese_learning` |
| `AUTH_SERVER_URL` | Auth service's internal HTTP base URL, e.g. `http://user-service:8080`; use its actual service name and container port |
| `AUTH_JWKS_URL` | Optional full public JWKS URL; defaults to `${AUTH_SERVER_URL}/.well-known/jwks.json` |
| `AUTH_JWT_ISSUER` | Must match issued tokens; default `JapaneseLearning.User` |
| `AUTH_JWT_AUDIENCE` | Must match issued tokens; default `JapaneseLearning` |

Set `AUTH_SERVER_URL` or an absolute `AUTH_JWKS_URL` to the Docker-reachable Auth
service; the local-development localhost default cannot reach another container.
Only public JWKS is needed. Do not supply signing keys or client secrets.

After MySQL, the separate root-owned Flyway service, and Auth are ready, run on
their shared Docker network (replace `japanese-learning` with its actual name):

```sh
docker run --rm --name vocabulary --network japanese-learning --env-file .env.docker -p 8080:8080 japanese-learning-vocabulary:local
```

MySQL remains reactive. Schema generation is disabled and this image does not
execute Flyway; the orchestration repository owns migrations. TLS terminates at
the future gateway/deployment layer.

Existing SmallRye Health routes are `/q/health`, `/q/health/live`, and
`/q/health/ready` on port 8080. Use liveness for process checks and readiness for
traffic admission (including the built-in reactive datasource check). The aggregate
`/q/health` also reflects readiness and is not a liveness probe.
`HealthResponseFilter` retains only overall status and each check's name/status,
omitting raw datasource exception data without replacing or duplicating checks.
HTTP status remains 200 for UP and 503 for DOWN. `HealthResponseFilterTest`
verifies redaction and HTTP integration. No dependencies were added.
A MySQL outage fails readiness while liveness stays UP; later successful probes
restore readiness. The root Compose readiness probe discards response bodies.
JWKS is fetched at initialization, after Compose waits for .NET readiness.
Continuous JWKS availability is deliberately not a health dependency: known
cached keys allow verification during an Auth outage, while unknown keys that
require a refresh cannot be verified until JWKS is available again. Health probes
do not contact Auth, and this is not whole-system readiness.
Verify readiness, real .NET-issued User/Admin tokens, and the
Admin import against the shared network during orchestration integration.

## API

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | `/api/v1/flashcards` | List flashcards; filter by `level`, `lesson`, `page`, and `size` (User or Admin) |
| GET | `/api/v1/flashcards/{id}` | Retrieve a flashcard (User or Admin) |
| GET | `/api/v1/jlpt-levels` | List JLPT levels (User or Admin) |
| GET | `/api/v1/lessons?level=N5` | List lessons by level (User or Admin) |
| POST | `/api/vocabularies/import` | Import JSON using the multipart field `file` (Admin only) |

Success and error responses include trace and correlation IDs. Clients may supply `X-Trace-Id` and `X-Correlation-Id` headers.

## Test and Build

```sh
./mvnw test
./mvnw package
```

Build output is written to `target/`.

## JWT resource server

Quarkus 3.38.2 uses `quarkus-oidc` in bearer-token service mode. The Auth Service
need not implement OIDC discovery: Quarkus fetches its configured JWKS endpoint,
selects the public key by JWT `kid`, and verifies tokens locally. Discovery,
JWT/opaque-token introspection, and OIDC Dev Services are disabled. No client
secret, private key, login implementation, or per-request Auth Service call is used.

| Environment variable | Default |
| --- | --- |
| `AUTH_SERVER_URL` | `http://localhost:5116` |
| `AUTH_JWKS_URL` | `${AUTH_SERVER_URL}/.well-known/jwks.json` |
| `AUTH_JWT_ISSUER` | `JapaneseLearning.User` |
| `AUTH_JWT_AUDIENCE` | `JapaneseLearning` |

For Docker, set `AUTH_SERVER_URL=http://user-service:<port>` or override the
complete `AUTH_JWKS_URL`. Use HTTPS for production JWKS transport outside a trusted
local network. No cryptographic key material belongs in Quarkus configuration.

Only RS256 is accepted. Issuer, audience, subject, and expiration are required;
expired tokens and future `nbf` values are rejected with zero clock grace. Keep
service clocks synchronized. The principal is the immutable `sub`. `iat` is not
required because the .NET `TokenService` explicitly disables default token times
and supplies only `Expires`. Its standard outbound claim mapping serializes
`ClaimTypes.Role` as `role`; `quarkus.oidc.roles.role-claim-path=role` maps `User`
and `Admin` directly to Quarkus roles, including `@RolesAllowed("User")` and
`@RolesAllowed("Admin")`. No custom claim parser is needed.

Quarkus fetches and caches the JWKS at initialization. An unknown `kid` triggers
refresh subject to its default 10-minute forced-refresh throttle
(`quarkus.oidc.token.forced-jwk-refresh-interval`); known keys are reused without
per-request JWKS retrieval. Publish new keys before using them and retain old
public keys until their tokens expire. There is no introspection fallback.
The specific `OidcProvider` logger is set to ERROR because its verification
warnings can contain the rejected bearer token; this also suppresses its other
warnings. Avoid enabling token-bearing diagnostic logging in production.

All application REST resources use the centralized default policy
`quarkus.security.jaxrs.default-roles-allowed=User,Admin`. A new unannotated REST
operation therefore requires one of these application roles. The import method's
explicit `@RolesAllowed("Admin")` overrides that default. No per-method User/Admin
annotations or additional role mapping are needed.

| Resource | Operation | Allowed roles |
| --- | --- | --- |
| `FlashcardResource` | `GET /api/v1/flashcards` | User, Admin |
| `FlashcardResource` | `GET /api/v1/flashcards/{id}` | User, Admin |
| `JlptLevelResource` | `GET /api/v1/jlpt-levels` | User, Admin |
| `LessonResource` | `GET /api/v1/lessons?level=N5` | User, Admin |
| `VocabularyResource` | `POST /api/vocabularies/import` | Admin |

Missing/invalid tokens receive 401; authenticated users without an allowed role
receive 403. Business response wrappers and exception mappers are unchanged.
Swagger UI, OpenAPI, and health are infrastructure routes outside this REST
policy. Default proactive authentication still checks a bearer token if one is
supplied to an otherwise public route.

SmallRye OpenAPI explicitly defines one HTTP bearer scheme named `bearerAuth`,
with `scheme: bearer` and `bearerFormat: JWT`. Automatic OIDC scheme generation is
disabled. `META-INF/openapi.yaml` supplies a global `security: [{bearerAuth: []}]`
requirement inherited by every application operation, including import. Bearer
security has no OAuth scopes; the import's Admin restriction is enforced at runtime.
Swagger accepts only the raw token and automatically adds `Authorization: Bearer`.
Swagger UI retains Quarkus's default dev/test availability; this change does not
enable it in production builds.

### Automated security tests

`JwtSecurityTest` exercises the production resources with real signed tokens and
security interceptors. CDI test service alternatives isolate database operations.
`JwksTestResource` runs an ephemeral loopback HTTP server with two generated RSA
public keys; private keys exist only in test memory. The resource is shared with
Quarkus tests so the existing suite needs no running Auth Service. Test datasource
configuration disables database startup connections and Dev Services.

The JWT suite covers all normal APIs with User/Admin tokens and missing tokens,
non-application roles, import authorization, malformed tokens, wrong issuer/audience,
expired/missing expiration, future `nbf`, wrong RSA key, unknown `kid`, HS256,
RS512, JWKS cache reuse/key selection, and preventing a `groups=Admin` claim from
overriding `role=User`. The test tokens omit `iat` like .NET. Existing flashcard
HTTP tests use the same signed-token fixture while retaining their business and
response assertions.

`OpenApiSecurityTest` checks the exact single bearer scheme, effective security
requirements on all five operations, and anonymous access to OpenAPI, Swagger UI,
and health liveness. OpenAPI operation-level requirements may be omitted because
the specification defines inheritance from the root security requirement.

Commands used for verification (PowerShell, from this repository):

```powershell
.\mvnw.cmd -B -ntp test "-Dtest=JwtSecurityTest,OpenApiSecurityTest,FlashcardResourceTest"
.\mvnw.cmd -B -ntp package
.\mvnw.cmd -B -ntp package -DskipTests
git diff --check
```

Step 8.10 validation: the complete suite and packaging passed (80 tests, zero
failures/errors/skips) using `./mvnw verify -Dquarkus.http.test-port=0`
(`.\mvnw.cmd` on Windows). The ephemeral test port avoids conflicting with a
running development API on port 8081. The reader test now uses its own fixture,
and the validator's valid fixture includes the required lesson. Missing multipart
uploads now use Jakarta Bean Validation and return 400 after authorization.
Fresh migrations do not create lessons. Lesson rows are application/domain data
and must be provisioned through the lesson-management workflow before imports
reference them.
No formatter or static-analysis plugin is configured in the existing Maven build.

### Manual .NET integration (PowerShell)

Run commands from the Quarkus repository. Prerequisites: configured .NET database
and signing keys, an existing User account, and Quarkus MySQL with the existing
migrations/seeds applied. The optional Admin import writes dictionary data; use a
development database. Quarkus itself needs only the public JWKS endpoint.

1. In terminal A, start .NET using its HTTP launch profile:

   ```powershell
   dotnet run --project ../dotnet/src/JapaneseLearning.User.Api/JapaneseLearning.User.Api.csproj --launch-profile http
   ```

2. In terminal B, confirm the public JWKS and log in for a newly issued token:

   ```powershell
   Invoke-RestMethod http://localhost:5116/.well-known/jwks.json | ConvertTo-Json -Depth 5
   # Expect an RSA signing key with kid japanese-learning-local-1, n and e; no private components.
   $userCredential = Get-Credential -Message 'Existing User account: enter email and password'
   $loginBody = @{
       email = $userCredential.UserName
       password = $userCredential.GetNetworkCredential().Password
   } | ConvertTo-Json
   $login = Invoke-RestMethod -Method Post -Uri http://localhost:5116/api/auth/login -ContentType application/json -Body $loginBody
   $userToken = $login.accessToken
   ```

3. In terminal C, set the existing database variables (or use the local `.env`)
   and start Quarkus:

   ```powershell
   $env:AUTH_SERVER_URL = 'http://localhost:5116'
   $env:AUTH_JWKS_URL = 'http://localhost:5116/.well-known/jwks.json'
   $env:AUTH_JWT_ISSUER = 'JapaneseLearning.User'
   $env:AUTH_JWT_AUDIENCE = 'JapaneseLearning'
   .\mvnw.cmd quarkus:dev
   ```

4. In terminal B, check authentication and User authorization:

   ```powershell
   curl.exe -i http://localhost:8080/api/v1/jlpt-levels
   # Expected: 401
   curl.exe -i -H "Authorization: Bearer $userToken" http://localhost:8080/api/v1/jlpt-levels
   # Expected: 200 and the existing ApiResponse with JLPT levels
   curl.exe -i -H "Authorization: Bearer $userToken" -F "file=@src/main/resources/data/vocabulary/n5.json;type=application/json" http://localhost:8080/api/vocabularies/import
   # Expected: 403
   ```

5. If an Admin account is available, obtain its token and test the actual import:

   ```powershell
   $adminCredential = Get-Credential -Message 'Existing Admin account: enter email and password'
   $adminBody = @{
       email = $adminCredential.UserName
       password = $adminCredential.GetNetworkCredential().Password
   } | ConvertTo-Json
   $adminLogin = Invoke-RestMethod -Method Post -Uri http://localhost:5116/api/auth/login -ContentType application/json -Body $adminBody
   $adminToken = $adminLogin.accessToken
   curl.exe -i -H "Authorization: Bearer $adminToken" -F "file=@src/main/resources/data/vocabulary/n5.json;type=application/json" http://localhost:8080/api/vocabularies/import
   # Expected: 200 and the existing ApiResponse with import totals
   ```

These commands validate the real .NET-issued token using public JWKS information.
The real .NET-to-Quarkus JWKS flow and User/import denial were manually verified by the developer. The automated suite remains independent of that service.

### Manual Swagger verification

Start the Auth Service and Quarkus in development mode using the commands above;
obtain fresh User and Admin access tokens through .NET. Use a development database
with the existing schema and seeds for successful business responses.

1. Open `http://localhost:8080/q/swagger-ui/` without credentials. The page must load.
   `http://localhost:8080/q/openapi?format=json` must also load without credentials.
2. Click **Authorize**. Confirm a single **bearerAuth (http, Bearer)** entry with a
   token input; no OAuth login or username/password flow should be offered.
3. If already authorized, click **Logout**, then **Close**. Expand
   `GET /api/v1/jlpt-levels`, click **Try it out**, then **Execute**. Expect **401**.
4. Click **Authorize**, paste only the raw User JWT (`eyJ...`) into **Value**,
   click **Authorize**, then **Close**. Do not type `Bearer `.
5. Execute `GET /api/v1/jlpt-levels` again. Expect **200**. The generated curl or
   browser Network request must contain `Authorization: Bearer eyJ...` with one
   Bearer prefix. Flashcards and lessons use the same policy.
6. Expand `POST /api/vocabularies/import`, click **Try it out**, and select
   `src/main/resources/data/vocabulary/n5.json` in the **file** input. Execute with
   the User token. Expect **403**.
7. In **Authorize**, click **Logout**, paste a raw Admin JWT, authorize, and close.
   Execute `GET /api/v1/jlpt-levels` again. Expect **200**.
8. Execute the import again with the same valid file. Expect **200** and import
   totals when the development database is ready. This performs an actual import.
   A business/database error after authorization is separate from a 401/403.

The Swagger browser interaction must be checked manually; automated HTTP tests
verify its availability, the generated scheme, and the authorization matrix.
