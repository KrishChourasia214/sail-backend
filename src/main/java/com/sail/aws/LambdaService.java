package com.sail.aws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.AddPermissionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.Environment;
import software.amazon.awssdk.services.lambda.model.FunctionCode;
import software.amazon.awssdk.services.lambda.model.ResourceConflictException;
import software.amazon.awssdk.services.lambda.model.Runtime;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeRequest;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeResponse;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionConfigurationRequest;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class LambdaService {

    private final LambdaClient lambdaClient;
    private final String region;
    private final String functionPrefix;
    private final String lambdaExecutionRole;
    private final String accountId;
    private final LambdaCodeStorageService codeStorageService;
    private final LambdaDatabaseConfigurationService dbConfigService;

    public LambdaService(LambdaClient lambdaClient,
                         @Value("${aws.region}") String region,
                         @Value("${aws.lambda.function.prefix}") String functionPrefix,
                         @Value("${aws.lambda.execution.role:}") String lambdaExecutionRole,
                         @Value("${aws.account.id}") String accountId,
                         LambdaCodeStorageService codeStorageService,
                         LambdaDatabaseConfigurationService dbConfigService) {
        this.lambdaClient = lambdaClient;
        this.region = region;
        this.functionPrefix = functionPrefix;
        this.lambdaExecutionRole = lambdaExecutionRole;
        this.accountId = accountId;
        this.codeStorageService = codeStorageService;
        this.dbConfigService = dbConfigService;
    }

    /**
     * BACKWARD COMPATIBLE METHOD - uses H2 by default.
     * This ensures existing code that calls this method without dbType still works.
     */
    public String createFunction(String functionName, File jarFile, String handler) {
        // Default to H2 for backward compatibility
        return createFunction(functionName, jarFile, handler, 
                             LambdaDatabaseConfigurationService.DatabaseType.H2);
    }

    /**
     * NEW METHOD - accepts database type for intelligent configuration.
     * Recommended for new deployments that auto-detect database type.
     */
    public String createFunction(String functionName, File jarFile, String handler, 
                                 LambdaDatabaseConfigurationService.DatabaseType dbType) {
        try {
            // 1. Prepare deployment package (plain JAR)
            File deploymentPackage = createDeploymentPackage(jarFile);

            // 2. Upload to S3 code bucket
            String s3Key = codeStorageService.uploadCodePackage(deploymentPackage, functionName);
            String s3Bucket = codeStorageService.getCodeBucketName();

            // 3. Build FunctionCode using S3 bucket + key
            FunctionCode functionCode = FunctionCode.builder()
                    .s3Bucket(s3Bucket)
                    .s3Key(s3Key)
                    .build();

            System.out.println("Creating Lambda function " + functionName +
                               " with handler=" + handler +
                               " and code from s3://" + s3Bucket + "/" + s3Key);

            // 4. Get database configuration based on detected type
            Map<String, String> environmentVariables = dbConfigService.getFreeTierDatabaseConfig(dbType);
            
            // 5. Add additional Lambda-specific environment variables
            environmentVariables.put("SAIL_DEPLOYMENT_TYPE", "FREE_TIER_STUDENT");
            environmentVariables.put("JAVA_TOOL_OPTIONS", "-XX:+TieredCompilation -XX:TieredStopAtLevel=1");
            
            System.out.println("Database type: " + dbType + " - Environment configured for free tier");

            // 6. Create Lambda function with environment variables
            CreateFunctionResponse response = lambdaClient.createFunction(
                    CreateFunctionRequest.builder()
                            .functionName(functionName)
                            .runtime(Runtime.JAVA17)
                            .role(getLambdaExecutionRole())
                            .handler(handler)
                            .code(functionCode)
                            .timeout(60)  // Increased for Spring Boot startup
                            .memorySize(512)  // Kept low for free tier
                            .environment(Environment.builder()
                                    .variables(environmentVariables)
                                    .build())
                            .build()
            );

            return response.functionArn();

        } catch (ResourceConflictException e) {
            // Function already exists -> update code
            return updateFunctionCode(functionName, jarFile, dbType);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Lambda function: " + e.getMessage(), e);
        }
    }

    /**
     * BACKWARD COMPATIBLE METHOD - updates function code without changing environment.
     */
    public String updateFunctionCode(String functionName, File jarFile) {
        return updateFunctionCode(functionName, jarFile, 
                                 LambdaDatabaseConfigurationService.DatabaseType.H2);
    }

    /**
     * NEW METHOD - updates function code AND environment variables with database config.
     */
    public String updateFunctionCode(String functionName, File jarFile, 
                                    LambdaDatabaseConfigurationService.DatabaseType dbType) {
        try {
            File deploymentPackage = createDeploymentPackage(jarFile);

            String s3Key = codeStorageService.uploadCodePackage(deploymentPackage, functionName);
            String s3Bucket = codeStorageService.getCodeBucketName();

            // Update code
            UpdateFunctionCodeResponse codeResponse = lambdaClient.updateFunctionCode(
                UpdateFunctionCodeRequest.builder()
                    .functionName(functionName)
                    .s3Bucket(s3Bucket)
                    .s3Key(s3Key)
                    .build()
            );

            // Also update environment variables with database configuration
            Map<String, String> environmentVariables = dbConfigService.getFreeTierDatabaseConfig(dbType);
            environmentVariables.put("SAIL_DEPLOYMENT_TYPE", "FREE_TIER_STUDENT");
            environmentVariables.put("JAVA_TOOL_OPTIONS", "-XX:+TieredCompilation -XX:TieredStopAtLevel=1");

            lambdaClient.updateFunctionConfiguration(
                UpdateFunctionConfigurationRequest.builder()
                    .functionName(functionName)
                    .environment(Environment.builder()
                            .variables(environmentVariables)
                            .build())
                    .build()
            );

            return codeResponse.functionArn();
        } catch (Exception e) {
            throw new RuntimeException("Failed to update Lambda function: " + e.getMessage(), e);
        }
    }

    public String generateFunctionName() {
        return functionPrefix + System.currentTimeMillis();
    }

    private File createDeploymentPackage(File jarFile) {
        // For Java Lambda runtime, the JAR itself is already a valid ZIP.
        // No need to wrap it inside another zip file.
        return jarFile;
    }

    private String getLambdaExecutionRole() {
        if (lambdaExecutionRole != null && !lambdaExecutionRole.isEmpty()) {
            return lambdaExecutionRole;
        }
        // Default role name - user should configure this
        throw new IllegalStateException("AWS Lambda execution role must be configured via aws.lambda.execution.role property");
    }

    public void addInvokePermissionForApi(String functionArn, String restApiId, String region) {
        String sourceArn = String.format("arn:aws:execute-api:%s:%s:%s/*/*/*",
                region, accountId, restApiId);

        lambdaClient.addPermission(AddPermissionRequest.builder()
                .functionName(functionArn) // ARN or name
                .statementId(UUID.randomUUID().toString())
                .action("lambda:InvokeFunction")
                .principal("apigateway.amazonaws.com")
                .sourceArn(sourceArn)
                .build());
    }
}