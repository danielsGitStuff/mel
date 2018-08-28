package de.mein.drive.nio;

import de.mein.drive.sql.FsDirectory;
import de.mein.drive.data.fs.RootDirectory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class OPath {
	public static List<String> calcRelPath(RootDirectory root, FsDirectory f) {
		String absPath = f.getOriginal().getAbsolutePath();
		String rootPath = root.getPath();
		if (absPath.startsWith(rootPath)) {
			String relPath = absPath.substring(rootPath.length());
			StringTokenizer tokenizer = new StringTokenizer(relPath, File.separator);
			List<String> result = new ArrayList<>();
			while (tokenizer.hasMoreElements()) {
				String name = tokenizer.nextElement().toString();
				result.add(name);
			}
			return result;
		} else {
			System.err.println("OPath.calcRelPath(): path did not match!!");
		}
		return null;
	}

}
