package com.sail.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Stream;

/**
 * Prepares an uploaded Spring Boot project to run on AWS Lambda by:
 *  - finding the @SpringBootApplication main class
 *  - injecting aws-serverless-java-container + lambda-core deps into pom.xml
 *  - creating a StreamLambdaHandler in the same package
 *  - injecting a global CORS configuration class (SailCorsConfig) into the project
 *
 * Returns the fully qualified handler string to use in Lambda:
 *   e.g. "com.example.taskmanager.StreamLambdaHandler::handleRequest"
 */
@Service
public class LambdaAdapterService {

    public String prepareProjectForLambda(String projectRootPath) throws IOException {
        Path projectRoot = Paths.get(projectRootPath);

        // 1. Find main @SpringBootApplication class
        Path mainAppPath = findSpringBootApplicationClass(projectRoot);
        if (mainAppPath == null) {
            throw new RuntimeException("@SpringBootApplication class not found under " + projectRoot);
        }

        String packageName = extractPackageName(mainAppPath);
        String mainClassSimpleName = mainAppPath.getFileName().toString().replace(".java", "");
        String handlerClassSimpleName = "StreamLambdaHandler";

        // 2. Inject dependencies into pom.xml
        Path pomPath = projectRoot.resolve("pom.xml");
        if (!Files.exists(pomPath)) {
            throw new RuntimeException("pom.xml not found at " + pomPath);
        }
        updatePomWithLambdaDependencies(pomPath);

        // 3. Create handler class in same package
        createHandlerClass(projectRoot, packageName, mainClassSimpleName, handlerClassSimpleName);

        // 4. Inject global CORS configuration (SailCorsConfig) into the project
        injectCorsConfiguration(projectRoot, packageName);

        // 5. Return handler FQN
        String handlerFqn = packageName + "." + handlerClassSimpleName + "::handleRequest";
        System.out.println("Prepared Lambda handler: " + handlerFqn);
        return handlerFqn;
    }

    // ---------- Helpers ----------

    private Path findSpringBootApplicationClass(Path projectRoot) throws IOException {
        Path srcMainJava = projectRoot.resolve("src/main/java");
        if (!Files.exists(srcMainJava)) {
            return null;
        }

        try (Stream<Path> paths = Files.walk(srcMainJava)) {
            return paths
                    .filter(p -> p.getFileName().toString().endsWith("Application.java"))
                    .filter(Files::isRegularFile)
                    .filter(this::fileContainsSpringBootApplication)
                    .findFirst()
                    .orElse(null);
        }
    }

    private boolean fileContainsSpringBootApplication(Path javaFile) {
        try {
            String content = Files.readString(javaFile);
            return content.contains("@SpringBootApplication");
        } catch (IOException e) {
            return false;
        }
    }

