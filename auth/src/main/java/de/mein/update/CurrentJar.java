package de.mein.update;

import de.mein.Lok;

/**
 * This is a workaround to find the jar file the application was started from.
 * The trick is to read the jar from the classpath which contains a certain Java class.
 * This class cannot be arbitrarily chosen (e.g. a BouncyCastle class would lead to a jar which is not what we want)
 * Call initCurrentJarClass() with a class that the application is definitely started from.
 */
public class CurrentJar {
    private static Class<?> currentJarClass;

    public static void initCurrentJarClass(Class<?> clazz) throws Exception {
        if (CurrentJar.currentJarClass == null)
            CurrentJar.currentJarClass = clazz;
        else
            throw new Exception("current jar was initialized before");
    }

    public static Class<?> getCurrentJarClass() {
        if (currentJarClass == null)
            for (int i = 0; i < 5; i++) {
                Lok.error("CurrentJar not initialized");
            }
        return currentJarClass;
    }
}
