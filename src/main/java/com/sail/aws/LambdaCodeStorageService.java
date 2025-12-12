package com.sail.aws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;

/**
 * Stores Lambda code packages (JARs) in S3 for use with CreateFunction/UpdateFunction.
 */
@Service
public class LambdaCodeStorageService {

    private final S3Client s3Client;
    private final String codeBucketName;

    public LambdaCodeStorageService(S3Client s3Client,
                                    @Value("${aws.lambda.code.bucket}") String codeBucketName) {
        this.s3Client = s3Client;
        this.codeBucketName = codeBucketName;

        ensureBucketExists();
    }

    private void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder()
                    .bucket(codeBucketName)
                    .build());
            System.out.println("Lambda code bucket exists: " + codeBucketName);
        } catch (NoSuchBucketException e) {
            System.out.println("Creating Lambda code bucket: " + codeBucketName);
            s3Client.createBucket(CreateBucketRequest.builder()
                    .bucket(codeBucketName)
                    .build());
        }
    }

    /**
     * Uploads the given file to S3 under a key based on the functionName.
     * Returns the S3 key.
     */
    public String uploadCodePackage(File file, String functionName) {
        String key = "functions/" + functionName + "/" + file.getName();
        System.out.println("Uploading Lambda code to s3://" + codeBucketName + "/" + key);

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(codeBucketName)
                        .key(key)
                        .build(),
                RequestBody.fromFile(file)
        );

        return key;
    }

    public String getCodeBucketName() {
        return codeBucketName;
    }
}