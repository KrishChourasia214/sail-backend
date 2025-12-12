package com.sail.utils;

import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class EndpointScanner {

    private static final Pattern GET_MAPPING =
            Pattern.compile("@GetMapping\\s*\\([^)]*[\"']([^\"']+)[\"']");
    private static final Pattern POST_MAPPING =
            Pattern.compile("@PostMapping\\s*\\([^)]*[\"']([^\"']+)[\"']");
    private static final Pattern PUT_MAPPING =
            Pattern.compile("@PutMapping\\s*\\([^)]*[\"']([^\"']+)[\"']");
    private static final Pattern DELETE_MAPPING =
            Pattern.compile("@DeleteMapping\\s*\\([^)]*[\"']([^\"']+)[\"']");
    private static final Pattern REQUEST_MAPPING =
            Pattern.compile("@RequestMapping\\s*\\([^)]*[\"']([^\"']+)[\"']");
    private static final Pattern REST_CONTROLLER = Pattern.compile("@RestController");
    private static final Pattern CONTROLLER = Pattern.compile("@Controller");

    // ===== Public API =====

    public List<String> scanEndpoints(String projectPath) throws IOException {
        List<String> endpoints = new ArrayList<>();
        Path javaPath = Paths.get(projectPath, "src/main/java");

        if (!Files.exists(javaPath)) {
            return endpoints;
        }

        Files.walk(javaPath)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(p -> {
                    try {
                        String content = Files.readString(p);
                        if (isRestController(content)) {
                            endpoints.addAll(extractEndpoints(content));
                        }
                    } catch (IOException e) {
                        // Skip file if can't read
                    }
                });

        // Remove duplicates and sort (optional, but nicer)
        return endpoints.stream().distinct().sorted().toList();
    }

    public String findMainClass(String projectPath) throws IOException {
        Path javaPath = Paths.get(projectPath, "src/main/java");

        if (!Files.exists(javaPath)) {
            return null;
        }

        return Files.walk(javaPath)
                .filter(p -> p.toString().endsWith("Application.java"))
                .map(p -> {
                    try {
                        String content = Files.readString(p);
                        if (content.contains("@SpringBootApplication") ||
                            content.contains("SpringApplication.run")) {
                            return convertPathToClassName(p, javaPath);
                        }
                    } catch (IOException e) {
                        // Skip
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    // ===== Internal helpers =====

    private boolean isRestController(String content) {
        return REST_CONTROLLER.matcher(content).find() ||
               (CONTROLLER.matcher(content).find() &&
                (content.contains("@ResponseBody") || content.contains("@RestController")));
    }

    /**
     * Extract endpoints from a single controller file.
     * We scan line-by-line to avoid regex crossing into other annotations/methods.
     * We also combine class-level @RequestMapping("/base") with method-level mappings.
     */
    private List<String> extractEndpoints(String content) {
        List<String> endpoints = new ArrayList<>();

        String[] lines = content.split("\\R"); // split on any line break
        String classBase = null;              // e.g. "/tasks" or "/api/scan"

        for (String line : lines) {
            String trimmed = line.trim();

            // Class-level @RequestMapping("/base") – use first one as base
            Matcher classRm = REQUEST_MAPPING.matcher(trimmed);
            if (classBase == null && classRm.find() && looksLikeClassLevel(trimmed)) {
                classBase = classRm.group(1);
                // You can optionally add the base itself as an endpoint
                endpoints.add(normalizePath(classBase, ""));
                continue;
            }

            // Method-level mappings
            String methodPath = null;

            Matcher gm = GET_MAPPING.matcher(trimmed);
            if (gm.find()) {
                methodPath = gm.group(1);
            }

            Matcher pm = POST_MAPPING.matcher(trimmed);
            if (pm.find()) {
                methodPath = pm.group(1);
            }

            Matcher putm = PUT_MAPPING.matcher(trimmed);
            if (putm.find()) {
                methodPath = putm.group(1);
            }

            Matcher dm = DELETE_MAPPING.matcher(trimmed);
            if (dm.find()) {
                methodPath = dm.group(1);
            }

            // Method-level @RequestMapping("/something")
            // Second or later RequestMapping in file treated as method-level
            Matcher methodRm = REQUEST_MAPPING.matcher(trimmed);
            if (methodPath == null && classBase != null && methodRm.find() && !looksLikeClassLevel(trimmed)) {
                methodPath = methodRm.group(1);
            }

            if (methodPath != null) {
                String fullPath = normalizePath(classBase, methodPath);
                endpoints.add(fullPath);
            }
        }

        return endpoints;
    }

    /**
     * Heuristic: treat @RequestMapping lines before 'class' as class-level.
     */
    private boolean looksLikeClassLevel(String line) {
        // Very simple heuristic; good enough for typical controllers
        return !line.contains("public") && !line.contains("private") && !line.contains("protected");
    }

    /**
     * Combine class-level base path and method path into a single endpoint string.
     */
    private String normalizePath(String classBase, String methodPath) {
        String cb = (classBase == null) ? "" : classBase.trim();
        String mp = (methodPath == null) ? "" : methodPath.trim();

        if (!cb.isEmpty() && !cb.startsWith("/")) {
            cb = "/" + cb;
        }
        if (!mp.isEmpty() && !mp.startsWith("/")) {
            mp = "/" + mp;
        }

        String combined;
        if (!cb.isEmpty() && !mp.isEmpty()) {
            combined = cb + mp;
        } else if (!cb.isEmpty()) {
            combined = cb;
        } else if (!mp.isEmpty()) {
            combined = mp;
        } else {
            combined = "/";
        }

        // Collapse multiple slashes and return
        return combined.replaceAll("/{2,}", "/");
    }

    private String convertPathToClassName(Path filePath, Path basePath) {
        String relativePath = basePath.relativize(filePath).toString();
        return relativePath.replace(File.separator, ".")
                           .replace(".java", "");
    }
}