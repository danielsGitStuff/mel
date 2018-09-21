package de.mein.auth.tools;

import java.io.InputStream;
import java.net.URL;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

import de.mein.Lok;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.sql.RWLock;

/**
 * Created by xor on 2/26/16.
 */
public class Timor {
    private RWLock lock = new RWLock();
    private Timer timer;

    public Timor() {

    }

    public void start(long milliseconds) {
        timer = new Timer();
        lock.lockWrite();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                lock.unlockWrite();
            }
        }, milliseconds);
    }

    public void waite() {
        lock.lockWrite();
        lock.unlockWrite();
    }

    static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    public static void main(String[] args) throws JsonSerializationException, IllegalAccessException {
        InputStream is = String.class.getResourceAsStream("/sql.sql");
        URL url = String.class.getResource("foo.txt");
        String text = new Scanner(String.class.getResourceAsStream("/sql.sql"), "UTF-8").useDelimiter("\\A").next();
        String r = convertStreamToString(is);
        Lok.debug(r);
        //.getClassLoader().getResource("de/mein/auth/service/register.fxml"));
    }


}