    private String extractPackageName(Path javaFile) throws IOException {
        List<String> lines = Files.readAllLines(javaFile);
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("package ")) {
                // e.g. "package com.example.taskmanager;"
                line = line.substring("package ".length()).trim();
                if (line.endsWith(";")) {
                    line = line.substring(0, line.length() - 1);
                }
                return line; // com.example.taskmanager
            }
        }
        throw new RuntimeException("Could not find package declaration in " + javaFile);
    }

    private void updatePomWithLambdaDependencies(Path pomPath) throws IOException {
        String pom = Files.readString(pomPath, StandardCharsets.UTF_8);

        boolean changed = false;

        // 1. Add Lambda/container dependencies if not present
        if (!pom.contains("aws-serverless-java-container-springboot3")) {
            String depsToInsert = """
                    
                    <!-- Added by SAIL for AWS Lambda + API Gateway support -->
                    <dependency>
                        <groupId>com.amazonaws.serverless</groupId>
                        <artifactId>aws-serverless-java-container-springboot3</artifactId>
                        <version>1.9.1</version>
                    </dependency>
                    <dependency>
                        <groupId>com.amazonaws</groupId>
                        <artifactId>aws-lambda-java-core</artifactId>
                        <version>1.2.3</version>
                    </dependency>
                    """;

            String depsMarker = "</dependencies>";
            int depsIdx = pom.indexOf(depsMarker);
            if (depsIdx == -1) {
                throw new RuntimeException("Could not find </dependencies> in pom.xml");
            }

            pom = pom.substring(0, depsIdx) + depsToInsert + "\n" + pom.substring(depsIdx);
            changed = true;
            System.out.println("Injected Lambda dependencies into pom.xml");
        }

        // 2. Ensure Spring Boot fat JAR repackage is skipped (we will use Shade)
        if (!pom.contains("<skip>true</skip>")) {
            String bootSkipSnippet = """
                    
                    <!-- Added by SAIL to skip Spring Boot fat jar repackage for Lambda -->
                    <plugin>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-maven-plugin</artifactId>
                        <configuration>
                            <skip>true</skip>
                        </configuration>
                    </plugin>
                    """;

            String pluginsMarker = "</plugins>";
            int pluginsIdx = pom.indexOf(pluginsMarker);
            if (pluginsIdx != -1) {
                pom = pom.substring(0, pluginsIdx) + bootSkipSnippet + "\n" + pom.substring(pluginsIdx);
                changed = true;
                System.out.println("Injected spring-boot-maven-plugin skip configuration into pom.xml");
            } else {
                System.out.println("Warning: Could not find </plugins> to insert spring-boot-maven-plugin skip config");
            }
        }

        // 3. Ensure Maven Shade Plugin is present to build an uber-jar
        if (!pom.contains("maven-shade-plugin")) {
            String shadePluginSnippet = """
                    
                    <!-- Added by SAIL: build uber-jar with all dependencies for Lambda -->
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-shade-plugin</artifactId>
                        <version>3.5.0</version>
                        <executions>
                            <execution>
                                <phase>package</phase>
                                <goals>
                                    <goal>shade</goal>
                                </goals>
                                <configuration>
                                    <createDependencyReducedPom>false</createDependencyReducedPom>
                                </configuration>
                            </execution>
                        </executions>
                    </plugin>
                    """;

            String pluginsMarker = "</plugins>";
            int pluginsIdx = pom.indexOf(pluginsMarker);
            if (pluginsIdx != -1) {
                pom = pom.substring(0, pluginsIdx) + shadePluginSnippet + "\n" + pom.substring(pluginsIdx);
                changed = true;
                System.out.println("Injected maven-shade-plugin configuration into pom.xml");
            } else {
                System.out.println("Warning: Could not find </plugins> to insert shade plugin config");
            }
        }

        if (changed) {
            Files.writeString(pomPath, pom, StandardCharsets.UTF_8);
            System.out.println("Updated pom.xml at " + pomPath);
        } else {
            System.out.println("pom.xml already contains Lambda deps and shade config, no changes made");
        }
    }

    private void createHandlerClass(Path projectRoot,
                                    String packageName,
                                    String mainClassSimpleName,
                                    String handlerClassSimpleName) throws IOException {

        String packagePath = packageName.replace('.', '/');
        Path handlerDir = projectRoot.resolve("src/main/java").resolve(packagePath);
        Files.createDirectories(handlerDir);

        Path handlerFile = handlerDir.resolve(handlerClassSimpleName + ".java");
        if (Files.exists(handlerFile)) {
            System.out.println("Handler class already exists: " + handlerFile);
            return;
        }

        String handlerSource = """
                package %s;

                import com.amazonaws.serverless.exceptions.ContainerInitializationException;
                import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
                import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
                import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler;
                import com.amazonaws.services.lambda.runtime.Context;
                import com.amazonaws.services.lambda.runtime.RequestStreamHandler;

                import java.io.IOException;
                import java.io.InputStream;
                import java.io.OutputStream;

                /**
                 * Automatically generated by SAIL.
                 * Boots the Spring Boot application inside AWS Lambda and
                 * forwards API Gateway proxy events to Spring MVC controllers.
                 */
                public class %s implements RequestStreamHandler {

                    private static final SpringBootLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse> handler;

                    static {
                        try {
                            handler = SpringBootLambdaContainerHandler.getAwsProxyHandler(%s.class);
                        } catch (ContainerInitializationException e) {
                            throw new RuntimeException("Could not initialize Spring Boot application", e);
                        }
                    }

                    @Override
                    public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
                        handler.proxyStream(input, output, context);
                    }
                }
                """.formatted(packageName, handlerClassSimpleName, mainClassSimpleName);

        Files.writeString(handlerFile, handlerSource, StandardCharsets.UTF_8);
        System.out.println("Created Lambda handler class at " + handlerFile);
    }

    /**
     * Inject a global CORS configuration class into the user's Spring Boot project.
     * This makes all responses include Access-Control-Allow-Origin, etc.,
     * without the user having to modify their code.
     */
    private void injectCorsConfiguration(Path projectRoot, String basePackage) {
        try {
            Path srcMainJava = projectRoot.resolve("src/main/java");
            if (!Files.exists(srcMainJava)) {
                System.out.println("CORS injection: src/main/java not found in " + projectRoot);
                return;
            }

            String configPackage = basePackage + ".config";
            String configPackagePath = configPackage.replace('.', '/');

            Path configDir = srcMainJava.resolve(configPackagePath);
            Files.createDirectories(configDir);

            Path configFile = configDir.resolve("SailCorsConfig.java");
            if (Files.exists(configFile)) {
                System.out.println("CORS injection: SailCorsConfig already exists at " + configFile);
                return;
            }

            String source = ""
                    + "package " + configPackage + ";\n\n"
                    + "import org.springframework.context.annotation.Configuration;\n"
                    + "import org.springframework.web.servlet.config.annotation.CorsRegistry;\n"
                    + "import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;\n\n"
                    + "@Configuration\n"
                    + "public class SailCorsConfig implements WebMvcConfigurer {\n"
                    + "    @Override\n"
                    + "    public void addCorsMappings(CorsRegistry registry) {\n"
                    + "        registry.addMapping(\"/**\")\n"
                    + "                .allowedOrigins(\"*\")\n"
                    + "                .allowedMethods(\"GET\", \"POST\", \"PUT\", \"DELETE\", \"OPTIONS\")\n"
                    + "                .allowedHeaders(\"*\")\n"
                    + "                .allowCredentials(false)\n"
                    + "                .maxAge(3600);\n"
                    + "    }\n"
                    + "}\n";

            Files.writeString(configFile, source, StandardCharsets.UTF_8);
            System.out.println("CORS injection: Created SailCorsConfig at " + configFile);

        } catch (Exception e) {
            System.out.println("CORS injection: Failed - " + e.getMessage());
            e.printStackTrace();
        }
    }
}