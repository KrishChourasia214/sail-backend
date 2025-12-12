package com.sail.utils;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class ZipExtractor {

    public String extractZip(InputStream zipInputStream, String extractToPath) throws IOException {
        Path extractPath = Paths.get(extractToPath);
        Files.createDirectories(extractPath);

        try (ZipArchiveInputStream zipInput = new ZipArchiveInputStream(zipInputStream)) {
            ZipArchiveEntry entry;
            while ((entry = zipInput.getNextZipEntry()) != null) {
                Path entryPath = extractPath.resolve(entry.getName());
                
                // Security: Prevent zip slip vulnerability
                if (!entryPath.normalize().startsWith(extractPath.normalize())) {
                    throw new IOException("Invalid entry path: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    try (OutputStream outputStream = Files.newOutputStream(entryPath)) {
                        zipInput.transferTo(outputStream);
                    }
                }
            }
        }

        return extractPath.toString();
    }

    public String extractZip(File zipFile, String extractToPath) throws IOException {
        try (FileInputStream fis = new FileInputStream(zipFile)) {
            return extractZip(fis, extractToPath);
        }
    }
}

