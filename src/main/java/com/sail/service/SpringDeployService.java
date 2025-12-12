package com.sail.service;

import com.sail.aws.ApiGatewayService;
import com.sail.aws.LambdaService;
import com.sail.aws.LambdaDatabaseConfigurationService;
import com.sail.aws.LambdaDatabaseConfigurationService.DatabaseType;
import com.sail.aws.SamCliService;
import com.sail.dto.DeployResult;
import com.sail.model.ProjectInfo;
import com.sail.repository.ProjectInfoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;

@Service
public class SpringDeployService {

    private final LambdaService lambdaService;
    private final ApiGatewayService apiGatewayService;
    private final SamCliService samCliService;
    private final ProjectInfoRepository projectInfoRepository;
    private final LambdaAdapterService lambdaAdapterService;
    private final LambdaDatabaseConfigurationService dbConfigService;
    private final String region;
    private final String buildDir;

    public SpringDeployService(LambdaService lambdaService,
                               ApiGatewayService apiGatewayService,
                               SamCliService samCliService,
                               ProjectInfoRepository projectInfoRepository,
                               LambdaAdapterService lambdaAdapterService,
                               LambdaDatabaseConfigurationService dbConfigService,
                               @Value("${aws.region}") String region,
                               @Value("${sail.temp.build.dir}") String buildDir) {
        this.lambdaService = lambdaService;
        this.apiGatewayService = apiGatewayService;
        this.samCliService = samCliService;
        this.projectInfoRepository = projectInfoRepository;
        this.lambdaAdapterService = lambdaAdapterService;
        this.dbConfigService = dbConfigService;
        this.region = region;
        this.buildDir = buildDir;
    }

