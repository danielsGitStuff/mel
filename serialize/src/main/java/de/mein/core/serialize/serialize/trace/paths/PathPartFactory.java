package de.mein.core.serialize.serialize.trace.paths;

/**
 * Delivers delicious {@link IPathPart} instances depending on their description
 * 
 * @author DECK006
 *
 */
public class PathPartFactory {

	private PathPartFactory() {

	}

	public static IPathPart createPathPart(String pathToken) throws ClassNotFoundException {
		IPathPart pathPart;
		if (pathToken.startsWith("[") && pathToken.endsWith("]")) {
			String className = pathToken.substring(1, pathToken.length() - 1);
			pathPart = new ClassPathPart(className);
		} else {
			pathPart = new FieldPathPart(pathToken);
		}
		return pathPart;
	}
}
