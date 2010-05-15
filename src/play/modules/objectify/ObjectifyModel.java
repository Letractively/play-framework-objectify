package play.modules.objectify;

import com.googlecode.objectify.Key;

import java.lang.reflect.Field;

/**
 * The base model for all managed entities, with some convenience methods to simplify application code. Of
 * particular importance is only subclasses of <code>ObjectifyModel</code> is handled by {@link play.modules.objectify.ObjectifyBinder}.
 *
 * @author David Cheong
 * @since 22/04/2010
 * @see play.modules.objectify.ObjectifyBinder
 */
@SuppressWarnings({"unchecked"})
public abstract class ObjectifyModel {

    /**
     * Returns the {@link Key} associated with this entity instance.
     *
     * @param <T> the type
     * @return the key
     */
    public <T extends ObjectifyModel> Key<T> getKey() {
        return ObjectifyService.getKey(this);
    }

    /**
     * An alias of {@link #getKey()}.
     *
     * @param <T> the type
     * @return the key
     */
    public <T extends ObjectifyModel> Key<T> key() {
        return ObjectifyService.key(this);
    }

    /**
     * Returns the string representation of the {@link Key} associated with this entity instance.
     *
     * @return the key string
     */
    public String getKeyStr() {
        return ObjectifyService.getKeyStr(this);
    }

    /**
     * An alias of {@link #getKeyStr()}.
     *
     * @return the key string
     */
    public String keyStr() {
        return ObjectifyService.keyStr(this);
    }

    /**
     * An alias of {@link #getKeyStr()}.
     *
     * @return the key string
     */
    public String str() {
        return ObjectifyService.keyStr(this);
    }

    /**
     * Refreshes an entity instance given string identifier.
     *
     * @param str the string identifier
     * @param <R> the type
     * @return the refreshed instance
     */
    public <R> R fetch(String str) {

        String rawKind;
        String idOrKeyProperty;
        int colon = str.indexOf(":");

        if (colon == -1) {
            if (str.endsWith("Id")) {
                rawKind = str.substring(0, str.length() - 2);
                idOrKeyProperty = str;
            }
            else if (str.endsWith("Key")) {
                rawKind = str.substring(0, str.length() - 3);
                idOrKeyProperty = str;
            }
            else {
                rawKind = str;
                idOrKeyProperty = str;
            }
        }
        else {
            idOrKeyProperty = str.substring(0, colon);
            rawKind = str.substring(colon + 1);
        }

        return (R) fetch(idOrKeyProperty, rawKind);

    }

    /**
     * Refreshes an entity instance given a Long, String or {@link Key}.
     *
     * @param idOrKeyProperty id or key
     * @param rawKind the kind
     * @param <R> the type
     * @return the refreshed instance
     */
    public <R> R fetch(String idOrKeyProperty, String rawKind) {

        Class<?> kind = getKind(rawKind);
        Object idOrKey = getFieldValue(this, idOrKeyProperty);

        if (idOrKey instanceof Long) {
            return ObjectifyService.find((Class<? extends R>) kind, (Long) idOrKey, false);
        }
        else if (idOrKey instanceof String) {
            return ObjectifyService.find((Class<? extends R>) kind, (String) idOrKey, false);
        }
        else if (idOrKey instanceof Key) {
            return ObjectifyService.find((Key<? extends R>) idOrKey, false);
        }
        else if (idOrKey != null) {
            throw new IllegalArgumentException("Id must be a Long, String, Key or null");
        }
        else {
            return null;
        }

    }

    /**
     * Returns the class for a given input string, prepending it with "models." if required.
     *
     * @param rawKind the raw kind
     * @return the kind as a class
     */
    private Class<?> getKind(String rawKind) {
        if (rawKind.startsWith("models.")) {
            return ObjectifyService.loadClass(rawKind);
        }
        else {
            return ObjectifyService.loadClass("models." + capitalize(rawKind));
        }
    }

    /**
     * Returns the value of a given field in an object via direct access.
     *
     * @param object the object
     * @param fieldName the field name
     * @return the field value
     */
    private static Object getFieldValue(Object object, String fieldName) {
        try {
            Field field = object.getClass().getField(fieldName);
            return field.get(object);
        }
        catch (Exception e) {
            throw new RuntimeException("Unable to reflectively get value for: " + fieldName);
        }
    }

    /**
     * Capitalize the first character of a given string.
     *
     * @param str the input string
     * @return the output string
     */
    private String capitalize(String str) {
        int strLen;
        if (str == null || (strLen = str.length()) == 0) {
            return str;
        }
        return new StringBuffer(strLen)
                .append(Character.toTitleCase(str.charAt(0)))
                .append(str.substring(1))
                .toString();
    }

}
