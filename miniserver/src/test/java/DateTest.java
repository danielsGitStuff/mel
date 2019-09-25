import de.mel.Lok;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

public class DateTest {
    @Test
    public void date() throws Exception{
        File f = new File("date.txt");
        Path path = Paths.get(f.toURI());
        f.delete();
        LocalDateTime date = LocalDateTime.now();
        Files.write(path,date.toString().getBytes());
        Lok.debug(date.toString());
        byte[] bytes = Files.readAllBytes(path);
        String read = new String(bytes);
        Lok.debug(read);
        LocalDateTime readTime = LocalDateTime.parse(read);
        Lok.debug(readTime.toString());
    }
}
