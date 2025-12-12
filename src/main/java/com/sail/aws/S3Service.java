package com.sail.aws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class S3Service {

    private final S3Client s3Client;
    private final String region;
    private final String bucketPrefix;

    public S3Service(S3Client s3Client, 
                     @Value("${aws.region}") String region,
                     @Value("${aws.s3.bucket.prefix}") String bucketPrefix) {
        this.s3Client = s3Client;
        this.region = region;
        this.bucketPrefix = bucketPrefix;
    }

    public String createBucket(String bucketName) {
        try {
            boolean exists = bucketExists(bucketName);
            if (!exists) {
                CreateBucketRequest createRequest = CreateBucketRequest.builder()
                        .bucket(bucketName)
                        .build();
                s3Client.createBucket(createRequest);
            }

            disablePublicAccessBlock(bucketName);
            enableStaticWebsiteHosting(bucketName);
            setBucketPublicReadPolicy(bucketName);

            return bucketName;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create/configure S3 bucket: " + e.getMessage(), e);
        }
    }

    private boolean bucketExists(String bucketName) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            return true;
        } catch (NoSuchBucketException e) {
            return false;
        }
    }

    public void uploadDirectory(String bucketName, String directoryPath) {
        try {
            Path dirPath = Paths.get(directoryPath);
            Files.walk(dirPath)
                    .filter(Files::isRegularFile)
                    .forEach(file -> {
                        String key = dirPath.relativize(file).toString().replace("\\", "/");
                        uploadFile(bucketName, key, file.toFile());
                    });
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload directory to S3: " + e.getMessage(), e);
        }
    }
 // inside S3Service
    public void uploadStaticSite(String bucketName, String siteRootPath) {
        try {
            Path root = Paths.get(siteRootPath);

            // 1. Find the main HTML file in the root folder (entry point)
            Path entryHtml = Files.list(root)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".html"))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No HTML file found in " + siteRootPath));

            // 2. Upload entry HTML as ROOT index.html (rename if needed)
            uploadFile(bucketName, "index.html", entryHtml.toFile());

            // 3. Upload sibling CSS and JS files (same folder)
            try (var stream = Files.list(root)) {
                stream.filter(Files::isRegularFile)
                      .filter(p -> {
                          String name = p.getFileName().toString().toLowerCase();
                          return name.endsWith(".css") || name.endsWith(".js");
                      })
                      .forEach(p -> {
                          String key = p.getFileName().toString(); // keep original name
                          uploadFile(bucketName, key, p.toFile());
                      });
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to upload static site to S3: " + e.getMessage(), e);
        }
    }

    public void uploadFile(String bucketName, String key, File file) {
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(getContentType(key))
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromFile(file));
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to S3: " + e.getMessage(), e);
        }
    }

    public String getWebsiteUrl(String bucketName) {
        return String.format("http://%s.s3-website.%s.amazonaws.com", bucketName, region);
    }

    public String generateBucketName() {
        return bucketPrefix + UUID.randomUUID().toString().substring(0, 8);
    }

    private void enableStaticWebsiteHosting(String bucketName) {
        try {
            WebsiteConfiguration websiteConfig = WebsiteConfiguration.builder()
                    .indexDocument(IndexDocument.builder().suffix("index.html").build())
                    .errorDocument(ErrorDocument.builder().key("error.html").build())
                    .build();

            PutBucketWebsiteRequest websiteRequest = PutBucketWebsiteRequest.builder()
                    .bucket(bucketName)
                    .websiteConfiguration(websiteConfig)
                    .build();

            s3Client.putBucketWebsite(websiteRequest);
        } catch (Exception e) {
            // Log error but don't fail
            System.err.println("Warning: Could not enable static website hosting: " + e.getMessage());
        }
    }
    
    private void disablePublicAccessBlock(String bucketName) {
        try {
            PublicAccessBlockConfiguration config = PublicAccessBlockConfiguration.builder()
                    .blockPublicAcls(false)
                    .ignorePublicAcls(false)
                    .blockPublicPolicy(false)
                    .restrictPublicBuckets(false)
                    .build();

            PutPublicAccessBlockRequest request = PutPublicAccessBlockRequest.builder()
                    .bucket(bucketName)
                    .publicAccessBlockConfiguration(config)
                    .build();

            s3Client.putPublicAccessBlock(request);
            System.out.println("Disabled Block Public Access for bucket: " + bucketName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to disable public access block: " + e.getMessage(), e);
        }
    }

    private void setBucketPublicReadPolicy(String bucketName) {
        String policy = """
            {
              "Version": "2012-10-17",
              "Statement": [
                {
                  "Sid": "PublicReadForStaticWebsite",
                  "Effect": "Allow",
                  "Principal": "*",
                  "Action": "s3:GetObject",
                  "Resource": "arn:aws:s3:::%s/*"
                }
              ]
            }
            """.formatted(bucketName);

        PutBucketPolicyRequest policyRequest = PutBucketPolicyRequest.builder()
                .bucket(bucketName)
                .policy(policy)
                .build();

        try {
            s3Client.putBucketPolicy(policyRequest);
            System.out.println("Bucket policy applied for bucket: " + bucketName);
        } catch (S3Exception e) {
            // S3 returned an error (AccessDenied, MalformedPolicy, etc.)
            System.err.println("Failed to set bucket policy for " + bucketName +
                    ": " + e.awsErrorDetails().errorCode() + " - " +
                    e.awsErrorDetails().errorMessage());
            throw new RuntimeException("Could not set public read policy for bucket " + bucketName, e);
        } catch (Exception e) {
            // Any other unexpected error
            System.err.println("Unexpected error while setting bucket policy for " + bucketName +
                    ": " + e.getMessage());
            throw new RuntimeException("Could not set public read policy for bucket " + bucketName, e);
        }
    }
    
    private String getContentType(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        switch (extension) {
            case "html": return "text/html";
            case "css": return "text/css";
            case "js": return "application/javascript";
            case "json": return "application/json";
            case "png": return "image/png";
            case "jpg": case "jpeg": return "image/jpeg";
            case "gif": return "image/gif";
            case "svg": return "image/svg+xml";
            default: return "application/octet-stream";
        }
    }
}

