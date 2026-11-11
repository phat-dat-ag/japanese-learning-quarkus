# Java Code Style Rules R01–R19

These canonical rules are mandatory whenever creating or modifying Java source
in this Quarkus service. Read the complete document before coding and review
every modified Java file before completion. Preserve Java semantics, business
logic, and existing behavior; avoid unrelated formatting or refactoring.

Apply the rules to Jakarta REST resources, CDI beans and services, Quarkus
security and configuration, Hibernate ORM and Panache code (including this
project's Hibernate Reactive Panache), logging and observability, filters,
interceptors, utilities, and production, unit, integration, and Quarkus tests.
Mockito examples illustrate formatting only: this project uses its existing
test tools and does not require adding Mockito or any other dependency.

Spring examples below are retained as canonical formatting illustrations only.
They do not prescribe Spring APIs, annotations, or implementation patterns for
this Quarkus service. R19 takes precedence over compact formatting preferences
when an expression is horizontally dense.

## R01 — One Statement Per Line

- Write exactly one Java statement per line.
- Never combine multiple statements using semicolons on the same line.
- Never compress variable declarations, method calls, or assignments.
- Prefer clear vertical structure over fewer lines.

Incorrect:

```java
config.setPort(8080); config.setEnabled(true);
```

Correct:

```java
config.setPort(8080);
config.setEnabled(true);
```

## R02 — Separate Annotations and Declarations

- Place annotations on separate lines before their declarations.
- Use one annotation per line.
- Never combine annotations and method declarations on one line.
- Apply consistently to classes, constructors, methods, fields, and parameters.

Incorrect:

```java
@Test void shouldReturnSuccess() {
}
```

Correct:

```java
@Test
void shouldReturnSuccess() {
}
```

## R03 — Mandatory Braces

- Always use braces for `if`, `else`, `for`, `while`, and `do-while`.
- Never omit braces for single-statement bodies.
- Place the opening brace on the control-flow declaration line.
- Place the closing brace on its own line.

Incorrect:

```java
if (enabled) return;
```

Correct:

```java
if (enabled) {
    return;
}
```

## R04 — Strict Line Length Policy

This is the latest approved version and supersedes the previous 100/120 policy.

- Preferred target: 80 characters per line.
- Normal hard maximum: 100 characters.
- Exceptional maximum: 120 characters, only when unavoidable.
- Exceptions may include imports, package declarations, unsplittable identifiers,
  and literals that cannot safely be divided.
- Prefer splitting long expressions at logical boundaries.
- Do not shorten meaningful identifiers solely to satisfy line length.
- Do not compress complex expressions just because they fit within 100 characters.
- Avoid horizontal scrolling on a 15-inch laptop.
- Avoid unnecessary line breaks for simple expressions.

## R05 — No Wildcard Imports

- Never use wildcard imports.
- Never use wildcard static imports.
- Import only the types and static methods actually used.
- Remove unused imports.

Incorrect:

```java
import java.util.*;
import static org.mockito.Mockito.*;
```

Correct:

```java
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
```

## R06 — Logical Code Grouping

Organize code into clear logical groups, such as:

- Dependency and variable initialization.
- Configuration and mock setup.
- Business operations.
- Validation and assertions.
- Cleanup and resource release.
- Return statements.

Rules:

- Separate distinct groups with one blank line.
- Keep closely related statements together.
- Avoid arbitrary blank lines.
- Avoid redundant comments when code structure is self-explanatory.

## R07 — Descriptive Naming

- Use meaningful names for classes, methods, fields, parameters, variables, and
  constants.
- Test method names should describe the behavior being tested.
- Avoid ambiguous abbreviations and redundant words.
- Prefer clarity over unnecessarily short names.
- Do not rename existing public APIs solely for formatting.
- Preserve project naming conventions when they remain clear and consistent.

## R08 — Named Constants

- Extract meaningful configuration values, timeouts, limits, and repeated values
  into named constants.
- Use descriptive constant names.
- Do not extract every numeric literal automatically.
- Preserve behavior and avoid unnecessary abstractions.

Example:

```java
private static final int MAX_POOL_SIZE = 2;
private static final int MAX_REFRESH_ATTEMPTS = 150;
private static final int REFRESH_INTERVAL_MS = 10;
```

## R09 — Readable Method Chaining

- Keep simple, short chains compact.
- Break long or complex chains into multiple lines.
- Prefer one significant operation per line.
- Place the dot at the beginning of continuation lines.
- Avoid deeply nested chained calls on one line.
- Apply R19 when a chain is horizontally dense even below 100 characters.

Example:

```java
double total = registry.find("app.security.events")
        .counters()
        .stream()
        .mapToDouble(counter -> counter.count())
        .sum();
```

## R10 — Never Compress Code

- Never compress multiple operations onto one line.
- Do not use one-line `try`, `catch`, `finally`, or control-flow blocks.
- Prioritize readability over minimizing line count.

Incorrect:

```java
try { process(); } finally { cleanup(); }
```

Correct:

```java
try {
    process();
} finally {
    cleanup();
}
```

## R11 — Import Organization

Preferred import groups:

1. Project-specific imports.
2. Framework and third-party imports.
3. Java and Jakarta imports.
4. Static imports.

Requirements:

- Explicit imports only.
- No wildcard imports.
- Alphabetize imports within each group.
- Separate major groups with one blank line.
- Remove unused imports.
- Preserve an established project-wide import order when present, while
  maintaining explicit imports.

## R12 — No Fully Qualified Class Names Inside Java Code

- Use explicit imports instead of fully qualified class names in fields,
  parameters, return types, constructors, annotations, and method bodies.
- Types from `java.lang` and the same package do not require imports.
- Fully qualified names are permitted only when necessary to resolve unavoidable
  naming conflicts.

Incorrect:

```java
private com.example.metrics.SecurityMetrics securityMetrics;
```

Correct:

```java
import com.example.metrics.SecurityMetrics;
```

```java
private SecurityMetrics securityMetrics;
```

## R13 — Vertical Spacing and Indentation

- Use four spaces per indentation level.
- Never use tabs.
- Insert one space after commas.
- Insert spaces around binary operators.
- Separate imports and class declarations with a blank line.
- Separate distinct logical code groups with one blank line.
- Keep related statements together.
- Avoid excessive consecutive blank lines.
- Remove trailing whitespace.
- End Java source files with a newline.

Incorrect:

```java
metrics.completed("POST","/auth/login",false,401);
metrics.completed("GET","/admin/users",false,403);
assertThat(registry.getMeters()).hasSize(7);
```

Correct:

```java
metrics.completed("POST", "/auth/login", false, 401);
metrics.completed("GET", "/admin/users", false, 403);

assertThat(registry.getMeters()).hasSize(7);
```

## R14 — Method Arguments, Parameters, and Annotations

### Method invocations

For long or horizontally dense method calls:

- Place each argument on a separate line.
- Use consistent indentation.
- Place the closing `)` on its own line.
- Keep short and simple calls compact.

Example:

```java
return MeterFilter.maximumAllowableTags(
        "http.server.requests",
        "uri",
        100,
        MeterFilter.deny()
);
```

### Method declarations

For multiline parameter lists:

- Place each parameter on its own line.
- Use consistent indentation.
- Put the closing `)` on its own line.
- Keep short declarations compact.

Example:

```java
SecurityFilterChain metricsSecurity(
        HttpSecurity http,
        ObjectProvider<Connector> connectors
) throws Exception {
    // Implementation
}
```

### Annotated parameters

- Place each annotation on its own line.
- Place the parameter type and name on the next line.
- Insert one blank line between annotated parameter groups.
- Do not insert a blank line between an annotation and its associated parameter.
- Keep annotations belonging to the same parameter together.

Example:

```java
@Bean
MeterRegistryCustomizer<MeterRegistry> metricsIdentity(
        @Value("${spring.application.name}")
        String service,

        @Value("${app.deployment-environment:development}")
        String environment,

        @Value("${app.version:unknown}")
        String version
) {
    // Implementation
}
```

Apply equivalent formatting to constructors and long annotation argument lists.

## R15 — Control Flow Block Formatting

- Always use braces.
- Place control-flow bodies on subsequent lines.
- Never place a body statement on the same line as its condition.
- Place closing braces on separate lines.
- Use R14 for long calls inside control-flow bodies.

Incorrect:

```java
if (metrics != null) metrics.completed(method(), route, internal, status);
```

Correct:

```java
if (metrics != null) {
    metrics.completed(
            method(),
            route,
            internal,
            status
    );
}
```

## R16 — Boolean Expressions and Condition Formatting

For multiline boolean expressions:

- Place each logical condition on a separate line.
- Put `&&` and `||` at the beginning of continuation lines.
- Align conditions consistently.
- For multiline `if`, `else if`, and `while` conditions, place the closing `)` on
  its own line.
- Keep simple conditions compact.
- Preserve operator precedence and short-circuit behavior.

Example:

```java
boolean health = path.equals("/actuator/health")
        || path.startsWith("/actuator/health/")
        || path.equals("/actuator/prometheus");
```

Example:

```java
if (request.isSecure()
        && user.isActive()
        && user.hasPermission("ADMIN")
) {
    processRequest();
}
```

## R17 — Logical Block Separation

- Separate initialization, configuration, execution, validation, and return
  operations when they are distinct logical groups.
- Use one blank line between logical groups.
- Keep related configuration calls together.
- Avoid excessive whitespace.

Example:

```java
Connector connector = new Connector(
        TomcatServletWebServerFactory.DEFAULT_PROTOCOL
);

connector.setPort(port);
connector.setProperty("address", "0.0.0.0");
connector.setProperty("maxThreads", "8");
connector.setProperty("maxConnections", "32");
connector.setProperty("acceptCount", "16");
connector.setProperty("connectionTimeout", "5000");

return connector;
```

## R18 — Long Method and Class Declarations

### Long method declarations

- Prefer keeping the return type and method name together.
- Put long parameter lists on separate lines.
- Put the closing `)` on its own line.
- Keep `throws` on the same line as the closing `)` whenever possible.
- Do not separate the return type from the method name unless unavoidable.

Example:

```java
@Bean
@ConditionalOnProperty(
        name = "app.metrics.listener.enabled",
        havingValue = "true"
)
WebServerFactoryCustomizer<TomcatServletWebServerFactory> metricsListener(
        Connector metricsConnector
) {
    return factory -> factory.addAdditionalConnectors(metricsConnector);
}
```

Example with `throws`:

```java
SecurityFilterChain metricsSecurity(
        HttpSecurity http,
        ObjectProvider<Connector> connectors
) throws Exception {
    // Implementation
}
```

### Long class declarations

Break long `extends` and `implements` declarations into readable continuation
lines.

Example:

```java
public class SecurityMetricsConfiguration
        extends AbstractMetricsConfiguration
        implements WebMvcConfigurer, ApplicationContextAware {

    // Implementation
}
```

## R19 — Strict Horizontal Readability and Expression Wrapping

This is the latest approved rule.

It takes precedence over compact formatting preferences whenever expressions
are horizontally dense.

### Horizontal readability

- Target 80 characters per line.
- Keep ordinary Java lines within 100 characters.
- Allow up to 120 characters only for unavoidable exceptions.
- Avoid horizontally dense expressions even when they fit within 100 characters.
- Prefer readable vertical formatting over minimizing line count.

### Fluent method chaining

- Prefer one significant chained operation per line.
- Put dots at the beginning of continuation lines.
- Separate nested callbacks visually.
- Avoid compressing multiple configuration steps onto one line.
- Keep trivial chains compact.

Incorrect:

```java
@Bean
SecurityFilterChain security(HttpSecurity http) throws Exception {
    return http.authorizeHttpRequests(requests -> requests.anyRequest().permitAll()).build();
}
```

Correct:

```java
@Bean
SecurityFilterChain security(HttpSecurity http) throws Exception {
    return http
            .authorizeHttpRequests(requests ->
                    requests.anyRequest().permitAll()
            )
            .build();
}
```

### Lambda expressions

- Break long or nested lambda expressions into readable continuation lines.
- Prefer expression lambdas for single-expression behavior.
- Use block lambdas when multiple statements are required.
- Do not introduce unnecessary braces.
- Preserve lambda return behavior and functional interface compatibility.

### Spring Boot and Quarkus

Apply strict horizontal readability to:

- Security configuration.
- Fluent builder APIs.
- Stream pipelines.
- HTTP client configuration.
- Metrics and observability configuration.
- Dependency configuration.
- Nested callbacks.
- Validation and assertion chains.

Do not introduce Spring Boot-specific implementation patterns into Quarkus or
Quarkus-specific patterns into Spring Boot.

### Quarkus examples

Annotated Jakarta REST parameters follow R02 and R14:

```java
@GET
@Path("/{id}")
public Uni<VocabularyResponse> findVocabulary(
        @PathParam("id")
        Long vocabularyId
) {
    return vocabularyService.findById(vocabularyId);
}
```

Keep significant operations and nested callbacks readable in Mutiny chains:

Incorrect:

```java
return repository.findById(id).onItem().ifNotNull().transform(item -> mapper.map(item));
```

Correct:

```java
return repository.findById(id)
        .onItem().ifNotNull()
        .transform(item -> mapper.map(item));
```

Use a block lambda when a callback requires multiple statements:

```java
return repository.findById(id)
        .invoke(item -> {
            validateItem(item);
            logOperation(item);
        });
```

Formatting must preserve reactive subscription, transaction boundaries,
short-circuit behavior, callback ordering, and return values. Examples do not
authorize changes to the project's architecture, dependencies, or API contracts.
