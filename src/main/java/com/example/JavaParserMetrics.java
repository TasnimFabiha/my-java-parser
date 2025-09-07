// package com.example;

// import com.github.javaparser.JavaParser;
// import com.github.javaparser.ast.CompilationUnit;
// import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
// import com.github.javaparser.ast.body.EnumDeclaration;
// import com.github.javaparser.ast.body.MethodDeclaration;

// import java.io.IOException;
// import java.nio.file.*;
// import java.util.stream.Stream;

// public class JavaParserMetrics {

//     private static int totalMethods = 0;
//     private static int totalClasses = 0;
//     private static int totalInterfaces = 0;
//     private static int totalEnums = 0;
//     private static long totalLOC = 0;

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

//         // Walk the directory structure and parse each .java file
//         Files.walk(root)
//              .filter(Files::isRegularFile)
//              .filter(path -> path.toString().endsWith(".java"))
//              .forEach(JavaParserMetrics::processFile);

//         // Output results in CSV format
//         System.out.println("methods," + totalMethods);
//         System.out.println("classes," + totalClasses);
//         System.out.println("interfaces," + totalInterfaces);
//         System.out.println("enums," + totalEnums);
//         System.out.println("loc," + totalLOC);
//     }

//     private static void processFile(Path path) {
//         // 1) Count lines of code
//         try (Stream<String> lines = Files.lines(path)) {
//             totalLOC += lines.count();
//         } catch (IOException e) {
//             System.err.println("Error reading lines: " + path + " - " + e.getMessage());
//         }

//         // 2) Parse with JavaParser
//         try {
//             CompilationUnit cu = new JavaParser()
//                     .parse(path)
//                     .getResult()
//                     .orElse(null);

//             if (cu == null) {
//                 System.err.println("Could not parse " + path);
//                 return;
//             }

//             // Count classes and interfaces
//             cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cd -> {
//                 if (cd.isInterface()) {
//                     totalInterfaces++;
//                 } else {
//                     totalClasses++;
//                 }
//             });

//             // Count enums
//             cu.findAll(EnumDeclaration.class).forEach(e -> totalEnums++);

//             // Count methods
//             cu.findAll(MethodDeclaration.class).forEach(m -> totalMethods++);

//         } catch (Exception e) {
//             System.err.println("Parse error in " + path + ": " + e.getMessage());
//         }
//     }
// }

