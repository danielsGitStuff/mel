package de.mel.core.serialize.serialize.trace.paths;

import de.mel.core.serialize.EntityAnalyzer;
import de.mel.core.serialize.SerializableEntity;

import java.lang.reflect.Field;


@SuppressWarnings({ "unchecked", "rawtypes" })
public class ClassPathPart implements IPathPart {

	private Class<? extends SerializableEntity> clazz;

	/**
	 * 
	 * @param className
	 *            something like this: '[ClassName]'
	 * @throws ClassNotFoundException
	 */
	public ClassPathPart(String className) throws ClassNotFoundException {
		clazz = EntityAnalyzer.clazz(className);
	}

	public ClassPathPart(Class entityClass) {
		this.clazz = entityClass;
	}

	@Override
	public boolean down(Field field) {
		Class fieldClass = field.getDeclaringClass();
		if (fieldClass.isAssignableFrom(clazz)) {
			return true;
		}
		return false;
	}

	@Override
	public boolean start(Class entityClazz) {
		if (entityClazz.isAssignableFrom(clazz)) {
			return true;
		}
		return false;
	}

	@Override
	public String getDesc() {
		return clazz.getSimpleName();
	}

	@Override
	public boolean fits(IPathPart tracePart) {
		if (tracePart instanceof ClassPathPart) {
			ClassPathPart classPathPart = (ClassPathPart) tracePart;
			return clazz.isAssignableFrom(classPathPart.clazz);
		} else {
			return false;
		}
	}

	@Override
	public String toString() {
		return "[" + getClass().getSimpleName() + "] clazz: " + clazz.getSimpleName();
	}
}
