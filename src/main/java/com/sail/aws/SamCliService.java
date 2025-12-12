package com.sail.aws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
public class SamCliService {

    private final String samCliPath;
    private final String buildDir;
    private final String stackName;

    public SamCliService(@Value("${sam.cli.path}") String samCliPath,
                         @Value("${sam.build.dir}") String buildDir,
                         @Value("${sam.deploy.stack.name}") String stackName) {
        this.samCliPath = samCliPath;
        this.buildDir = buildDir;
        this.stackName = stackName;
    }

    public void buildSamApplication(String templatePath, String baseDir) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(samCliPath);
        command.add("build");
        command.add("--template");
        command.add(templatePath);
        command.add("--build-dir");
        command.add(buildDir);
        command.add("--base-dir");
        command.add(baseDir);

        executeCommand(command, baseDir);
    }

    public void deploySamApplication(String templatePath, String stackNameOverride) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(samCliPath);
        command.add("deploy");
        command.add("--template");
        command.add(templatePath);
        command.add("--stack-name");
        command.add(stackNameOverride != null ? stackNameOverride : stackName);
        command.add("--capabilities");
        command.add("CAPABILITY_IAM");
        command.add("--no-confirm-changeset");
        command.add("--no-fail-on-empty-changeset");

        executeCommand(command, null);
    }

    public String generateSamTemplate(String functionName, String handler, String codeUri) {
        return String.format(
            "AWSTemplateFormatVersion: '2010-09-09'\n" +
            "Transform: AWS::Serverless-2016-10-31\n" +
            "Description: SAIL-generated SAM template\n\n" +
            "Resources:\n" +
            "  %s:\n" +
            "    Type: AWS::Serverless::Function\n" +
            "    Properties:\n" +
            "      CodeUri: %s\n" +
            "      Handler: %s\n" +
            "      Runtime: java17\n" +
            "      Timeout: 30\n" +
            "      MemorySize: 512\n" +
            "      Events:\n" +
            "        ApiEvent:\n" +
            "          Type: Api\n" +
            "          Properties:\n" +
            "            Path: /{proxy+}\n" +
            "            Method: ANY\n",
            functionName, codeUri, handler
        );
    }

    private void executeCommand(List<String> command, String workingDir) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        
        if (workingDir != null) {
            processBuilder.directory(new File(workingDir));
        }
        
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        // Read output
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("SAM CLI command failed with exit code: " + exitCode);
        }
    }
}

