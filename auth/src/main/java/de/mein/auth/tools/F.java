package de.mein.auth.tools;

import java.io.File;

public class F {
    public static void rmRf(File f){
        N.forEach(f.listFiles(File::isFile), File::delete);
        N.forEach(f.listFiles(File::isDirectory),F::rmRf);
        f.delete();
    }
}
