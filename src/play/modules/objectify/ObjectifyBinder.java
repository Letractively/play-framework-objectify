package play.modules.objectify;

import com.googlecode.objectify.Key;
import play.data.binding.BeanWrapper;
import play.data.binding.Binder;
import play.exceptions.UnexpectedException;

import javax.persistence.Embedded;
import javax.persistence.Id;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * A simple binder which has a single entry point method {@link #bind(String, Class, java.lang.reflect.Type, java.util.Map)}
 * which is invoked by {@link ObjectifyPlugin#bind(String, Class, java.lang.reflect.Type, java.lang.annotation.Annotation[], java.util.Map)}
 * to handling binding. Applications wishing to provide custom binding logic may subclass this and provide a reference via the "objectify.binder"
 * property in application.conf.
 *
 * @author David Cheong
 * @since 29/04/2010
 * @see play.modules.objectify.ObjectifyPlugin
 * @see play.modules.objectify.ObjectifyModel
 */
public class ObjectifyBinder {

    /**
     * Invoked when binding HTTP parameters to {@link ObjectifyModel} instances.
     *
     * @param name the param name
     * @param clazz the target class which should be ObjectifyModel
     * @param type the type
     * @param params the params map
     * @return the bound instance or null
     */
    @SuppressWarnings({"unchecked", "UnusedDeclaration"})
    public Object bind(String name, Class clazz, Type type, Map<String, String[]> params) {

        if (ObjectifyModel.class.isAssignableFrom(clazz)) {

            String idKey = name + ".id";

            if (params.containsKey(idKey) && params.get(idKey).length > 0 && params.get(idKey)[0] != null && params.get(idKey)[0].trim().length() > 0) {

                String rawId = params.get(idKey)[0];
                params.remove(idKey);
                Class idType = findKeyType(clazz);

                ObjectifyModel instance = find(clazz, rawId, idType);
                if (instance != null) {
                    return edit(instance, name, params);
                }

            }

            return create(clazz, name, params);

        }

        return null;

    }

    /**
     * Finds a {@link ObjectifyModel} instance given the entity class, the id as a raw string and
     * the id type. If a {@link Key} cannot be parsed or otherwise resolved properly, this method
     * returns null.
     *
     * @param clazz the entity class
     * @param rawId the id as a raw string
     * @param idType the id type
     * @param <T> the entity type
     * @return the instance or null
     */
    public <T extends ObjectifyModel> T find(Class<T> clazz, String rawId, Class idType) {
        Key<T> key = null;
        if (isNumeric(rawId)) {
            key = new Key<T>(clazz, Long.parseLong(rawId));
        }
        else if (idType.equals(String.class)) {
            key = new Key<T>(clazz, rawId);
        }
        T instance = null;
        if (key != null) {
            instance = ObjectifyService.find(key, false);
        }
        if (instance == null) {
            try {
                key = ObjectifyService.getKey(rawId);
            }
            catch (IllegalArgumentException e) {
                return null;
            }
            instance = ObjectifyService.find(key, false);
        }
        return instance;
    }

    /**
     * Instantiates a new {@link ObjectifyModel} instance and bind the supplied parameters where appropriate.
     *
     * @param clazz the entity class
     * @param name the param name
     * @param params the params map
     * @param <T> the entity type
     * @return the bound instance
     */
    @SuppressWarnings({"unchecked"})
    public <T extends ObjectifyModel> T create(Class clazz, String name, Map<String, String[]> params) {
        T instance = (T) ObjectifyService.instantiate(clazz);
        return edit(instance, name, params);
    }

    /**
     * Binds the given entity instance with the supplied parameters.
     *
     * @param instance the entity instance
     * @param name the param name
     * @param params the params map
     * @param <T> the entity type
     * @return the bound instance
     */
    @SuppressWarnings({"unchecked", "ConstantConditions"})
    public <T> T edit(T instance, String name, Map<String, String[]> params) {

        try {

            BeanWrapper bw = new BeanWrapper(instance.getClass());

            Set<Field> fields = new HashSet<Field>();
            Class clazz = instance.getClass();
            while (!clazz.equals(Object.class)) {
                Collections.addAll(fields, clazz.getDeclaredFields());
                clazz = clazz.getSuperclass();
            }

            for (Field field : fields) {

                field.setAccessible(true);

                Class<?> fieldType = field.getType();
                String fieldName = field.getName();
                String fieldPath = name + "." + fieldName;
                String[] fieldValues = params.get(fieldPath);
                boolean embedded = field.getAnnotation(Embedded.class) != null;
                boolean many = Collection.class.isAssignableFrom(fieldType) || fieldType.isArray();

                if (Key.class.isAssignableFrom(fieldType)) {
                    if (fieldValues != null && fieldValues.length > 0) {
                        if (!fieldValues[0].equals("")) {
                            params.remove(fieldPath);
                            String rawId = fieldValues[0];
                            Key key = ObjectifyService.getKey(rawId);
                            bw.set(fieldName, instance, key);
                        }
                        else {
                            bw.set(fieldName, instance, null);
                            params.remove(fieldPath);
                        }
                    }
                }
                else if (many) {
                    if (fieldValues == null && getParamsByKeyPrefix(params, fieldPath + "[0]").size() == 0) {
                        bw.set(fieldName, instance, null);
                    }
                    else {
                        Class fieldManyRawType = getCollectionFieldRawType(field);
                        if (fieldManyRawType != null) {
                            if (Key.class.isAssignableFrom(fieldManyRawType)) {
                                Collection collection = newCollection(field, fieldPath);
                                for (String rawId : fieldValues) {
                                    Key key = ObjectifyService.getKey(rawId);
                                    collection.add(key);
                                }
                                Object collectionOrArray = convertToArrayIfRequired(fieldType, fieldManyRawType, collection);
                                bw.set(fieldName, instance, collectionOrArray);
                                params.remove(fieldPath);
                            }
                            else if (embedded) {
                                int i = 0;
                                Collection collection = newCollection(field, fieldPath);
                                Map<String, String[]> paramsNested;
                                while (true) {
                                    String fieldPathNested = fieldPath + "[" + i + "]";
                                    paramsNested = getParamsByKeyPrefix(params, fieldPathNested);
                                    if (paramsNested.size() > 0) {
                                        Object fieldValue = ObjectifyService.instantiate(fieldManyRawType);
                                        fieldValue = edit(fieldValue, fieldPathNested, paramsNested);
                                        collection.add(fieldValue);
                                        i++;
                                    }
                                    else {
                                        break;
                                    }
                                }
                                Object collectionOrArray = convertToArrayIfRequired(fieldType, fieldManyRawType, collection);
                                bw.set(fieldName, instance, collectionOrArray);
                            }
                            else if (isSimpleType(fieldManyRawType)) {
                                Collection collection = newCollection(field, fieldPath);
                                for (String fieldValue : fieldValues) {
                                    Object convertedFieldValue;
                                    if (fieldManyRawType.isEnum()) {
                                        convertedFieldValue = getEnumValue(fieldValue, fieldManyRawType);
                                    }
                                    else {
                                        convertedFieldValue = Binder.directBind(fieldValue, fieldManyRawType);
                                    }
                                    collection.add(convertedFieldValue);
                                }
                                Object collectionOrArray = convertToArrayIfRequired(fieldType, fieldManyRawType, collection);
                                bw.set(fieldName, instance, collectionOrArray);
                                params.remove(fieldPath);
                            }
                            else {
                                throw new UnexpectedException("Unable to bind: " + instance.getClass() + ", " + fieldPath + " is a neither Key<T>, @Embedded, Enum or simple collection");
                            }
                        }
                        else {
                            throw new UnexpectedException("Unable to bind: " + instance.getClass() + ", " + fieldPath + " is a non-parameterized collection");
                        }
                    }
                }
                else if (embedded) {
                    Object fieldValue = ObjectifyService.instantiate(fieldType);
                    fieldValue = edit(fieldValue, fieldPath, params);
                    bw.set(fieldName, instance, fieldValue);
                }

            }

            bw.bind(name, instance.getClass(), params, "", instance, null);

            return instance;

        }
        catch (Exception e) {
            throw new UnexpectedException("Unable to bind: " + instance.getClass() + ", " + e.getMessage(), e);
        }

    }

    /**
     * Creates a new {@link Collection} for a given {@link Field}. This method only supports {@link List},
     * {@link Set} and native Java arrays.
     *
     * @param field the field
     * @param fieldPath the field path
     * @return the new Collection
     */
    public static Collection newCollection(Field field, String fieldPath) {
        Class<?> type = field.getType();
        if (List.class.isAssignableFrom(type) || type.isArray()) {
            return new ArrayList();
        }
        else if (Set.class.isAssignableFrom(type)) {
            return new HashSet();
        }
        else {
            throw new UnexpectedException("Unable to instantiate Collection as it is neither List, Set or Array: " + fieldPath + ", " + type.getName());
        }
    }

    /**
     * Obtains the value of a given enum given its String name.
     *
     * @param value the value
     * @param clazz the enum class
     * @return the enum instance
     */
    public static <T extends Enum<T>> T getEnumValue(String value, Class<T> clazz) {
        return Enum.valueOf(clazz, value);
    }

    /**
     * Converts a given {@link Collection} to a native Java array if mandated by the field type.
     *
     * @param fieldType the field type
     * @param rawType the raw type when creating the array
     * @param collection the source Collection
     * @return the source Collection or the converted array of raw types
     */
    @SuppressWarnings({"unchecked"})
    public static Object convertToArrayIfRequired(Class fieldType, Class rawType, Collection collection) {
        if (fieldType.isArray()) {
            Object[] array = (Object[]) Array.newInstance(rawType, collection.size());
            return collection.toArray(array);
        }
        else {
            return collection;
        }
    }

    /**
     * Obtains the collection or array raw type for a given field which is a native Java array
     * or a generic {@link Collection}.
     *
     * @param field the field
     * @return the raw type or null if the input is invalid
     */
    public static Class getCollectionFieldRawType(Field field) {
        if (field.getType().isArray()) {
            return field.getType().getComponentType();
        }
        else {
            Type type = field.getGenericType();
            if (type instanceof ParameterizedType) {
                ParameterizedType genericType = (ParameterizedType) field.getGenericType();
                Type[] args = genericType.getActualTypeArguments();
                if (args != null && args.length > 0 && args[0] != null) {
                    return (Class) args[0];
                }
            }
        }
        return null;
    }

    /**
     * Creates a {@link Map} containing a subset of parameters matching the given key prefix
     * from the supplied parameters.
     *
     * @param params the params map
     * @param keyPrefix the key prefix
     * @return the map containing matching params
     */
    public static Map<String, String[]> getParamsByKeyPrefix(Map<String, String[]> params, String keyPrefix) {
        Map<String, String[]> newParams = new HashMap<String, String[]>();
        Set<Map.Entry<String, String[]>> entries = params.entrySet();
        for (Map.Entry<String, String[]> entry : entries) {
            String key = entry.getKey();
            String[] value = entry.getValue();
            if (key.startsWith(keyPrefix)) {
                newParams.put(key, value);
            }
        }
        return newParams;
    }

    /**
     * Obtains the key type given an entity class containing a field suitably annotated with {@link Id}.
     *
     * @param clazz the entity class
     * @return the key type
     */
    public static Class findKeyType(Class clazz) {
        try {
            while (!clazz.equals(Object.class)) {
                for (Field field : clazz.getDeclaredFields()) {
                    if (field.isAnnotationPresent(Id.class)) {
                        field.setAccessible(true);
                        return field.getType();
                    }
                }
                clazz = clazz.getSuperclass();
            }
        }
        catch (Exception e) {
            throw new UnexpectedException("Error while determining the @Id for an object of type: " + clazz);
        }
        return null;
    }

    /**
     * Returns true if the input string is non-null and contains only numeric characters.
     *
     * @param str the input string
     * @return true if numeric, false otherwise
     */
    public static boolean isNumeric(String str) {
        if (str == null) {
            return false;
        }
        int length = str.length();
        for (int i = 0; i < length; i++) {
            if (!Character.isDigit(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if the type supplied is "simple".
     *
     * @param type the type
     * @return true if simple, false otherwise
     */
    public boolean isSimpleType(Class type) {
        return String.class.equals(type) ||
                Number.class.isAssignableFrom(type) ||
                type.isPrimitive() ||
                type.isEnum() ||
                Date.class.equals(type) ||
                Boolean.class.isAssignableFrom(type) ||
                boolean.class.isAssignableFrom(type)
                ;
    }

}
