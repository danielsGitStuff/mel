package de.mein.core.serialize.deserialize;

/**
 * If you just deserialize GraphSets and store the graphs in the database, the
 * graph of the database might get cut on the edges of the GraphSet's graphs.
 * This is rather adverse.
 * 
 * @author xor
 *
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class GraphMerger {

//	private EntityTraverser traverser;
//	private Map<Long, Entity> traversedEntities = new HashMap<>();
////	private AccessController accessController;
////	private Neo4jTemplate template;
//
//	/**
//	 * merges a GraphSet with the graph DB graph
//	 * 
//	 * @param graphs
//	 * @return
//	 * @throws IllegalArgumentException
//	 * @throws IllegalAccessException
//	 * @throws EntityNotFoundException
//	 * @throws EntityNotInGraphException
//	 * @throws NoGraphIdException
//	 */
//	public List<Entity> merge(Neo4jTemplate template, GraphSet graphSet) throws IllegalArgumentException, IllegalAccessException, EntityNotFoundException,
//			EntityNotInGraphException, NoGraphIdException {
//
//		Set<Entity> graphs = graphSet.getGraphs();
//		Set<Entity> deletionGraphs = graphSet.getDelete();
//
//		this.template = template;
//		this.accessController = new AccessController(this.template, graphSet.getAppUser());
//		this.traverser = new EntityTraverser();
//		this.traverser.collectEntities(graphs);
//		this.traverser.collectEntitiesForDeletion(deletionGraphs);
//
//		List<Entity> graphsToSave = new ArrayList<>();
//		List<Entity> entities = traverser.getIdEntityMap().values().stream().collect(Collectors.toList());
//		for (Entity entity : entities) {
//			Entity traversedEntity = traverseEntity(entity);
//			if (traversedEntity != null) {
//				graphsToSave.add(traversedEntity);
//			}
//		}
//
//		// stuff which is new in the graph
//		for (Entity entity : traverser.getNewEntitiesMap().keySet()) {
//			graphsToSave.add(traverseEntity(entity));
//		}
//		return graphsToSave;
//	}
//
//	public void trave(Entity entity) {
//
//	}
//
//	public EntityTraverser getTraverser() {
//		return traverser;
//	}
//
//	private Entity traverseEntity(Entity entity) throws IllegalArgumentException, IllegalAccessException, EntityNotFoundException, NoGraphIdException {
//		if (entity == null) {
//			return null;
//		}
//
//		List<Field> fields = entity.getFields();
//
//		boolean access = accessController.hasAccess(entity);
//		if (!access) {
//			/**
//			 * it has to be removed from the entityMap or it might cause a cut
//			 * in the database
//			 */
//			traverser.getIdEntityMap().remove(entity.getId());
//			return null;
//		}
//		Long id = entity.getId();
//		if (traversedEntities.containsKey(id)) {
//			// skip!
//			return traversedEntities.get(id);
//		} else {
//			Entity dbEntity = traverser.getDBEntity(entity);
//			if (dbEntity == null) {
//				dbEntity = entity;
//			}
//			traversedEntities.put(id, dbEntity);
//			for (Field field : fields) {
//				field.setAccessible(true);
//				Object fieldValue = field.get(entity);
//				Class fieldClass = field.getType();
//				if (FieldAnalyzer.isInterestingClass(fieldClass)) {
//					applyInterestingField(field, entity);
//				} else if (FieldAnalyzer.isSet(fieldClass)) {
//					applySet(field, entity);
//				} else {
//					field.set(dbEntity, fieldValue);
//				}
//			}
//			return dbEntity;
//		}
//	}
//
//	private boolean isSetEmpty(Object setObject) {
//		boolean result = setObject == null || ((Set<Entity>) setObject).size() == 0;
//		return result;
//	}
//
//	private void applySet(Field field, Entity entity) throws IllegalArgumentException, IllegalAccessException, EntityNotFoundException, NoGraphIdException {
//		Entity dbEntity = traverser.getDBEntity(entity);
//		Object jsObject = field.get(entity);
//		Object dbObject = field.get(dbEntity);
//		Set<Entity> setToSet = new HashSet<>();
//		if (isSetEmpty(jsObject)) {
//			if (dbObject != null) {
//				Set<Entity> dbEntities = (Set<Entity>) dbObject;
//				for (Entity dbSetEntity : dbEntities) {
//					if (!traverser.isInGraph(dbSetEntity)) {
//						setToSet.add(dbSetEntity);
//					}
//				}
//			}
//		} else {
//			Set<Entity> jsEntities = (Set<Entity>) jsObject;
//			for (Entity jsSetEntity : jsEntities) {
//				Entity traversedEntity = traverseEntity(jsSetEntity);
//				if (traversedEntity != null) {
//					setToSet.add(traversedEntity);
//				}
//			}
//			if (dbObject != null) {
//				Set<Entity> dbSetEntities = (Set<Entity>) dbObject;
//				for (Entity dbSetEntity : dbSetEntities) {
//					if (!traverser.isInGraph(dbSetEntity) || traverser.isInDeletionGraph(dbSetEntity)) {
//						setToSet.add(dbSetEntity);
//					}
//				}
//			}
//		}
//		field.set(dbEntity, setToSet);
//	}
//
//	private Entity applyInterestingField(Field field, Entity entity) throws EntityNotFoundException, IllegalArgumentException, IllegalAccessException, NoGraphIdException {
//		Entity dbEntity = traverser.getDBEntity(entity);
//		if (dbEntity == null) {
//			return entity;
//		}
//		Object jsObject = field.get(entity);
//		Object dbObject = field.get(dbEntity);
//		Entity entityToSet = null;
//		if (jsObject == null) {
//			if (dbObject != null) {
//				entityToSet = (Entity) dbObject;
//			}
//		} else {
//			entityToSet = traverseEntity((Entity) jsObject);
//		}
//		field.set(dbEntity, entityToSet);
//		return dbEntity;
//	}
}
