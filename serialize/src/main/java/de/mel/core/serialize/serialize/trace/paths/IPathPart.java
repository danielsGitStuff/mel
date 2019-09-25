package de.mel.core.serialize.serialize.trace.paths;

import java.lang.reflect.Field;

public interface IPathPart {

	public boolean down(Field field);

	@SuppressWarnings("rawtypes")
	public boolean start(Class entityClazz);

	public String getDesc();

	public boolean fits(IPathPart tracePart);

}
