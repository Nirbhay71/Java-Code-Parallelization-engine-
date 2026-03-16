package com.parallelizer.output;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Utility to write the generated code to a file or print to console.
 */
public class OutputWriter {

    public void writeFile(String filePath, String content) throws IOException {
        Files.write(Paths.get(filePath), content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public void writeToConsole(String content) {
        System.out.println("\n--- GENERATED MULTITHREADED CODE ---\n");
        System.out.println(content);
        System.out.println("\n------------------------------------\n");
    }
}
