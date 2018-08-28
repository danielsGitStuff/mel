package de.mein.core.serialize.serialize.trace.paths;

import java.lang.reflect.Field;

public class FieldPathPart implements IPathPart {

	private String fieldName;

	public FieldPathPart(String pathToken) {
		fieldName = pathToken;
	}

	@Override
	public boolean down(Field field) {
		boolean ok = fieldName.equals(field.getName());
		return ok;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public boolean start(Class entityClazz) {
		return false;
	}

	@Override
	public String getDesc() {
		return fieldName;
	}

	@Override
	public boolean fits(IPathPart tracePart) {
		if (tracePart instanceof FieldPathPart) {
			FieldPathPart fieldPathPart = (FieldPathPart) tracePart;
			return fieldName.equals(fieldPathPart.fieldName);
		} else {
			return false;
		}
	}

	@Override
	public String toString() {
		return "[" + getClass().getSimpleName() + "] field: " + fieldName;
	}
}
