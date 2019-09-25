package de.mel.core.serialize.serialize.tools;

import org.json.JSONObject;

public class StringBuilder {
    private java.lang.StringBuilder sb = new java.lang.StringBuilder();

    public StringBuilder() {

    }

    public StringBuilder append(Object object) {
        sb.append(object);
        return this;
    }

    @Override
    public String toString() {
        return sb.toString();
    }

    /**
     * writes: {
     */
    public StringBuilder objBegin() {
        sb.append('{');
        return this;
    }

    /**
     * writes: }
     */
    public StringBuilder objEnd() {
        sb.append('}');
        return this;
    }

    /**
     * writes: :
     */
    public StringBuilder eq() {
        sb.append(':');
        return this;
    }

    /**
     * writes: ,
     */
    public StringBuilder comma() {
        sb.append(',');
        return this;
    }

    /**
     * writes: [
     */
    public StringBuilder arrBegin() {
        sb.append('[');
        return this;
    }

    /**
     * writes: ]
     */
    public StringBuilder arrEnd() {
        sb.append(']');
        return this;
    }

    /**
     * writes: "key"
     *
     * @param key
     */
    public StringBuilder key(String key) {
        sb.append('"');
        sb.append(key);
        sb.append('"');
        return this;
    }

    /**
     * writes: "\n"
     */
    public StringBuilder lineBreak() {
        sb.append("\n");
        return this;
    }

    /**
     * writes: 1<br>
     * or writes: "not a number"
     */
    @SuppressWarnings("rawtypes")
    public StringBuilder value(Object value) {
        if (value != null) {
            Class valueClass = value.getClass();
            if (Number.class.isAssignableFrom(valueClass)) {
                sb.append(value);
            } else {
                String stringToWrite = JSONObject.quote(value.toString());
                sb.append(stringToWrite);
            }
        } else {
            sb.append("null");
        }
        return this;
    }

    public StringBuilder br() {
        sb.append("<br>");
        return this;
    }
}
