package play.modules.objectify;

import com.google.appengine.api.datastore.KeyFactory;
import com.googlecode.objectify.Key;
import play.Play;

/**
 * @author David Cheong
 * @since 21/04/2010
 */
public class ObjectifyFactory extends com.googlecode.objectify.ObjectifyFactory {

    @Override
    public String getKind(String className) {
        return super.getKind(loadClass(className));
    }

    public static <T> Class<T> loadClass(Class<T> clazz) {
        return loadClass(clazz.getName());
    }

    public static <T> Class<T> loadClass(String name) {
        try {
            return (Class<T>) Play.classloader.loadClass(name);
        }
        catch (ClassNotFoundException e) {
            throw new RuntimeException("Unable to load class: " + name, e);
        }
    }

    public <T> Key<T> getKey(Object keyOrEntity) {
        if (keyOrEntity instanceof String) {
            return rawKeyToTypedKey(KeyFactory.stringToKey((String) keyOrEntity));
        }
        else if (keyOrEntity.getClass().isArray()) {
            return getKey((Object[]) keyOrEntity);
        }
        return super.getKey(keyOrEntity);
    }

    @SuppressWarnings({"unchecked"})
    public <T> Key<T> getKey(Object... pairs) {
        Key<?> current = null;
        if (pairs != null) {
            int len = pairs.length;
            if (len % 2 == 1) {
                throw new IllegalArgumentException("Argument not pairs, length: " + len);
            }
            for (int i = 0; i < len; i += 2) {
                Class<?> kind = (Class<?>) pairs[i];
                Object idOrName = pairs[i + 1];
                if (kind == null) {
                    throw new IllegalArgumentException("Key kind must not be null");
                }
                if (idOrName instanceof Long) {
                    current = new Key(current, kind, (Long) idOrName);
                }
                else if (idOrName instanceof String) {
                    current = new Key(current, kind, (String) idOrName);
                }
                else if (idOrName == null) {
                    throw new IllegalArgumentException("Key id must not be null");
                }
                else {
                    throw new IllegalArgumentException("Key id must be either Long or String");
                }
            }
        }
        return (Key<T>) current;
    }

    public String getKeyStr(Object keyOrEntity) {
        try {
            return keyOrEntity == null ? null : KeyFactory.keyToString(getRawKey(keyOrEntity));
        }
        catch (Exception e) {
            return null;
        }
    }

    public com.google.appengine.api.datastore.Key getRawKey(Object keyOrEntity) {
        if (keyOrEntity instanceof String) {
            return KeyFactory.stringToKey((String) keyOrEntity);
        }
        return super.getRawKey(keyOrEntity);
    }

}
