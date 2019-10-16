package de.mel.auth.tools;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

public class F {
    public static void rmRf(File f) {
        N.forEach(f.listFiles(File::isFile), File::delete);
        N.forEach(f.listFiles(File::isDirectory), F::rmRf);
        f.delete();
    }

    public static String readResourceToString(String resourcePath) throws IOException {
        InputStream in = F.class.getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IOException("could not read resource: " + resourcePath);
        }
        Scanner s = new Scanner(in).useDelimiter("\\A");
        if (s.hasNext())
            return s.next();
        throw new IOException("could not read resource: " + resourcePath);
    }

    @NotNull
    public static String readMimeType(@NotNull File file) throws IOException {
        String  mime = Files.probeContentType(Path.of(file.toURI()));
        return mime;
    }
}
