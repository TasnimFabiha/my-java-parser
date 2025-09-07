# Java Source Metrics & API Scanner

A tiny, single-jar CLI that scans a Java source tree and reports:

- Counts: **methods, classes, interfaces, enums, LOC**
- Spring roles: **@RestController/@Controller**, **@Service**, **@Repository**
- JPA **@Entity** classes
- “Domain” classes (by package hints: `.domain` / `.model`)
- HTTP endpoints from **Spring MVC** (`@GetMapping`, `@PostMapping`, `@RequestMapping` …) and **JAX-RS** (`@Path`, `@GET` …)

Built on [JavaParser](https://javaparser.org/). The shaded JAR includes all dependencies—no classpath hassles.

---

## Requirements

- **Java 17+**
- **Maven 3.8+** (only if you build from source)

---

## Build

```bash
mvn clean package -DskipTests
```
This produces a fat jar at:

```
target/my-java-parser.jar
```

> The filename is stable (set via `<finalName>` in the POM).

---

## Run

```bash
java -jar target/my-java-parser.jar /path/to/java/src/root
```

Examples:

```bash
# macOS/Linux
java -jar target/my-java-parser.jar ~/projects/petclinic/src/main/java

# Windows (PowerShell)
java -jar target\my-java-parser.jar C:\code\myapp\src\main\java
```

---

## Output

First, a CSV-style summary:

```
methods,1234
classes,210
interfaces,18
enums,7
loc,54321
controllers,6
services,14
repositories,5
entities,12
domainClasses,28
endpoints,42
```

Then detailed lists:

```
Controllers:
com.example.user.UserController
...

Services:
com.example.user.UserService
...

Entities (@Entity):
com.example.user.User
...

Domain classes (by package name hint):
com.example.model.Address
...
```

Finally, discovered endpoints:

```
API Endpoints (method path -> Class#method):
GET    /users               -> com.example.user.UserController#list
POST   /users               -> com.example.user.UserController#create
GET    /api/v1/orders/{id}  -> com.example.order.OrderResource#get
...
```

**Notes**

- Spring: supports `@GetMapping/@PostMapping/@PutMapping/@DeleteMapping/@PatchMapping` and `@RequestMapping` with `path/value` (arrays allowed) and `method=RequestMethod.*`.
- JAX-RS: supports class/method `@Path` and HTTP verb annotations (`@GET`, `@POST`, `@PUT`, `@DELETE`, `@PATCH`, `@OPTIONS`, `@HEAD`).
- “Domain” classes are inferred via package hints: `.domain` or `.model`.

---

## Using From a Notebook

A ready-to-use Python notebook (`call_javaparser.ipynb`) is included in the repo and shows how to invoke the jar programmatically.

---

## Troubleshooting

- **Unable to access jarfile …**  
  Ensure you’re pointing to `target/my-java-parser.jar` (not `original-*.jar`).

- **Could not find or load main class …**  
  Always run with `-jar` (the manifest already sets `Main-Class`).

- **IDE flags syntax but Maven builds**  
  Set the project JDK/Language level to **17** and re-import Maven.

---

## Dev Notes

- **Main class:** `com.example.JavaParserMetrics`  
- **Java:** 17 (uses modern features like switch expressions)  
- **Packaging:** shaded (fat) jar via `maven-shade-plugin`

Quick rebuild:

```bash
mvn -q clean package -DskipTests && java -jar target/my-java-parser.jar /path/to/src
```

---

