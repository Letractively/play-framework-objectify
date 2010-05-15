package play.modules.objectify;

import com.googlecode.objectify.Key;

import java.lang.reflect.Field;

/**
 * @author David Cheong
 * @since 22/04/2010
 */
@SuppressWarnings({"unchecked"})
public abstract class ObjectifyModel {

//    public <T extends ObjectifyModel> T get(Key<? extends T> key) throws EntityNotFoundException {
//        return ObjectifyService.get(key);
//    }
//
//    public <T extends ObjectifyModel> T get(Long id) throws EntityNotFoundException {
//        return (T) ObjectifyService.get(getClass(), id);
//    }
//
//    public <T extends ObjectifyModel> T get(String name) throws EntityNotFoundException {
//        return (T) ObjectifyService.get(getClass(), name);
//    }
//
//    public <T extends ObjectifyModel> Map<Key<T>, T> getByKeys(Iterable<? extends Key<? extends T>> keys) {
//        return ObjectifyService.get(keys);
//    }
//
//    public <S, T extends ObjectifyModel> Map<S, T> getByIdsOrNames(Iterable<S> idsOrNames) {
//        return (Map<S, T>) ObjectifyService.get(getClass(), idsOrNames);
//    }
//
//    public <T extends ObjectifyModel> T find(Key<? extends T> key, boolean newIfNull) {
//        return ObjectifyService.find(key, newIfNull);
//    }
//
//    public <T extends ObjectifyModel> T find(Long id, boolean newIfNull) {
//        return (T) ObjectifyService.find(getClass(), id, newIfNull);
//    }
//
//    public <T extends ObjectifyModel> T find(String name, boolean newIfNull) {
//        return (T) ObjectifyService.find(getClass(), name, newIfNull);
//    }
//
//    public <T extends ObjectifyModel> Key<T> save() {
//        return put();
//    }
//
//    public <T extends ObjectifyModel> Key<T> put() {
//        return (Key<T>) ObjectifyService.put(this);
//    }
//
//    public void delete() {
//        ObjectifyService.delete(this);
//    }
//
//    public <T extends ObjectifyModel> Query<T> query() {
//        return (Query<T>) ObjectifyService.query(this.getClass());
//    }
//
//    public Objectify begin() {
//        return ObjectifyService.begin();
//    }
//
//    public Objectify beginTxn() {
//        return ObjectifyService.beginTxn();
//    }
//
//    public void commit() {
//        ObjectifyService.commit();
//    }
//
//    public void rollback() {
//        ObjectifyService.rollback();
//    }

    public <T extends ObjectifyModel> Key<T> getKey() {
        return ObjectifyService.getKey(this);
    }

    public <T extends ObjectifyModel> Key<T> key() {
        return ObjectifyService.key(this);
    }

    public String getKeyStr() {
        return ObjectifyService.getKeyStr(this);
    }

    public String keyStr() {
        return ObjectifyService.keyStr(this);
    }

    public String str() {
        return ObjectifyService.keyStr(this);
    }

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

    public <R> R fetch(String idOrKeyProperty, String rawKind) {

        rawKind = capitalize(rawKind);
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

    private Class<?> getKind(String rawKind) {
        if (rawKind.startsWith("models.")) {
            return ObjectifyService.loadClass(rawKind);
        }
        else {
            return ObjectifyService.loadClass("models." + rawKind);
        }
    }

    protected static Object getFieldValue(Object object, String fieldName) {
        try {
            Field field = object.getClass().getField(fieldName);
            return field.get(object);
        }
        catch (Exception e) {
            throw new RuntimeException("Unable to reflectively get value for: " + fieldName);
        }
    }

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
