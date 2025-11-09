package de.mel.util;

import de.mel.auth.jobs.Job;
import de.mel.auth.tools.N;
import de.mel.filesync.service.MelFileSyncService;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class MergeTestTools {
    public static Method getMethod(Class clazz, String methodName) throws NoSuchMethodException {
        System.err.println(clazz);
        Method method = null;
        do {
            Method[] methods = clazz.getDeclaredMethods();
            method = N.first(methods, m -> m.getName().equals(methodName));
            clazz = clazz.getSuperclass();
        } while (method == null);
        method.setAccessible(true);
        return method;
    }

    public static void callWorkOnJob(MelFileSyncService syncService, Job job) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method workWorkWork = getMethod(syncService.getClass(), "workWork");
        workWorkWork.invoke(syncService, job);
    }
}
