package com.ontheway.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Utils {

    /**
     * Reads the entire contents of a file and returns it as a String.
     * (should be used for small files).
     *
     * @param filename the absolute path to the file to be read
     * @return entire contents of filename
     * @throws java.nio.file.NoSuchFileException if file is not found
     * @throws IOException if file read fails.
     */
    public static String readAll(String filename) throws IOException {
        return new String(Files.readAllBytes(Paths.get(filename)));
    }

}