    public DeployResult deploySpringBoot(String projectId) {
        DeployResult result = new DeployResult();
        result.setDeploymentType("SPRINGBOOT");

        try {
            ProjectInfo projectInfo = projectInfoRepository.findById(projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

            if (!"SPRINGBOOT".equals(projectInfo.getProjectType())) {
                throw new RuntimeException("Project is not a Spring Boot application");
            }

            String projectPath = projectInfo.getExtractedPath();

            // Step 0: Detect database type from project
            DatabaseType dbType = detectDatabaseType(projectPath);
            System.out.println("Detected database type: " + dbType);

            // Step 1: Prepare project for Lambda (inject deps + handler)
            String handlerFqn = lambdaAdapterService.prepareProjectForLambda(projectPath);
            System.out.println("Using Lambda handler: " + handlerFqn);

            // Step 2: Build the project (now Lambda-ready)
            File jarFile = buildProject(projectPath);

            // Step 3: Generate function name
            String functionName = lambdaService.generateFunctionName();

            // Step 4: Create Lambda function with intelligent database configuration
            String functionArn = lambdaService.createFunction(functionName, jarFile, handlerFqn, dbType);

            // Step 5: Create API Gateway
            String apiName = apiGatewayService.generateApiName();
            String apiId = apiGatewayService.createRestApi(apiName);

            // Step 6: Allow API Gateway to invoke Lambda
            lambdaService.addInvokePermissionForApi(functionArn, apiId, region);

            // Step 7: Create proxy resource + ANY method + Lambda proxy integration + deployment + stage
            String stageName = "prod";
            String apiUrl = apiGatewayService.setupLambdaProxy(apiId, functionArn, stageName);

            // Update project status
            projectInfo.setStatus("DEPLOYED");
            projectInfoRepository.save(projectInfo);

            // Fill result
            result.setLambdaName(functionName);
            result.setApiUrl(apiUrl);
            result.setRegion(region);
            result.setStatus("SUCCESS");
            return result;

        } catch (Exception e) {
            result.setStatus("FAILED");
            result.setErrorMessage(e.getMessage());
            return result;
        }
    }

    /**
     * Detects database type from application.properties and pom.xml
     */
    private DatabaseType detectDatabaseType(String projectPath) {
        try {
            // First, check application.properties
            Path propsPath = Paths.get(projectPath, "src", "main", "resources", "application.properties");
            
            if (Files.exists(propsPath)) {
                Properties props = new Properties();
                try (FileInputStream fis = new FileInputStream(propsPath.toFile())) {
                    props.load(fis);
                }
                
                String datasourceUrl = props.getProperty("spring.datasource.url");
                if (datasourceUrl != null) {
                    datasourceUrl = datasourceUrl.toLowerCase();
                    
                    if (datasourceUrl.contains("h2:")) {
                        System.out.println("Detected H2 database from datasource URL");
                        return DatabaseType.H2;
                    } else if (datasourceUrl.contains("mysql:")) {
                        System.out.println("Detected MySQL database from datasource URL");
                        return DatabaseType.MYSQL;
                    } else if (datasourceUrl.contains("postgresql:")) {
                        System.out.println("Detected PostgreSQL database from datasource URL");
                        return DatabaseType.POSTGRESQL;
                    } else if (datasourceUrl.contains("mongodb:")) {
                        System.out.println("Detected MongoDB from datasource URL");
                        return DatabaseType.MONGODB;
                    } else if (datasourceUrl.contains("mariadb:")) {
                        System.out.println("Detected MariaDB database from datasource URL");
                        return DatabaseType.MARIADB;
                    } else if (datasourceUrl.contains("oracle:")) {
                        System.out.println("Detected Oracle database from datasource URL");
                        return DatabaseType.ORACLE;
                    } else if (datasourceUrl.contains("sqlserver:")) {
                        System.out.println("Detected SQL Server from datasource URL");
                        return DatabaseType.SQLSERVER;
                    }
                }
            }
            
            // Fallback: check pom.xml dependencies
            System.out.println("No datasource URL found, checking pom.xml dependencies");
            return detectFromPomDependencies(projectPath);
            
        } catch (Exception e) {
            System.out.println("Could not detect database type, defaulting to H2: " + e.getMessage());
            return DatabaseType.H2;
        }
    }
    
    /**
     * Detects database type from pom.xml dependencies as fallback
     */
    private DatabaseType detectFromPomDependencies(String projectPath) {
        try {
            Path pomPath = Paths.get(projectPath, "pom.xml");
            if (!Files.exists(pomPath)) {
                System.out.println("No pom.xml found, defaulting to NONE");
                return DatabaseType.NONE;
            }
            
            String pomContent = Files.readString(pomPath).toLowerCase();
            
            if (pomContent.contains("<artifactid>h2</artifactid>")) {
                System.out.println("Detected H2 from pom.xml dependency");
                return DatabaseType.H2;
            } else if (pomContent.contains("<artifactid>mysql-connector") || 
                       pomContent.contains("<artifactid>mysql</artifactid>")) {
                System.out.println("Detected MySQL from pom.xml dependency");
                return DatabaseType.MYSQL;
            } else if (pomContent.contains("<artifactid>postgresql</artifactid>")) {
                System.out.println("Detected PostgreSQL from pom.xml dependency");
                return DatabaseType.POSTGRESQL;
            } else if (pomContent.contains("<artifactid>mongodb") || 
                       pomContent.contains("spring-boot-starter-data-mongodb")) {
                System.out.println("Detected MongoDB from pom.xml dependency");
                return DatabaseType.MONGODB;
            } else if (pomContent.contains("<artifactid>mariadb")) {
                System.out.println("Detected MariaDB from pom.xml dependency");
                return DatabaseType.MARIADB;
            } else if (pomContent.contains("<artifactid>ojdbc") || 
                       pomContent.contains("oracle")) {
                System.out.println("Detected Oracle from pom.xml dependency");
                return DatabaseType.ORACLE;
            } else if (pomContent.contains("mssql-jdbc") || 
                       pomContent.contains("sqlserver")) {
                System.out.println("Detected SQL Server from pom.xml dependency");
                return DatabaseType.SQLSERVER;
            }
            
            System.out.println("No database dependency detected in pom.xml");
            return DatabaseType.NONE;
            
        } catch (Exception e) {
            System.out.println("Error reading pom.xml: " + e.getMessage());
            return DatabaseType.H2;
        }
    }

    // ---------- Build Helpers ----------

    private void runMavenBuild(Path projectDir) throws IOException, InterruptedException {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

        // Detect Maven Wrapper
        Path wrapperScript = isWindows
                ? projectDir.resolve("mvnw.cmd")
                : projectDir.resolve("mvnw");

        Path wrapperProps = projectDir.resolve(".mvn")
                .resolve("wrapper")
                .resolve("maven-wrapper.properties");

        boolean hasWrapper = Files.exists(wrapperScript) && Files.exists(wrapperProps);

        ProcessBuilder pb;

        if (hasWrapper) {
            System.out.println("Using Maven Wrapper in: " + projectDir.toAbsolutePath());
            if (isWindows) {
                pb = new ProcessBuilder("cmd.exe", "/c", "mvnw.cmd", "clean", "package", "-DskipTests");
            } else {
                pb = new ProcessBuilder("./mvnw", "clean", "package", "-DskipTests");
            }
        } else {
            System.out.println("Maven Wrapper not found or incomplete at " + projectDir.toAbsolutePath()
                    + " - falling back to global mvn");
            String mvnCmd = isWindows ? "mvn.cmd" : "mvn";
            pb = new ProcessBuilder(mvnCmd, "clean", "package", "-DskipTests");
        }

        pb.directory(projectDir.toFile());
        pb.inheritIO();  // see Maven output in console

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("Maven build failed with exit code " + exitCode);
        }
    }

    private File buildProject(String projectPath) throws IOException, InterruptedException {
        Path projectDir = Paths.get(projectPath);

        System.out.println("Running Maven build in: " + projectDir.toAbsolutePath());
        runMavenBuild(projectDir);

        Path targetDir = projectDir.resolve("target");
        if (!Files.exists(targetDir) || !Files.isDirectory(targetDir)) {
            throw new RuntimeException("target directory not found after build: " + targetDir);
        }

        return Files.walk(targetDir)
                .filter(p -> p.toString().endsWith(".jar") && !p.toString().contains("original"))
                .findFirst()
                .map(Path::toFile)
                .orElseThrow(() ->
                        new RuntimeException("JAR file not found after build in " + targetDir));
    }
}