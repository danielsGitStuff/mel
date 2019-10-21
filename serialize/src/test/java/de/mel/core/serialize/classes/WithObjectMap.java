package de.mel.core.serialize.classes;

import de.mel.core.serialize.SerializableEntity;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class WithObjectMap implements SerializableEntity {
    public Map<String, URL> urls = new HashMap<>();
}
