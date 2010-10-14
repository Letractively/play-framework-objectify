package play.modules.objectify;

import play.exceptions.UnexpectedException;

import javax.persistence.Id;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Date;

/**
 * A class containing convenient utilities.
 *
 * @author David Cheong
 * @since 14/10/2010
 */
public class Utils extends play.utils.Utils {


    /**
     * Converts a given {@link java.util.Collection} to a native Java array if mandated by the field type.
     *
     * @param fieldType  the field type
     * @param rawClass   the raw class when creating the array
     * @param collection the source Collection
     * @return the source Collection or the converted array of raw types
     */
    @SuppressWarnings({"unchecked"})
    public static Object convertToArrayIfRequired(Class fieldType, Class rawClass, Collection collection) {
        if (fieldType.isArray()) {
            Object[] array = (Object[]) Array.newInstance(rawClass, collection.size());
            return collection.toArray(array);
        }
        else {
            return collection;
        }
    }

    /**
     * Obtains the raw class for a given field which must not be a collection.
     *
     * @param field the field
     * @return the raw type or null if the input is invalid
     */
    public static Class getSingleFieldRawClass(Field field) {
        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType) {
            Type[] args = ((ParameterizedType) genericType).getActualTypeArguments();
            if (args != null && args.length > 0 && args[0] != null) {
                return (Class) args[0];
            }
        }
        return null;
    }

    /**
     * Obtains the collection or array raw class for a given field which is a native Java array
     * or a generic {@link java.util.Collection}.
     *
     * @param field the field
     * @return the raw type or null if the input is invalid
     */
    public static Class getManyFieldRawClass(Field field) {
        if (field.getType().isArray()) {
            return field.getType().getComponentType();
        }
        else {
            Type type = field.getGenericType();
            if (type instanceof ParameterizedType) {
                ParameterizedType genericType = (ParameterizedType) field.getGenericType();
                Type[] args = genericType.getActualTypeArguments();
                if (args != null && args.length > 0 && args[0] != null) {
                    Type nestedType = args[0];
                    if (nestedType instanceof Class) {
                        return (Class) nestedType;
                    }
                    else {
                        return (Class) ((ParameterizedType) nestedType).getRawType();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Obtains the collection or array raw class for a given field which is a native Java array
     * or a generic {@link java.util.Collection}.
     *
     * @param field the field
     * @return the raw type or null if the input is invalid
     */
    public static Class getManyFieldRawType(Field field) {
        if (field.getType().isArray()) {
            return field.getType().getComponentType();
        }
        else {
            Type type = field.getGenericType();
            if (type instanceof ParameterizedType) {
                ParameterizedType genericType = (ParameterizedType) field.getGenericType();
                Type[] args = genericType.getActualTypeArguments();
                if (args != null && args.length > 0 && args[0] != null) {
                    Type nestedType = args[0];
                    if (nestedType instanceof Class) {
                        return (Class) nestedType;
                    }
                    else {
                        Type[] nestedArgs = ((ParameterizedType) nestedType).getActualTypeArguments();
                        if (nestedArgs != null && nestedArgs.length > 0 && nestedArgs[0] != null) {
                            return (Class) nestedArgs[0];
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Obtains the key field given an entity class containing a field suitably annotated with {@link javax.persistence.Id}.
     *
     * @param clazz the entity class
     * @return the key field
     */
    @SuppressWarnings({"unchecked"})
    public static Field getKeyField(Class<? extends ObjectifyModel> clazz) {
        try {
            while (!clazz.equals(Object.class)) {
                for (Field field : clazz.getDeclaredFields()) {
                    if (field.isAnnotationPresent(Id.class)) {
                        field.setAccessible(true);
                        return field;
                    }
                }
                clazz = (Class<? extends ObjectifyModel>) clazz.getSuperclass();
            }
        }
        catch (Exception e) {
            throw new UnexpectedException("Error while determining the @Id for an object of type: " + clazz);
        }
        return null;
    }

    /**
     * Obtains the key type given an entity class containing a field suitably annotated with {@link Id}.
     *
     * @param clazz the entity class
     * @return the key type
     */
    public static Class getKeyType(Class<? extends ObjectifyModel> clazz) {
        Field field = getKeyField(clazz);
        return field != null ? field.getType() : null;
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
    public static boolean isSimpleType(Class type) {
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
