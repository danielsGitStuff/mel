package de.mel.core.serialize.exceptions;


import de.mel.core.serialize.EntityAnalyzer;
import de.mel.core.serialize.SerializableEntity;

public class EntityNotFoundException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2530874275566286457L;
//	private final Long entityId;
	private String type;

	public EntityNotFoundException(SerializableEntity entity) {
//		this.entityId = EntityAnalyzer.getId(entity); entity.getId();
		this.type = EntityAnalyzer.getType(entity);
	}

//	public EntityNotFoundException(Long id) {
//		this.entityId = id;
//	}

	@Override
	public String getMessage() {
		return "asdasdasdasasdasrgtr5z56757zu6";
//		return "The desired Entity with the type " + type + " and id " + entityId + " was not found! :(";
	}

}
