package de.mein.core.serialize.serialize.trace.paths;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Checks whether your traversal through objects matches this path. It stores
 * the traversed object graph as 'trace'
 *
 * @author xor
 */
@SuppressWarnings({"rawtypes"})
public class PathFinder {

    /**
     * exception.g. [Process].processactions.processactions.document
     *
     * @param path
     * @return
     * @throws ClassNotFoundException
     */
    public static List<IPathPart> buildForcedPath(String path) throws ClassNotFoundException {
        List<IPathPart> forcedPath = new ArrayList<>();
        int classNameStart = path.indexOf("[");
        int classNameStop = path.indexOf("]") + 1;
        String className = path.substring(classNameStart, classNameStop);
        forcedPath.add(PathPartFactory.createPathPart(className));
        String rest = path.substring(classNameStop, path.length());
        StringTokenizer tokenizer = new StringTokenizer(rest, ".");
        while (tokenizer.hasMoreElements()) {
            String pathToken = (String) tokenizer.nextElement();
            IPathPart pathPart = PathPartFactory.createPathPart(pathToken);
            forcedPath.add(pathPart);
        }
        return forcedPath;
    }

    private List<IPathPart> forcedPath = new ArrayList<>();
    /**
     * the traversed object graph
     */
    private List<IPathPart> trace = new ArrayList<>();

    private boolean inPath = true;

    private boolean outOfCurrentGraph = false;

    public PathFinder(String path) throws ClassNotFoundException {
        forcedPath = PathFinder.buildForcedPath(path);
    }

    public PathFinder(List<IPathPart> forcedPath) {
        this.forcedPath = forcedPath;
    }

    /**
     * Checks whether the (partly) object graphs fulfills the start condition of
     * the path
     *
     * @param entityClass
     * @return true if entityClass is a valid start
     */
    public boolean start(Class entityClass) {
        inPath = true;
        ClassPathPart classPathPart = new ClassPathPart(entityClass);
        trace.add(classPathPart);
        if (!forcedPath.isEmpty()) {
            ClassPathPart forcedPathPart = (ClassPathPart) forcedPath.get(0);
            inPath = forcedPathPart.start(entityClass);
        }
        return inPath;
    }

    /**
     * Evaluates whether path object graph still match when navigating to a
     * property. it will trace the actual path.
     *
     * @param field
     * @return
     */
    public boolean down(Field field) {
        FieldPathPart traceFieldPathPart = new FieldPathPart(field.getName());
        trace.add(traceFieldPathPart);
        int index = trace.size() - 1;
        if (index < forcedPath.size() && inPath) {
            IPathPart pathPart = forcedPath.get(index);
            inPath = pathPart.down(field);
        } else {
            inPath = false;
        }
        return inPath;
    }

    /**
     * Moves up in the stored object graph, removes its last element and
     * evaluates whether trace and path match
     */
    public void up() {
        // remove last trace item
        if (!trace.isEmpty()) {
            trace.remove(trace.size() - 1);
        }
        // check if in forced path again
        if (!trace.isEmpty() && trace.size() <= forcedPath.size()) {
            inPath = true;
            for (int i = 0; i < trace.size(); i++) {
                IPathPart tracePart = trace.get(i);
                IPathPart forcePart = forcedPath.get(i);
                if (!tracePart.fits(forcePart)) {
                    inPath = false;
                }
            }
        } else {
            inPath = false;
        }
        if (trace.isEmpty()) {
            outOfCurrentGraph = true;
        }
    }

    /**
     * when the starting node is not part of the currently traversed graph this
     * instance can be ignored
     *
     * @return
     */
    public boolean isOutOfCurrentGraph() {
        return outOfCurrentGraph;
    }
}