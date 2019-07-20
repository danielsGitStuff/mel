package de.mein.core.serialize.serialize.fieldserializer.entity;

import de.mein.core.serialize.EntityAnalyzer;
import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.core.serialize.serialize.EntitySerializerMap;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializer;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mein.core.serialize.serialize.reflection.FieldAnalyzer;
import de.mein.core.serialize.serialize.tools.StringBuilder;
import de.mein.core.serialize.serialize.trace.TraceManager;


import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializes objects which implement SerializableEntity. Can be forced to
 * traverse certain graphs. It basically traverses every Field and creates
 * FieldSerializers where necessary. When getJSON() is called all Serializers
 * serialize their Entities and Fields.
 * <p>
 * Practically it rebuilds the Objectgraph tree-like db structure with
 * EntitySerializers. Once this is done it simply concatenates the JSON string
 * of itself and the getJSON() results of its children.
 *
 * @author xor
 * @see TraceManager
 */
@SuppressWarnings({"rawtypes"})
public class SerializableEntitySerializer extends FieldSerializer {
    public static final String ID = "$id";
    public static final String REF = "$ref";

    private Map<Long, SerializableEntity> idEntityMap = new HashMap<>();
    private Long refIdCount;
    private int traversalDepth = 2;

    private TraceManager traceManager = new TraceManager();
    private EntitySerializerMap existingSerializersMap = new EntitySerializerMap();
    private SerializableEntitySerializer parent;
    private Long refId;
    private SerializableEntity entity;
    private Map<Field, FieldSerializer> fieldValueMap = new HashMap<>();
    private List<Field> fields;
    private boolean jsonized = false;
    private boolean prepared = false;

    public SerializableEntitySerializer(TraceManager traceManager, SerializableEntity entity)
            throws IllegalArgumentException, IllegalAccessException {
        this.entity = entity;
        idEntityMap = new HashMap<>();
        refIdCount = 0L;
        this.traceManager = traceManager;
        this.refIdCount = 0L;
    }

    public static String serialize(SerializableEntity entity, int range) throws JsonSerializationException {
        return serialize(entity, null, range);
    }

    public static String serialize(SerializableEntity entity) throws JsonSerializationException {
        return SerializableEntitySerializer.serialize(entity, null, Integer.MAX_VALUE);
    }

    public static String serialize(SerializableEntity entity, TraceManager traceManager, int range) throws JsonSerializationException {
        SerializableEntitySerializer serializer = new SerializableEntitySerializer().setTraceManager(traceManager);
        serializer.setEntity(entity).setTraversalDepth(range);
        return serializer.JSON();
    }

    public SerializableEntity getEntity() {
        return entity;
    }

    public SerializableEntitySerializer() {
        this.refIdCount = 0L;
    }

    public SerializableEntitySerializer(SerializableEntitySerializer parent, SerializableEntity entity) {
        this.idEntityMap = parent.idEntityMap;
        this.refIdCount = null;
        this.parent = parent;
        this.existingSerializersMap = parent.existingSerializersMap;
        this.traceManager = parent.traceManager;
        this.traversalDepth = parent.traversalDepth - 1;
        this.entity = entity;
        existingSerializersMap.put(this);
    }

    public void addFieldFieldSerializer(Field field, FieldSerializer serializer) {
        fieldValueMap.put(field, serializer);
    }

    public SerializableEntitySerializer getPreparedSerializer(SerializableEntity entity)
            throws JsonSerializationException {
        SerializableEntitySerializer serializer = existingSerializersMap.get(entity);
        if (serializer == null) {
            serializer = new SerializableEntitySerializer(this, entity);
            existingSerializersMap.put(serializer);
            serializer.prepare();
        } else {
            /**
             * we have to traverse again if the depth of the already existing
             * parentSerializer is not enough
             */
            if (serializer.traversalDepth < traversalDepth - 1) {
                serializer.setTraversalDepth(traversalDepth - 1);
                serializer.prepare();
            }
        }
        return serializer;
    }