package com.example;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class JavaParserMetrics {

    private static int totalMethods = 0;
    private static int totalClasses = 0;
    private static int totalInterfaces = 0;
    private static int totalEnums = 0;
    private static long totalLOC = 0;

    // Buckets
    private static final Set<String> controllers = new TreeSet<>();
    private static final Set<String> services = new TreeSet<>();
    private static final Set<String> repositories = new TreeSet<>();
    private static final Set<String> entities = new TreeSet<>();
    private static final Set<String> domainClasses = new TreeSet<>();

    // METHOD  PATH  -> Class#method
    private static final List<String> apiEndpoints = new ArrayList<>();

    // Reuse a parser (lighter GC)
    private static final JavaParser PARSER = new JavaParser();

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

        // Walk source tree
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(JavaParserMetrics::processFile);
        }

        // --- Summary (CSV-like) ---
        System.out.println("methods," + totalMethods);
        System.out.println("classes," + totalClasses);
        System.out.println("interfaces," + totalInterfaces);
        System.out.println("enums," + totalEnums);
        System.out.println("loc," + totalLOC);
        System.out.println("controllers," + controllers.size());
        System.out.println("services," + services.size());
        System.out.println("repositories," + repositories.size());
        System.out.println("entities," + entities.size());
        System.out.println("domainClasses," + domainClasses.size());
        System.out.println("endpoints," + apiEndpoints.size());
        System.out.println();

        // --- Detailed lists ---
        printSet("Controllers", controllers);
        printSet("Services", services);
        printSet("Repositories", repositories);
        printSet("Entities (@Entity)", entities);
        printSet("Domain classes (by package name hint)", domainClasses);

        System.out.println("API Endpoints (method path -> Class#method):");
        apiEndpoints.forEach(System.out::println);
    }

    private static void processFile(Path path) {
        // LOC
        try (Stream<String> lines = Files.lines(path)) {
            totalLOC += lines.count();
        } catch (IOException e) {
            System.err.println("Error reading lines: " + path + " - " + e.getMessage());
        }

        // Parse
        try {
            CompilationUnit cu = PARSER.parse(path).getResult().orElse(null);
            if (cu == null) {
                System.err.println("Could not parse " + path);
                return;
            }

            String pkg = cu.getPackageDeclaration()
                           .map(PackageDeclaration::getNameAsString)
                           .orElse("");

            // Count types & methods
            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cd -> {
                if (cd.isInterface()) totalInterfaces++;
                else totalClasses++;
            });
            cu.findAll(EnumDeclaration.class).forEach(e -> totalEnums++);
            cu.findAll(MethodDeclaration.class).forEach(m -> totalMethods++);

            // Roles, domain, entities, endpoints
            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cd -> {
                String fqn = toFqn(pkg, cd.getNameAsString());

                // Controllers
                if (hasAnno(cd, "RestController")
                        || hasAnno(cd, "Controller")
                        || cd.getNameAsString().endsWith("Controller")) {
                    controllers.add(fqn);
                }

                // Services
                if (hasAnno(cd, "Service")
                        || cd.getNameAsString().endsWith("Service")) {
                    services.add(fqn);
                }

                // Repositories
                if (hasAnno(cd, "Repository")
                        || cd.getNameAsString().endsWith("Repository")) {
                    repositories.add(fqn);
                }

                // Entities
                if (hasAnno(cd, "Entity")) {
                    entities.add(fqn);
                }

                // Domain classes (package hints)
                if (pkg.contains(".domain.") || pkg.endsWith(".domain")
                        || pkg.contains(".model.") || pkg.endsWith(".model")) {
                    domainClasses.add(fqn);
                }

                // Extract endpoints for Spring + JAX-RS
                extractEndpointsForType(pkg, cd);
            });

        } catch (Exception e) {
            System.err.println("Parse error in " + path + ": " + e.getMessage());
        }
    }

    // ------- Endpoint extraction -------

    private static void extractEndpointsForType(String pkg, ClassOrInterfaceDeclaration cd) {
        String className = cd.getNameAsString();
        String fqn = toFqn(pkg, className);

        // Spring class-level @RequestMapping path(s)
        List<String> classSpringPrefixes = new ArrayList<>();
        for (AnnotationExpr a : cd.getAnnotations()) {
            if (isAnno(a, "RequestMapping")) {
                classSpringPrefixes.addAll(extractRequestMappingPaths(a));
            }
        }
        if (classSpringPrefixes.isEmpty()) classSpringPrefixes.add(""); // default to no prefix

        // JAX-RS class-level @Path
        List<String> classJaxPaths = new ArrayList<>();
        for (AnnotationExpr a : cd.getAnnotations()) {
            String p = getJaxRsPath(a);
            if (!p.isEmpty()) classJaxPaths.add(p);
        }
        if (classJaxPaths.isEmpty()) classJaxPaths.add("");

        // Spring methods
        cd.getMethods().forEach(m -> {
            List<SpringMapping> mappings = new ArrayList<>();
            for (AnnotationExpr a : m.getAnnotations()) {
                SpringMapping sm = getSpringMethodAndPath(a);
                if (sm != null) mappings.add(sm);
            }
            for (SpringMapping sm : mappings) {
                List<String> methodPaths = sm.paths.isEmpty()
                        ? List.of("")
                        : sm.paths;
                // combine class prefix x method path
                for (String cp : classSpringPrefixes) {
                    for (String mp : methodPaths) {
                        String full = joinPaths(cp, mp);
                        apiEndpoints.add(fmtEndpoint(sm.http, full, fqn, m.getNameAsString()));
                    }
                }
            }
        });

        // JAX-RS methods
        cd.getMethods().forEach(m -> {
            List<String> httpMethods = new ArrayList<>();
            List<String> methodPaths = new ArrayList<>();
            for (AnnotationExpr a : m.getAnnotations()) {
                String http = getJaxRsHttp(a);
                if (!http.isEmpty()) httpMethods.add(http);
                String p = getJaxRsPath(a);
                if (!p.isEmpty()) methodPaths.add(p);
            }
            if (httpMethods.isEmpty() && methodPaths.isEmpty()) return;

            if (httpMethods.isEmpty()) httpMethods.add(""); // unknown
            if (methodPaths.isEmpty()) methodPaths.add(""); // no extra segment

            for (String cp : classJaxPaths) {
                for (String mp : methodPaths) {
                    String full = joinPaths(cp, mp);
                    for (String http : httpMethods) {
                        apiEndpoints.add(fmtEndpoint(http, full, fqn, m.getNameAsString()));
                    }
                }
            }
        });
    }

    // ------- Helpers -------

    private static String toFqn(String pkg, String simple) {
        return (pkg == null || pkg.isEmpty()) ? simple : pkg + "." + simple;
    }

    private static boolean hasAnno(BodyDeclaration<?> node, String simpleName) {
        for (AnnotationExpr a : node.getAnnotations()) {
            if (isAnno(a, simpleName)) return true;
        }
        return false;
    }

    private static boolean isAnno(AnnotationExpr a, String simple) {
        String name = a.getName().getIdentifier();
        return name.equals(simple) || name.endsWith(simple);
    }

    private static List<String> extractRequestMappingPaths(AnnotationExpr anno) {
        // Handles @RequestMapping("..."), @RequestMapping(path="..."), @RequestMapping(value="..."),
        // and arrays: path={"a","b"}
        List<String> out = new ArrayList<>();
        if (anno instanceof SingleMemberAnnotationExpr sma) {
            out.addAll(exprToStrings(sma.getMemberValue()));
            return out;
        }
        if (anno instanceof NormalAnnotationExpr na) {
            for (MemberValuePair pair : na.getPairs()) {
                String key = pair.getNameAsString();
                if (key.equals("path") || key.equals("value")) {
                    out.addAll(exprToStrings(pair.getValue()));
                }
            }
        }
        return out;
    }

    private static class SpringMapping {
        final String http;       // GET/POST/PUT/DELETE/PATCH or "" if unknown
        final List<String> paths; // may be multiple
        SpringMapping(String http, List<String> paths) {
            this.http = http == null ? "" : http;
            this.paths = paths == null ? List.of("") : paths;
        }
    }

    private static SpringMapping getSpringMethodAndPath(AnnotationExpr anno) {
        // Supports @GetMapping/@PostMapping/... and @RequestMapping(method=..., path/value=..., arrays too)
        String name = anno.getName().getIdentifier();
        String http = switch (name) {
            case "GetMapping" -> "GET";
            case "PostMapping" -> "POST";
            case "PutMapping" -> "PUT";
            case "DeleteMapping" -> "DELETE";
            case "PatchMapping" -> "PATCH";
            case "RequestMapping" -> ""; // http may be in attributes
            default -> null;
        };
        if (http == null && !name.equals("RequestMapping")) return null;

        List<String> paths = new ArrayList<>();

        if (anno instanceof SingleMemberAnnotationExpr sma) {
            paths.addAll(exprToStrings(sma.getMemberValue()));
        } else if (anno instanceof NormalAnnotationExpr na) {
            // collect path/value and method
            String httpFromAttr = "";
            for (MemberValuePair pair : na.getPairs()) {
                String key = pair.getNameAsString();
                if (key.equals("path") || key.equals("value")) {
                    paths.addAll(exprToStrings(pair.getValue()));
                } else if (key.equals("method")) {
                    // RequestMethod.GET / {RequestMethod.GET, RequestMethod.POST}
                    httpFromAttr = extractRequestMethod(pair.getValue());
                }
            }
            if ((http == null || http.isEmpty()) && !httpFromAttr.isEmpty()) {
                http = httpFromAttr;
            }
        }
        if (paths.isEmpty()) paths.add("");
        return new SpringMapping(http == null ? "" : http, paths);
    }

    private static String extractRequestMethod(Expression expr) {
        // Handles single name or array: {RequestMethod.GET, RequestMethod.POST}
        List<String> methods = exprToStrings(expr);
        // Normalize to GET/POST/etc if possible
        for (String m : methods) {
            String upper = m.toUpperCase(Locale.ROOT);
            if (upper.contains("GET")) return "GET";
            if (upper.contains("POST")) return "POST";
            if (upper.contains("PUT")) return "PUT";
            if (upper.contains("DELETE")) return "DELETE";
            if (upper.contains("PATCH")) return "PATCH";
        }
        return "";
    }

    private static List<String> exprToStrings(Expression expr) {
        // Converts a literal or array initializer into a list of strings
        List<String> out = new ArrayList<>();
        if (expr instanceof ArrayInitializerExpr arr) {
            for (Expression e : arr.getValues()) {
                out.add(stripQuotes(e.toString()));
            }
        } else {
            out.add(stripQuotes(expr.toString()));
        }
        return out;
    }

    private static String getJaxRsPath(AnnotationExpr anno) {
        if (!isAnno(anno, "Path")) return "";
        if (anno instanceof SingleMemberAnnotationExpr sma) {
            return stripQuotes(sma.getMemberValue().toString());
        }
        if (anno instanceof NormalAnnotationExpr na) {
            for (MemberValuePair pair : na.getPairs()) {
                if (pair.getNameAsString().equals("value")) {
                    return stripQuotes(pair.getValue().toString());
                }
            }
        }
        return "";
    }

    private static String getJaxRsHttp(AnnotationExpr anno) {
        String n = anno.getName().getIdentifier();
        return switch (n) {
            case "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD" -> n;
            default -> "";
        };
    }

    private static String stripQuotes(String s) {
        if (s == null) return "";
        String t = s.trim();
        if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'"))) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }

    private static String joinPaths(String p1, String p2) {
        if (p1 == null) p1 = "";
        if (p2 == null) p2 = "";
        String a = p1.isEmpty() ? "" : (p1.startsWith("/") ? p1 : "/" + p1);
        String b = p2.isEmpty() ? "" : (p2.startsWith("/") ? p2 : "/" + p2);
        String res = (a + b);
        return res.isEmpty() ? "/" : res.replaceAll("//+", "/");
    }

    private static String fmtEndpoint(String http, String path, String fqn, String method) {
        http = (http == null ? "" : http.trim());
        path = (path == null ? "" : path.trim());
        if (path.isEmpty()) path = "/";
        return String.format("%-6s %-30s -> %s#%s", http, path, fqn, method);
    }

    private static void printSet(String title, Set<String> set) {
        System.out.println(title + ":");
        set.forEach(System.out::println);
        System.out.println();
    }
}
