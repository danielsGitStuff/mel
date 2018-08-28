package de.mein.core.serialize.classes;


import de.mein.core.serialize.SerializableEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xor on 26.10.2015.
 */
public class ChildSerializableEntity implements SerializableEntity {
    private List<ChildSerializableEntity> children = new ArrayList<>();
	private ChildSerializableEntity parent;
	private String primitive;

	public ChildSerializableEntity setParent(ChildSerializableEntity parent) {
		this.parent = parent;
		return this;
	}

	public ChildSerializableEntity setPrimitive(String primitive) {
		this.primitive = primitive;
		return this;
	}

	public ChildSerializableEntity getParent() {
		return parent;
	}

	public List<ChildSerializableEntity> getChildren() {
		return children;
	}

	public ChildSerializableEntity addChild(ChildSerializableEntity child){
		children.add(child);
		return this;
	}

	public String getPrimitive() {
		return primitive;
	}
}
