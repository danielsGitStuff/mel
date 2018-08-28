package de.mein.core.serialize.deserialize;

import de.mein.core.serialize.SerializableEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Creates maps of all existing Entities in Entity graphs. Can distribute the
 * according database entities as well.
 * 
 * @author xor
 *
 */
@SuppressWarnings({ "rawtypes" })
public class EntityTraverser {

	protected final Logger logger = LoggerFactory.getLogger(getClass());
	private Map<Long, SerializableEntity> idEntityMap = new HashMap<>();
	private Map<Long, SerializableEntity> idDBEntityMap = new HashMap<>();
	private Map<SerializableEntity, Boolean> newEntitiesMap = new HashMap<>();
	private Map<Long, SerializableEntity> idDeletionMap = new HashMap<>();

	public EntityTraverser() {
	}

	// public boolean isInDeletionGraph(Entity entity) throws NoGraphIdException
	// {
	// Long id = entity.getId();
	// if (id != null) {
	// return idDeletionMap.containsKey(id);
	// } else {
	// throw new NoGraphIdException(entity);
	// }
	// }

	public Map<Long, SerializableEntity> getIdEntityMap() {
		return idEntityMap;
	}

	public Map<SerializableEntity, Boolean> getNewEntitiesMap() {
		return newEntitiesMap;
	}

	public List<SerializableEntity> getEntitiesToSave() {
		List<SerializableEntity> result = idDBEntityMap.values().stream().collect(Collectors.toList());
		result.addAll(newEntitiesMap.keySet().stream().collect(Collectors.toList()));
		return result;
	}

	private void addEntity(SerializableEntity entity) {
		// Long id = entity.getId();
		// if (id != null) {
		// idEntityMap.put(id, entity);
		// } else {
		newEntitiesMap.put(entity, true);
		// }
	}

	public boolean isInGraph(SerializableEntity entity) {
		// Long id = entity.getId();
		// if (id != null) {
		// return this.idEntityMap.containsKey(id);
		// } else {
		return false;
		// }
	}

//	/**
//	 * Loads a copy from the database and stores it so you won't get a different
//	 * instance each time
//	 *
//	 * @param entity
//	 * @return
//	 */
//	public SerializableEntity getDBEntity(SerializableEntity entity) throws EntityNotFoundException {
//		// Long id = entity.getId();
//		// if (id != null) {
//		// Entity dbEntity = null;
//		// if (this.idDBEntityMap.containsKey(id)) {
//		// dbEntity = this.idDBEntityMap.get(id);
//		// } else {
//		// dbEntity = entity.load();
//		// if (dbEntity == null) {
//		// throw new EntityNotFoundException(entity);
//		// } else {
//		// this.idDBEntityMap.put(id, dbEntity);
//		// }
//		// }
//		// return dbEntity;
//		// }
//		return null;
//	}

	public void collectEntitiesForDeletion(Object object) throws IllegalArgumentException, IllegalAccessException {
		EntityTraverser traverser = new EntityTraverser();
		traverser.collectEntities(object);
		this.idDeletionMap = traverser.getIdEntityMap();
	}

	/**
	 * Traverses through all fields of this object and collects all Entity
	 * instances by storing their GraphIDs to entityMap or, if GraphId is null
	 * or not existent, the instance is supposed to be new and therefore is
	 * stored in the newEntitiesMap.
	 * 
	 * @param object
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 */
	public void collectEntities(Object object) throws IllegalArgumentException, IllegalAccessException {
		// if (object != null) {
		// Class objClass = object.getClass();
		// if (FieldAnalyzer.isInterestingClass(objClass)) {
		// SerializableEntity castedObj = (SerializableEntity) object;
		// if (!this.newEntitiesMap.containsKey(castedObj) &&
		// !this.idEntityMap.containsKey(castedObj.getId())) {
		// addEntity(castedObj);
		// List<Field> fields = EntityAnalyzer.getFields(castedObj.getClass());
		// for (Field field : fields) {
		// field.setAccessible(true);
		// Object fieldObject = field.get(object);
		// collectEntities(fieldObject);
		// }
		// }
		// } else if (FieldAnalyzer.isSet(objClass)) {
		// Collection collection = (Collection) object;
		// for (Object collectionObject : collection) {
		// collectEntities(collectionObject);
		// }
		// }
		// }
	}
}
