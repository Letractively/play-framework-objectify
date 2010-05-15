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
 * @author David Cheong
 * @since 29/04/2010
 */
public class ObjectifyBinder {

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

    /** @noinspection unchecked*/
    public <T extends ObjectifyModel> T create(Class clazz, String name, Map<String, String[]> params) {
        T instance = (T) ObjectifyService.instantiate(clazz);
        return edit(instance, name, params);
    }

    /** @noinspection ConstantConditions,unchecked */
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
                                Collection collection = newCollection(fieldPath, field);
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
                                Collection collection = newCollection(fieldPath, field);
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
                                Collection collection = newCollection(fieldPath, field);
                                for (String fieldValue : fieldValues) {
                                    Object convertedFieldValue = Binder.directBind(fieldValue, fieldManyRawType);
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

            bw.bind(name, instance.getClass(), params, "", instance);

            return instance;

        }
        catch (Exception e) {
            throw new UnexpectedException("Unable to bind: " + instance.getClass() + ", " + e.getMessage(), e);
        }

    }

    public static Collection newCollection(String fieldPath, Field field) {
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

    /** @noinspection unchecked*/
    public static Object convertToArrayIfRequired(Class fieldType, Class rawType, Collection collection) {
        if (fieldType.isArray()) {
            Object[] array = (Object[]) Array.newInstance(rawType, collection.size());
            return collection.toArray(array);
        }
        else {
            return collection;
        }
    }

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

    public static Class findKeyType(Class c) {
        try {
            while (!c.equals(Object.class)) {
                for (Field field : c.getDeclaredFields()) {
                    if (field.isAnnotationPresent(Id.class)) {
                        field.setAccessible(true);
                        return field.getType();
                    }
                }
                c = c.getSuperclass();
            }
        }
        catch (Exception e) {
            throw new UnexpectedException("Error while determining the @Id for an object of type: " + c);
        }
        return null;
    }

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
