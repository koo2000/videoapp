package com.github.koo2000.sample.videotagspring;

import org.springframework.util.FileCopyUtils;

import java.io.*;
import java.nio.file.Files;
import java.sql.Blob;
import java.sql.SQLException;

public class BlobUtil {

    private static final File TMP_DIRECTORY = new File("target/tmp");

    public static void cleanTmpFiles() {
        File[] files = TMP_DIRECTORY.listFiles(pathname -> pathname.getName().endsWith(".tmp"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            file.delete();
        }
    }

    public static File createTempFileAndCopy
            (Blob blob, long start, long end) {
        File data;
        try {
            if (!TMP_DIRECTORY.exists()) {
                Files.createDirectories(TMP_DIRECTORY.toPath());
            }
            data = File.createTempFile("download", ".tmp", TMP_DIRECTORY);
            data.deleteOnExit();
        } catch (IOException e) {
            throw new RuntimeException("file copy failed", e);
        }

        try (OutputStream o = new FileOutputStream(data)) {
            FileCopyUtils.copy(blob.getBinaryStream(), o);
        } catch (IOException|SQLException e) {
            throw new RuntimeException("file copy failed", e);
        }

        return data;
    }
}
