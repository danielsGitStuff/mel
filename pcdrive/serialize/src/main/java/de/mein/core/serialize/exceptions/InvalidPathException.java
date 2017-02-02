package de.mein.core.serialize.exceptions;

@SuppressWarnings("serial")
public class InvalidPathException extends Exception {

	private Exception innerException;
	private String path;

	public InvalidPathException(String path, Exception innerException) {
		this.path = path;
		this.innerException = innerException;
	}

	@Override
	public synchronized Throwable getCause() {
		return innerException;
	}

	@Override
	public String getMessage() {
		return "this provided path is invalid: '" + path
				+ "'. it has to be formatted like this: '[MyClass].property.property'...";
	}
}
