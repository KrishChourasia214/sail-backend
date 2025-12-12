package com.sail.aws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.apigateway.model.*;

import java.util.HashMap;
import java.util.Map;

@Service
public class ApiGatewayService {

    private final ApiGatewayClient apiGatewayClient;
    private final String region;
    private final String apiNamePrefix;

    public ApiGatewayService(ApiGatewayClient apiGatewayClient,
                             @Value("${aws.region}") String region,
                             @Value("${aws.api.gateway.name.prefix}") String apiNamePrefix) {
        this.apiGatewayClient = apiGatewayClient;
        this.region = region;
        this.apiNamePrefix = apiNamePrefix;
    }

    public String createRestApi(String apiName) {
        try {
            CreateRestApiRequest createRequest = CreateRestApiRequest.builder()
                    .name(apiName)
                    .description("SAIL-generated API for Spring Boot application")
                    .endpointConfiguration(EndpointConfiguration.builder()
                            .types(EndpointType.REGIONAL)
                            .build())
                    .build();

            CreateRestApiResponse response = apiGatewayClient.createRestApi(createRequest);
            return response.id();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create API Gateway: " + e.getMessage(), e);
        }
    }

    public String getApiUrl(String apiId) {
        return String.format("https://%s.execute-api.%s.amazonaws.com/", apiId, region);
    }

    public String generateApiName() {
        return apiNamePrefix + System.currentTimeMillis();
    }
    
    public String setupLambdaProxy(String restApiId, String functionArn, String stageName) {
        // 1. Find the root resource ("/")
        GetResourcesResponse resources = apiGatewayClient.getResources(
                GetResourcesRequest.builder()
                        .restApiId(restApiId)
                        .build()
        );

        String rootId = resources.items().stream()
                .filter(r -> "/".equals(r.path()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Root resource not found"))
                .id();

        // 2. Create resource {proxy+}
        CreateResourceResponse proxyResource = apiGatewayClient.createResource(
                CreateResourceRequest.builder()
                        .restApiId(restApiId)
                        .parentId(rootId)
                        .pathPart("{proxy+}")
                        .build()
        );
        String proxyResourceId = proxyResource.id();

        // 3. Create ANY method on {proxy+}
        String httpMethod = "ANY";
        apiGatewayClient.putMethod(
                PutMethodRequest.builder()
                        .restApiId(restApiId)
                        .resourceId(proxyResourceId)
                        .httpMethod(httpMethod)
                        .authorizationType("NONE")
                        .build()
        );

        // 4. Set Lambda proxy integration
        String uri = String.format("arn:aws:apigateway:%s:lambda:path/2015-03-31/functions/%s/invocations",
                region, functionArn);

        apiGatewayClient.putIntegration(
                PutIntegrationRequest.builder()
                        .restApiId(restApiId)
                        .resourceId(proxyResourceId)
                        .httpMethod(httpMethod)
                        .type(IntegrationType.AWS_PROXY)
                        .integrationHttpMethod("POST")
                        .uri(uri)
                        .build()
        );

        // ========== NEW: ENABLE CORS ==========
        // 5. Enable CORS on {proxy+} resource
        enableCorsOnResource(restApiId, proxyResourceId);
        
        // 6. Enable CORS on root resource (for direct endpoint calls)
        enableCorsOnResource(restApiId, rootId);
        // ======================================

        // 7. Deploy API to a stage (previously step 5)
        apiGatewayClient.createDeployment(
                CreateDeploymentRequest.builder()
                        .restApiId(restApiId)
                        .stageName(stageName)
                        .build()
        );

        // 8. Return stage base URL (previously step 6)
        return String.format("https://%s.execute-api.%s.amazonaws.com/%s",
                restApiId, region, stageName);
    }

    /**
     * NEW METHOD: Enable CORS on a specific API Gateway resource
     * This adds OPTIONS method to handle preflight requests from browsers
     */
    private void enableCorsOnResource(String restApiId, String resourceId) {
        try {
            System.out.println("Enabling CORS on resource: " + resourceId);
            
            // Create OPTIONS method for CORS preflight requests
            apiGatewayClient.putMethod(
                    PutMethodRequest.builder()
                            .restApiId(restApiId)
                            .resourceId(resourceId)
                            .httpMethod("OPTIONS")
                            .authorizationType("NONE")
                            .build()
            );

            // Mock integration for OPTIONS (returns 200 immediately)
            Map<String, String> requestTemplates = new HashMap<>();
            requestTemplates.put("application/json", "{\"statusCode\": 200}");
            
            apiGatewayClient.putIntegration(
                    PutIntegrationRequest.builder()
                            .restApiId(restApiId)
                            .resourceId(resourceId)
                            .httpMethod("OPTIONS")
                            .type(IntegrationType.MOCK)
                            .requestTemplates(requestTemplates)
                            .build()
            );

            // Configure method response for OPTIONS with CORS headers
            Map<String, Boolean> methodResponseParams = new HashMap<>();
            methodResponseParams.put("method.response.header.Access-Control-Allow-Headers", false);
            methodResponseParams.put("method.response.header.Access-Control-Allow-Methods", false);
            methodResponseParams.put("method.response.header.Access-Control-Allow-Origin", false);
            
            apiGatewayClient.putMethodResponse(
                    PutMethodResponseRequest.builder()
                            .restApiId(restApiId)
                            .resourceId(resourceId)
                            .httpMethod("OPTIONS")
                            .statusCode("200")
                            .responseParameters(methodResponseParams)
                            .build()
            );

            // Configure integration response with actual CORS header values
            Map<String, String> integrationResponseParams = new HashMap<>();
            integrationResponseParams.put("method.response.header.Access-Control-Allow-Headers",
                    "'Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token'");
            integrationResponseParams.put("method.response.header.Access-Control-Allow-Methods",
                    "'GET,POST,PUT,DELETE,OPTIONS'");
            integrationResponseParams.put("method.response.header.Access-Control-Allow-Origin",
                    "'*'");
            
            Map<String, String> responseTemplates = new HashMap<>();
            responseTemplates.put("application/json", "");
            
            apiGatewayClient.putIntegrationResponse(
                    PutIntegrationResponseRequest.builder()
                            .restApiId(restApiId)
                            .resourceId(resourceId)
                            .httpMethod("OPTIONS")
                            .statusCode("200")
                            .responseParameters(integrationResponseParams)
                            .responseTemplates(responseTemplates)
                            .build()
            );

            System.out.println("✓ CORS enabled successfully on resource: " + resourceId);

        } catch (ConflictException e) {
            // OPTIONS method already exists, which is fine
            System.out.println("✓ CORS already configured on resource: " + resourceId);
        } catch (Exception e) {
            // Log warning but don't fail the deployment
            System.err.println("⚠ Warning: Could not enable CORS on resource " + resourceId + ": " + e.getMessage());
            // Don't throw exception - CORS is a nice-to-have, shouldn't break deployment
        }
    }

    // Note: Full API Gateway setup with resources, methods, and integration
    // requires multiple steps. This is a simplified version.
    // For production, consider using AWS SAM or CloudFormation.
}