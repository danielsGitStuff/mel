package de.mel.contacts;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Set;

public class DeserialisationComparison {

    public static boolean equals(String json1, String json2) {
        JSONObject jo1 = new JSONObject(json1);
        JSONObject jo2 = new JSONObject(json2);
        return recurse2(jo1, jo2);
    }

    private static boolean recurse(JSONArray a1, JSONArray a2) {
        if (a1.length() != a2.length())
            return false;
        for (int i = 0; i < a1.length(); i++) {
            Object o1 = a1.get(i);
            Object o2 = a2.get(i);
            if ((o1 == null) != (o2 == null))
                return false;
            if (o1 == null)
                continue;
            if (o1.getClass() != o2.getClass())
                return false;
            if (o1 instanceof JSONObject) {
                return DeserialisationComparison.recurse2((JSONObject) o1, (JSONObject) o2);
            }
        }
        return true;
    }

    private static boolean recurse2(JSONObject jo1, JSONObject jo2) {
        Set<String> keys1 = jo1.keySet();
        Set<String> keys2 = jo2.keySet();
        boolean ok = keys2.containsAll(keys1) && keys1.containsAll(keys2);
        if (!ok) return false;
        for (String key : keys2) {
            Object o1 = jo1.get(key);
            Object o2 = jo2.get(key);
            if (o1.getClass() != o2.getClass())
                return false;
            if (o1 instanceof JSONArray) {
                if (!recurse((JSONArray) o1, (JSONArray) o2))
                    return false;
            }
        }
        return true;
    }

    ;
}
