package play.modules.objectify;

import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.datastore.KeyFactory;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.Query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author David Cheong
 * @since 20/04/2010
 */
public class ObjectifyService {

    protected static ObjectifyFactory factory = new ObjectifyFactory();

    protected static List<Objectify> stack = new ArrayList<Objectify>();

    public static <T> T instantiate(Class<T> clazz) {
        return (T) instantiate(clazz.getName());
    }

    public static <T> T instantiate(String className) {
        try {
            return (T) ObjectifyFactory.loadClass(className).newInstance();
        }
        catch (InstantiationException e) {
            throw new RuntimeException("Unable to create new instance of " + className, e);
        }
        catch (IllegalAccessException e) {
            throw new RuntimeException("Unable to create new instance of " + className, e);
        }
    }

    public static <T> Map<Key<T>, T> get(Iterable<? extends Key<? extends T>> keys) {
        return objectify().get(keys);
    }

    public static <T> T get(Key<? extends T> key) throws EntityNotFoundException {
        return objectify().get(key);
    }

    public static <T> T get(String className, Long id) throws EntityNotFoundException {
        return (T) get(loadClass(className), id);
    }

    public static <T> T get(String className, String name) throws EntityNotFoundException {
        return (T) get(loadClass(className), name);
    }

    public static <T> T get(Class<? extends T> clazz, Long id) throws EntityNotFoundException {
        return objectify().get(clazz, id);
    }

    public static <T> T get(Class<? extends T> clazz, String name) throws EntityNotFoundException {
        return objectify().get(clazz, name);
    }

    public static <S, T> Map<S, T> get(Class<? extends T> clazz, Iterable<S> idsOrNames) {
        return objectify().get(clazz, idsOrNames);
    }

    public static <T> T find(Key<? extends T> key, boolean newIfNull) {
        T instance = null;
        if (key != null && key.getId() != 0) {
            instance = objectify().find(key);
        }
        if (instance == null && newIfNull) {
            instance = (T) instantiate(key.getKindClassName());
        }
        return objectify().find(key);
    }

    public static <T> T find(String className, Long id, boolean newIfNull) {
        return (T) find(loadClass(className), id, newIfNull);
    }

    public static <T> T find(String className, String name, boolean newIfNull) {
        return (T) find(loadClass(className), name, newIfNull);
    }

    public static <T> T find(Class<? extends T> clazz, Long id, boolean newIfNull) {
        T instance = null;
        if (id != null && id != 0) {
            instance = objectify().find(clazz, id);
        }
        if (instance == null && newIfNull) {
            instance = instantiate(clazz);
        }
        return instance;
    }

    public static <T> T find(Class<? extends T> clazz, String name, boolean newIfNull) {
        T instance = null;
        if (name != null && name.length() != 0) {
            instance = objectify().find(clazz, name);
        }
        if (instance == null && newIfNull) {
            instance = instantiate(clazz);
        }
        return instance;
    }

    public static <T> Key<T> put(T obj) {
        return objectify().put(obj);
    }

    public static <T> Map<Key<T>, T> put(Iterable<? extends T> objs) {
        return objectify().put(objs);
    }

    public static void delete(Object keyOrEntity) {
        objectify().delete(keyOrEntity);
    }

    public static void delete(Iterable<?> keysOrEntities) {
        objectify().delete(keysOrEntities);
    }

    public static <T> void delete(Class<T> clazz, long id) {
        objectify().delete(clazz, id);
    }

    public static <T> void delete(Class<T> clazz, String name) {
        objectify().delete(clazz, name);
    }

    public static <T> Query<T> query() {
        return objectify().query();
    }

    public static <T> Query<T> query(Class<T> clazz) {
        return objectify().query(clazz);
    }

    public static Objectify objectify() {
        if (stack.isEmpty()) {
            begin();
        }
        return stack.get(0);
    }

    public static ObjectifyFactory factory() {
        return factory;
    }

    public static Objectify begin() {
        Objectify objectify = factory().begin();
        stack.add(0, objectify);
        return objectify;
    }

    public static Objectify beginTxn() {
        Objectify objectify = factory().beginTransaction();
        stack.add(0, objectify);
        return objectify;
    }

    public static void commit() {
        closeTxn(false);
    }

    /**
     * @noinspection ForLoopReplaceableByForEach
     */
    public static void commitAll() {
        for (int i = 0; i < stack.size(); i++) {
            closeTxn(false);
        }
    }

    public static void rollback() {
        closeTxn(true);
    }

    /**
     * @noinspection ForLoopReplaceableByForEach
     */
    public static void rollbackAll() {
        for (int i = 0; i < stack.size(); i++) {
            closeTxn(true);
        }
    }

    protected static void closeTxn(boolean rollback) {
        if (!stack.isEmpty()) {
            Objectify objectify = objectify();
            Transaction transaction = objectify.getTxn();
            if (transaction != null && transaction.isActive()) {
                if (rollback) {
                    transaction.rollback();
                }
                else {
                    transaction.commit();
                }
            }
            stack.remove(0);
        }
    }

    public static <T> Key<T> getKey(Object keyOrEntity) {
        return factory().getKey(keyOrEntity);
    }

    public static <T> Key<T> key(Object keyOrEntity) {
        return getKey(keyOrEntity);
    }

    public static <T> Key<T> getKey(Object... pairs) {
        return factory().getKey(pairs);
    }

    public static <T> Key<T> key(Object... pairs) {
        return getKey(pairs);
    }

    public static String getKeyStr(Object keyOrEntity) {
        return factory().getKeyStr(keyOrEntity);
    }

    public static String keyStr(Object keyOrEntity) {
        return getKeyStr(keyOrEntity);
    }

    public static void register(Class<?> clazz) {
        factory().register(clazz);
    }

    public static void register(String className) {
        factory().register(loadClass(className));
    }

    /**
     * @noinspection unchecked
     */
    public static <T> Class<T> loadClass(Class<T> clazz) {
        return (Class<T>) loadClass(clazz.getName());
    }

    public static <T> Class<T> loadClass(String className) {
        if (className.startsWith("models.")) {
            return ObjectifyFactory.loadClass(className);
        }
        else {
            return ObjectifyFactory.loadClass("models." + className);
        }
    }

    public static void setDatastoreTimeoutRetryCount(int value) {
        factory().setDatastoreTimeoutRetryCount(value);
    }

    public static int getDatastoreTimeoutRetryCount() {
        return factory().getDatastoreTimeoutRetryCount();
    }
}
