package de.mein.core.serialize.serialize.fieldserializer.collections;

import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializer;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mein.core.serialize.serialize.tools.StringBuilder;
import de.mein.core.serialize.serialize.trace.TraceManager;

import java.util.*;

/**
 * Created by xor on 12/20/15.
 */
public class SerializableEntityCollectionSerializer extends FieldSerializer {

    protected SerializableEntitySerializer parentSerializer;
    protected List<SerializableEntitySerializer> serializers = new ArrayList<>();
    protected Iterable<? extends SerializableEntity> iterable;
    protected Integer traversalDepth;
    // debugger helper code, in case you need sorted lists cause you compare object graphs with json
    public static Comparator comparator;

    public SerializableEntityCollectionSerializer(SerializableEntitySerializer parentSerializer, Iterable<? extends SerializableEntity> iterable) {
        this.parentSerializer = parentSerializer;
        this.traversalDepth = parentSerializer.getTraversalDepth() - 1;
        this.iterable = iterable;
    }

    public void setTraversalDepth(Integer traversalDepth) {
        this.traversalDepth = traversalDepth;
    }

    public void setTraceManager(TraceManager traceManager) {
        parentSerializer.setTraceManager(traceManager);
    }

    @Override
    public boolean isNull() {
        return iterable == null || !iterable.iterator().hasNext();
    }

    @Override
    public String JSON() throws JsonSerializationException {
        StringBuilder b = new StringBuilder().arrBegin();
        int count = 0;

        Iterator<? extends SerializableEntity> iterator;
        // debugger helper code, in case you need sorted lists cause you compare object graphs with json
        if (comparator != null) {
            Iterator iter = iterable.iterator();
            List copy = new ArrayList();
            while (iter.hasNext())
                copy.add(iter.next());
            Collections.sort(copy, comparator);
            iterator = copy.iterator();
        } else iterator = iterable.iterator();
        iterator = iterable.iterator();
        while (iterator.hasNext()) {
            //TODO del
            SerializableEntity entity = null;
            try {
                entity = iterator.next();
            } catch (Exception e) {
                e.printStackTrace();
            }
            //SerializableEntity entity = iterator.next();
            SerializableEntitySerializer serializer;
            if (entity != null) {
                serializer = this.parentSerializer.getPreparedSerializer(entity);
                serializer.setTraversalDepth(traversalDepth);
                b.append(serializer.JSON());
            } else {
                b.append("null");
            }
            if (iterator.hasNext()) {
                b.comma();
            }
            count++;
        }
        b.arrEnd();
        return b.toString();
    }
}