    /**
     * Sets the maximum depth the EntitySerializer will traverse the nodes
     *
     * @param traversalDepth
     * @return
     */
    public SerializableEntitySerializer setTraversalDepth(int traversalDepth) {
        this.traversalDepth = traversalDepth;
        this.prepared = false;
        return this;
    }

    /**
     * Generates an id which will appear as "$id" or "$ref" in the de.mein.json.json
     *
     * @return
     */
    private Long generateRefId() {
        if (parent != null) {
            return parent.generateRefId();
        } else {
            refIdCount++;
            return refIdCount;
        }
    }

    private void assignRefId() {
        if (refId == null) {
            refId = generateRefId();
        }
    }

    public TraceManager getTraceManager() {
        return traceManager;
    }

    /**
     * traverses the entity and creates serializers for each field if necessary.
     *
     * @return
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     * @throws ClassNotFoundException
     */
    private void prepare() throws JsonSerializationException {
        try {
            this.fields = EntityAnalyzer.getSerializableFields(this.entity);
            this.fieldValueMap.clear();
            this.prepared = true;
            traceManager.start(this.entity);
            // if this is the root parentSerializer the constructor did not add this
            // to the existingSerializersMap
            existingSerializersMap.put(this);
            for (Field field : this.fields) {
                // "this$0" appears if you serialize a nested class
                if (!field.getName().equals("this$0")) {
                    traceManager.down(field);
                    // nand
                    if (!((traversalDepth == 0) && (FieldAnalyzer.isEntitySerializable(field) || FieldAnalyzer.isEntitySerializableCollection(field))) || traceManager.isForcedPath()) {
                        FieldSerializer fieldSerializer = FieldSerializerFactoryRepository.buildFieldSerializer(this, field);
                        if (fieldSerializer != null) {
                            this.fieldValueMap.put(field, fieldSerializer);
                        }
                    }
                    traceManager.up();
                }
            }
        } catch (Exception e) {
            System.err.println("SerializableEntitySerializer.prepare()");
            e.printStackTrace();
            throw new JsonSerializationException(e);
        }

    }

    @Override
    public boolean isNull() {
        return false;
    }

    public String JSON() throws JsonSerializationException {
        try {
            StringBuilder b = new StringBuilder();
            SerializableEntitySerializer existingSerializer = existingSerializersMap.get(entity);
            if (existingSerializer != null && existingSerializer != this)
                return existingSerializer.JSON();
            if (!this.prepared)
                this.prepare();
            if (!jsonized) {
                jsonized = true;
                assignRefId();
                b.objBegin().key(ID).eq().value(refId);
                String type = EntityAnalyzer.getType(entity);
                b.comma().key("__type").eq().value(type);
                for (Field field : fields) {
                    FieldSerializer fieldSerializer = this.fieldValueMap.get(field);
                    if (fieldSerializer != null && !fieldSerializer.isNull()) {
                        b.comma();
                        b.key(field.getName()).eq();
                        b.append(fieldSerializer.JSON());
                    }
                }
                b.objEnd();
            } else {
                b.objBegin().key(REF).eq().value(refId).objEnd();
            }
            return b.toString();
        } catch (Exception e) {
            e.printStackTrace();
            throw new JsonSerializationException(e);
        }
    }

    @Override
    public String toString() {
        return "serialises a '" + EntityAnalyzer.getType(entity);// entity.getType();
    }

    /**
     * Set this to force the EntitySerializer to traverse certain paths
     *
     * @param traceManager
     */
    public SerializableEntitySerializer setTraceManager(TraceManager traceManager) {
        this.traceManager = traceManager;
        if (this.traceManager == null) {
            this.traceManager = new TraceManager();
        }
        return this;
    }

    public SerializableEntitySerializer setEntity(SerializableEntity entity) {
        if (this.entity != null) {
            existingSerializersMap.remove(this.entity);
        }
        this.entity = entity;
        this.prepared = false;
        return this;
    }

    public int getTraversalDepth() {
        return traversalDepth;
    }


}
