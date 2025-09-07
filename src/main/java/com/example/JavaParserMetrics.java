package com.example;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;

public class JavaParserMetrics {

    private static int totalMethods = 0;
    private static int totalClasses = 0;
    private static int totalInterfaces = 0;
    private static int totalEnums = 0;
    private static long totalLOC = 0;

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java ... JavaParserMetrics <directory>");
            System.exit(1);
        }

        Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            System.err.println("Error: " + args[0] + " is not a directory.");
            System.exit(1);
        }

        // Walk the directory structure and parse each .java file
        Files.walk(root)
             .filter(Files::isRegularFile)
             .filter(path -> path.toString().endsWith(".java"))
             .forEach(JavaParserMetrics::processFile);

        // Output results in CSV format
        System.out.println("methods," + totalMethods);
        System.out.println("classes," + totalClasses);
        System.out.println("interfaces," + totalInterfaces);
        System.out.println("enums," + totalEnums);
        System.out.println("loc," + totalLOC);
    }

    private static void processFile(Path path) {
        // 1) Count lines of code
        try (Stream<String> lines = Files.lines(path)) {
            totalLOC += lines.count();
        } catch (IOException e) {
            System.err.println("Error reading lines: " + path + " - " + e.getMessage());
        }

        // 2) Parse with JavaParser
        try {
            CompilationUnit cu = new JavaParser()
                    .parse(path)
                    .getResult()
                    .orElse(null);

            if (cu == null) {
                System.err.println("Could not parse " + path);
                return;
            }

            // Count classes and interfaces
            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cd -> {
                if (cd.isInterface()) {
                    totalInterfaces++;
                } else {
                    totalClasses++;
                }
            });

            // Count enums
            cu.findAll(EnumDeclaration.class).forEach(e -> totalEnums++);

            // Count methods
            cu.findAll(MethodDeclaration.class).forEach(m -> totalMethods++);

        } catch (Exception e) {
            System.err.println("Parse error in " + path + ": " + e.getMessage());
        }
    }
}

// package com.example;

// import com.github.javaparser.JavaParser;
// import com.github.javaparser.ast.CompilationUnit;
// import com.github.javaparser.ast.PackageDeclaration;
// import com.github.javaparser.ast.body.*;
// import com.github.javaparser.ast.expr.AnnotationExpr;
// import com.github.javaparser.ast.expr.MemberValuePair;
// import com.github.javaparser.ast.expr.NormalAnnotationExpr;
// import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;

// import java.io.IOException;
// import java.nio.file.*;
// import java.util.*;
// import java.util.stream.Stream;

// public class JavaParserMetrics {

//     private static int totalMethods = 0;
//     private static int totalClasses = 0;
//     private static int totalInterfaces = 0;
//     private static int totalEnums = 0;
//     private static long totalLOC = 0;

//     // New collections
//     private static final Set<String> controllers = new TreeSet<>();
//     private static final Set<String> services = new TreeSet<>();
//     private static final Set<String> repositories = new TreeSet<>();
//     private static final Set<String> entities = new TreeSet<>();
//     private static final Set<String> domainClasses = new TreeSet<>();

//     // Endpoint record: METHOD  PATH  -> Class#method
//     private static final List<String> apiEndpoints = new ArrayList<>();

//     public static void main(String[] args) throws IOException {
//         if (args.length < 1) {
//             System.err.println("Usage: java ... JavaParserMetrics <directory>");
//             System.exit(1);
//         }

//         Path root = Paths.get(args[0]);
//         if (!Files.isDirectory(root)) {
//             System.err.println("Error: " + args[0] + " is not a directory.");
//             System.exit(1);
//         }

//         Files.walk(root)
//                 .filter(Files::isRegularFile)
//                 .filter(path -> path.toString().endsWith(".java"))
//                 .forEach(JavaParserMetrics::processFile);

//         // --- Summary (CSV-like) ---
//         System.out.println("methods," + totalMethods);
//         System.out.println("classes," + totalClasses);
//         System.out.println("interfaces," + totalInterfaces);
//         System.out.println("enums," + totalEnums);
//         System.out.println("loc," + totalLOC);
//         System.out.println("controllers," + controllers.size());
//         System.out.println("services," + services.size());
//         System.out.println("repositories," + repositories.size());
//         System.out.println("entities," + entities.size());
//         System.out.println("domainClasses," + domainClasses.size());
//         System.out.println("endpoints," + apiEndpoints.size());
//         System.out.println();

//         // --- Detailed lists ---
//         printSet("Controllers", controllers);
//         printSet("Services", services);
//         printSet("Repositories", repositories);
//         printSet("Entities (@Entity)", entities);
//         printSet("Domain classes (by package name hint)", domainClasses);

//         System.out.println("API Endpoints (method path -> Class#method):");
//         apiEndpoints.forEach(System.out::println);
//     }

