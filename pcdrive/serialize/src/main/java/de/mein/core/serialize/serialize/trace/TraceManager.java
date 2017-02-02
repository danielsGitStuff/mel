package de.mein.core.serialize.serialize.trace;

import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.exceptions.InvalidPathException;
import de.mein.core.serialize.serialize.trace.paths.ClassPathPart;
import de.mein.core.serialize.serialize.trace.paths.IPathPart;
import de.mein.core.serialize.serialize.trace.paths.PathFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Used by EntitySerializer to determine whether it has to serialize its
 * currently processed path. TraceManager uses {@link PathFinder}s to do so. Use
 * the up() and and down() methods to move in the object tree hierarchy.
 * 
 * @author DECK006
 *
 */
public class TraceManager {

	protected static final Logger logger = LoggerFactory.getLogger(TraceManager.class);

	private List<List<IPathPart>> forcedPaths = new ArrayList<>();

	private Set<PathFinder> runningPathFinders = new HashSet<>();

	private final List<IPathPart> startsTrace = new ArrayList<>();

	private boolean isForcedPath = false;

	public TraceManager() {

	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder(
				"[" + getClass().getSimpleName() + "] isForcedPath: " + isForcedPath + "\n");
		b.append("trace:\n");
		for (IPathPart pathPart : startsTrace) {
			b.append(pathPart.getDesc()).append("\n");
		}
		return b.toString();
	}

	/**
	 * Call this when moving up in the object graph hierarchy
	 */
	public void up() {
		if (!startsTrace.isEmpty()) {
			startsTrace.remove(startsTrace.size() - 1);
		}
		List<PathFinder> pathFindersToDelete = new ArrayList<>();
		for (PathFinder pathFinder : runningPathFinders) {
			pathFinder.up();
			if (pathFinder.isOutOfCurrentGraph()) {
				pathFindersToDelete.add(pathFinder);
			}
		}
		runningPathFinders.removeAll(pathFindersToDelete);
	}

	/**
	 * Call this when navigating to a property
	 * 
	 * @param field
	 */
	public void down(Field field) {
		isForcedPath = false;
		for (PathFinder pathFinder: runningPathFinders){
			if (pathFinder.down(field)){
				isForcedPath=true;
				return;
			}
		}
//		runningPathFinders.stream().filter(pathFinder -> pathFinder.down(field)).forEach(pathFinder -> {
//			isForcedPath = true;
//		});
	}

	/**
	 * Checks whether the node meets the starting type of a forced graph. If so
	 * the path will be stored and checked until the starting node is no longer
	 * part of the currently traversed object tree.
	 * 
	 * @param entity
	 * @throws ClassNotFoundException
	 */
	public void start(SerializableEntity entity) throws ClassNotFoundException {
		IPathPart pathPart = new ClassPathPart(entity.getClass().getName());
		startsTrace.add(pathPart);
		for (List<IPathPart> forcedPath : forcedPaths) {
			PathFinder finder = new PathFinder(forcedPath);
			if (finder.start(entity.getClass())) {
				runningPathFinders.add(finder);
				isForcedPath = true;
			}
		}
	}

	public boolean isForcedPath() {
		return isForcedPath;
	}

	/**
	 * exception.g. "[Process].processactions.processactions.document"
	 * 
	 * @param path
	 * @throws InvalidPathException
	 * @throws ClassNotFoundException
	 */
	public TraceManager addForcedPath(String path) throws InvalidPathException {
		List<IPathPart> forcedPath;
		try {
			forcedPath = PathFinder.buildForcedPath(path);
			forcedPaths.add(forcedPath);
		} catch (ClassNotFoundException e) {
			throw new InvalidPathException(path, e);
		}
		return this;
	}

}
