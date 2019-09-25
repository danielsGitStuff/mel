package de.mel.sql;

import java.util.List;

public class Logger {
	public static Logger getLogger(Class<?> clazz) {
		return new Logger();
	}

	public void warn(String string) {
		System.out.println(string);
	}

	public void error(String string, Exception e) {
		System.err.println(string);
		System.err.println(e.getMessage());
		e.printStackTrace();
//		if (exception.getStackTrace()!=null)
//			Arrays.stream(exception.getStackTrace()).forEach(System.err::println);
	}

	public void debug(String string) {
		System.out.println(string);

	}

	public void error(String message) {
		System.err.println(message);
	}

	public void errorWhereArgs(List<Object> whereArgs) {
		if (whereArgs!=null)
			whereArgs.forEach(arg -> System.err.print("wherearg: " + arg + " "));
		System.err.print("\n");
	}
}
