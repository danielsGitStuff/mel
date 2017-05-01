package de.mein.core.serialize.serialize;

import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Stores EntitySerializableSerializers by using their Entity as a key. Entities
 * which are not supposed to be stored in the database are stored by their
 * reference, stuff that goes comes out of the database is stored by using their
 * IDs as a key.
 * 
 * @author xor
 *
 */
public class EntitySerializerMap {
	// /**
	// * Entities must be stored by IDs, cause when receiving the same Entity
	// * twice you end up with two different objects.
	// */
	// private Map<Long, SerializableEntitySerializer> entitiesWithIds = new
	// HashMap<>();
	/**
	 * Entities which are not in the database yet or are not supposed to be
	 * stored in the database do not provide any ID. So they have to be stored
	 * differently.
	 */
	private Map<SerializableEntity, SerializableEntitySerializer> entitiesWithoutIds = new HashMap<>();

	public SerializableEntitySerializer get(SerializableEntity entity) {
		// Long id = entity.getId();
		// if (id == null) {
		if (entitiesWithoutIds.containsKey(entity)) {
			return entitiesWithoutIds.get(entity);
		} else {
			return null;
		}
		// } else {
		// if (entitiesWithIds.containsKey(id)) {
		// return entitiesWithIds.get(id);
		// } else {
		// return null;
		// }
		// }
	}

	/**
	 * it does not put if there is already a proper parentSerializer for that entity
	 * 
	 * @param serializer
	 */
	public void put(SerializableEntitySerializer serializer) {
		SerializableEntity entity = serializer.getEntity();
		// Long id = entity.getId();
		// if (id == null) {
		entitiesWithoutIds.put(entity, serializer);
		// } else {
		// entitiesWithIds.put(id, parentSerializer);
		// }
	}

	public void remove(SerializableEntity entity) {
		// Long id = entity.getId();
		// if (id == null) {
		if (entitiesWithoutIds.containsKey(entity)) {
			entitiesWithoutIds.remove(entity);
		}
		// } else {
		// if (entitiesWithIds.containsKey(id)) {
		// entitiesWithIds.remove(id);
		// }
		// }
	}
}
