import de.mel.Lok;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DateTest {
    @TempDir
    File tempDir;

    @Test
    public void date() throws Exception {
        Path path = new File(tempDir, "date.txt").toPath();
        LocalDateTime date = LocalDateTime.now();
        Files.write(path, date.toString().getBytes());
        Lok.debug(date.toString());
        String read = new String(Files.readAllBytes(path));
        Lok.debug(read);
        LocalDateTime readTime = LocalDateTime.parse(read);
        Lok.debug(readTime.toString());
        assertEquals(date, readTime);
    }
}
