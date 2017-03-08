package de.mein.core.serialize.serialize.reflection.classes;


import de.mein.core.serialize.JsonIgnore;
import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.serialize.tools.StringBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by xor on 01.11.2015.
 */
public class ReflectionTestPojo {
    private class Entity implements SerializableEntity {
        String name = "name";
    }

    private String primitive = "testString";
    private Collection<String> primitiveCollection = new ArrayList() {{
        add("1");
        add("2");
    }};
    private Entity entity = new Entity();
    private Collection<Entity> entityCollection = new ArrayList() {{
        add(new Entity());
    }};
    private List<List<String>> twoDimensionalList = new ArrayList(){{
        add(new ArrayList<String>(){{
            add("1.1");
            add("1.2");
        }});
        add(new ArrayList<String>(){{
            add("2.1");
            add("2.2");
        }});
    }};
    @JsonIgnore
    private String ignoredPrimitive = "ignore me!";

    public Collection<Entity> getEntityCollection() {
        return entityCollection;
    }

    public Collection<String> getPrimitiveCollection() {
        return primitiveCollection;
    }

    public Entity getEntity() {
        return entity;
    }

    public String getIgnoredPrimitive() {
        return ignoredPrimitive;
    }

    public String getPrimitive() {
        return primitive;
    }
}