//     private static void processFile(Path path) {
//         // LOC
//         try (Stream<String> lines = Files.lines(path)) {
//             totalLOC += lines.count();
//         } catch (IOException e) {
//             System.err.println("Error reading lines: " + path + " - " + e.getMessage());
//         }

//         // Parse
//         try {
//             CompilationUnit cu = new JavaParser().parse(path).getResult().orElse(null);
//             if (cu == null) {
//                 System.err.println("Could not parse " + path);
//                 return;
//             }

//             Optional<PackageDeclaration> pkgDecl = cu.getPackageDeclaration();
//             String pkg = pkgDecl.map(pd -> pd.getName().asString()).orElse("");

//             // Count types
//             cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cd -> {
//                 if (cd.isInterface()) {
//                     totalInterfaces++;
//                 } else {
//                     totalClasses++;
//                 }
//             });
//             cu.findAll(EnumDeclaration.class).forEach(e -> totalEnums++);
//             cu.findAll(MethodDeclaration.class).forEach(m -> totalMethods++);

//             // Class roles, domain, entities
//             cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cd -> {
//                 String fqn = toFqn(pkg, cd.getNameAsString());

//                 // Controllers (Spring)
//                 if (hasAnno(cd, "RestController") || hasAnno(cd, "Controller") ||
//                     cd.getNameAsString().endsWith("Controller")) {
//                     controllers.add(fqn);
//                 }

//                 // Services (Spring)
//                 if (hasAnno(cd, "Service") || cd.getNameAsString().endsWith("Service")) {
//                     services.add(fqn);
//                 }

//                 // Repositories (Spring/JPA)
//                 if (hasAnno(cd, "Repository") || cd.getNameAsString().endsWith("Repository")) {
//                     repositories.add(fqn);
//                 }

//                 // Entities (JPA)
//                 if (hasAnno(cd, "Entity")) {
//                     entities.add(fqn);
//                 }

//                 // Domain classes (package hint)
//                 if (pkg.contains(".domain.") || pkg.endsWith(".domain")
//                         || pkg.contains(".model.") || pkg.endsWith(".model")) {
//                     domainClasses.add(fqn);
//                 }

//                 // Extract API endpoints for Spring + JAX-RS
//                 extractEndpointsForType(pkg, cd);
//             });

//             // JAX-RS endpoints can also be in classes annotated with @Path (including records)
//             cu.findAll(AnnotationDeclaration.class); // (not needed, just a note)

//         } catch (Exception e) {
//             System.err.println("Parse error in " + path + ": " + e.getMessage());
//         }
//     }

//     // ---- Helpers ----

//     private static String toFqn(String pkg, String simple) {
//         return (pkg == null || pkg.isEmpty()) ? simple : pkg + "." + simple;
//     }

//     private static boolean hasAnno(BodyDeclaration<?> node, String simpleName) {
//         for (AnnotationExpr a : node.getAnnotations()) {
//             String name = a.getName().getIdentifier(); // simple name
//             if (name.equals(simpleName)) return true;
//             // be liberal: allow endsWith in case of nested/simple differences
//             if (name.endsWith(simpleName)) return true;
//         }
//         return false;
//     }

//     private static String getRequestMappingPath(AnnotationExpr anno) {
//         // Handles @RequestMapping("..."), @RequestMapping(path="..."), @RequestMapping(value="...")
//         if (anno instanceof SingleMemberAnnotationExpr sma) {
//             return stripQuotes(sma.getMemberValue().toString());
//         }
//         if (anno instanceof NormalAnnotationExpr na) {
//             for (MemberValuePair pair : na.getPairs()) {
//                 String key = pair.getNameAsString();
//                 if (key.equals("path") || key.equals("value")) {
//                     return stripQuotes(pair.getValue().toString());
//                 }
//             }
//         }
//         return "";
//     }

//     private static String getSpringMethodAndPath(AnnotationExpr anno) {
//         // For @GetMapping, @PostMapping, etc.
//         String name = anno.getName().getIdentifier();
//         String http = switch (name) {
//             case "GetMapping" -> "GET";
//             case "PostMapping" -> "POST";
//             case "PutMapping" -> "PUT";
//             case "DeleteMapping" -> "DELETE";
//             case "PatchMapping" -> "PATCH";
//             case "RequestMapping" -> ""; // method may be inside attributes; we’ll leave blank unless specified
//             default -> null;
//         };
//         if (http == null && !name.equals("RequestMapping")) return null;

