package com.sail.utils;

import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

@Component
public class FileUtils {

    public double getFileSizeMB(File file) {
        return file.length() / (1024.0 * 1024.0);
    }

    public void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (Stream<Path> paths = Files.walk(directory)) {
                paths.sorted((a, b) -> -a.compareTo(b))
                     .forEach(path -> {
                         try {
                             Files.delete(path);
                         } catch (IOException e) {
                             // Log error but continue
                         }
                     });
            }
        }
    }

    public void createDirectories(Path path) throws IOException {
        Files.createDirectories(path);
    }

    public boolean fileExists(Path path) {
        return Files.exists(path);
    }

    public String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1).toLowerCase() : "";
    }
}