//         String path = "";
//         if (anno instanceof SingleMemberAnnotationExpr sma) {
//             path = stripQuotes(sma.getMemberValue().toString());
//         } else if (anno instanceof NormalAnnotationExpr na) {
//             String method = null;
//             for (MemberValuePair pair : na.getPairs()) {
//                 String key = pair.getNameAsString();
//                 if ((key.equals("path") || key.equals("value")) && path.isEmpty()) {
//                     path = stripQuotes(pair.getValue().toString());
//                 } else if (key.equals("method")) {
//                     // could be RequestMethod.GET, array, etc. Keep simple:
//                     String raw = pair.getValue().toString();
//                     if (raw.contains("GET")) method = "GET";
//                     else if (raw.contains("POST")) method = "POST";
//                     else if (raw.contains("PUT")) method = "PUT";
//                     else if (raw.contains("DELETE")) method = "DELETE";
//                     else if (raw.contains("PATCH")) method = "PATCH";
//                     if (http == null || http.isEmpty()) http = method;
//                 }
//             }
//         }
//         return (http == null ? "" : http) + " " + (path == null ? "" : path);
//     }

//     private static String getJaxRsPath(AnnotationExpr anno) {
//         // For @Path("...") on class or method
//         if (!anno.getName().getIdentifier().equals("Path")) return "";
//         if (anno instanceof SingleMemberAnnotationExpr sma) {
//             return stripQuotes(sma.getMemberValue().toString());
//         }
//         if (anno instanceof NormalAnnotationExpr na) {
//             for (MemberValuePair pair : na.getPairs()) {
//                 if (pair.getNameAsString().equals("value")) {
//                     return stripQuotes(pair.getValue().toString());
//                 }
//             }
//         }
//         return "";
//     }

//     private static String getJaxRsHttp(AnnotationExpr anno) {
//         // @GET, @POST, @PUT, @DELETE, @PATCH, @OPTIONS, @HEAD
//         String n = anno.getName().getIdentifier();
//         return switch (n) {
//             case "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD" -> n;
//             default -> "";
//         };
//     }

//     private static String stripQuotes(String s) {
//         if (s == null) return "";
//         return s.replaceAll("^\"|\"$", "");
//     }

//     private static void extractEndpointsForType(String pkg, ClassOrInterfaceDeclaration cd) {
//         String className = cd.getNameAsString();
//         String fqn = toFqn(pkg, className);

//         // Spring class-level @RequestMapping path (prefix)
//         String classSpringPath = "";
//         for (AnnotationExpr a : cd.getAnnotations()) {
//             if (a.getName().getIdentifier().equals("RequestMapping")) {
//                 classSpringPath = getRequestMappingPath(a);
//             }
//         }

//         // JAX-RS class-level @Path
//         String classJaxPath = "";
//         for (AnnotationExpr a : cd.getAnnotations()) {
//             String p = getJaxRsPath(a);
//             if (!p.isEmpty()) classJaxPath = p;
//         }

//         // Spring methods
//         cd.getMethods().forEach(m -> {
//             String methodMapping = null;
//             for (AnnotationExpr a : m.getAnnotations()) {
//                 String mp = getSpringMethodAndPath(a);
//                 if (mp != null) {
//                     methodMapping = mp; // e.g., "GET /users"
//                     break;
//                 }
//             }
//             if (methodMapping != null) {
//                 String[] parts = methodMapping.split(" ", 2);
//                 String http = parts.length > 0 ? parts[0] : "";
//                 String path = parts.length > 1 ? parts[1] : "";
//                 String fullPath = joinPaths(classSpringPath, path);
//                 apiEndpoints.add(fmtEndpoint(http, fullPath, fqn, m.getNameAsString()));
//             }
//         });

//         // JAX-RS methods
//         cd.getMethods().forEach(m -> {
//             String http = "";
//             String methodPath = "";
//             for (AnnotationExpr a : m.getAnnotations()) {
//                 if (http.isEmpty()) http = getJaxRsHttp(a);
//                 String p = getJaxRsPath(a);
//                 if (!p.isEmpty()) methodPath = p;
//             }
//             if (!http.isEmpty()) {
//                 String fullPath = joinPaths(classJaxPath, methodPath);
//                 apiEndpoints.add(fmtEndpoint(http, fullPath, fqn, m.getNameAsString()));
//             }
//         });
//     }

//     private static String joinPaths(String p1, String p2) {
//         if (p1 == null) p1 = "";
//         if (p2 == null) p2 = "";
//         String a = p1.endsWith("/") ? p1.substring(0, p1.length() - 1) : p1;
//         String b = p2.startsWith("/") ? p2 : (p2.isEmpty() ? "" : "/" + p2);
//         return (a + b).isEmpty() ? "/" : (a + b);
//     }

//     private static String fmtEndpoint(String http, String path, String fqn, String method) {
//         http = (http == null ? "" : http.trim());
//         path = (path == null ? "" : path.trim());
//         if (path.isEmpty()) path = "/";
//         return String.format("%-6s %-30s -> %s#%s", http, path, fqn, method);
//     }

//     private static void printSet(String title, Set<String> set) {
//         System.out.println(title + ":");
//         set.forEach(System.out::println);
//         System.out.println();
//     }
// }