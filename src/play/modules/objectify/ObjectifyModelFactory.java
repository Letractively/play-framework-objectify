package play.modules.objectify;

import com.google.appengine.api.datastore.QueryResultIterable;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Query;
import play.Logger;
import play.db.Model;
import play.exceptions.UnexpectedException;
import play.libs.I18N;

import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Transient;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author David Cheong
 * @since 10/10/2010
 */
public class ObjectifyModelFactory implements Model.Factory {

    protected Class<? extends Model> clazz;

    public ObjectifyModelFactory(Class<? extends Model> clazz) {
        this.clazz = clazz;
    }

    public String keyName() {
        return keyField().getName();
    }

    public Class keyType() {
        return String.class;
    }

    public Object keyValue(Model m) {
        return ((ObjectifyModel) m).getKeyStr();
    }

    Field keyField() {
        Class c = clazz;
        try {
            while (!c.equals(Object.class)) {
                for (Field field : c.getDeclaredFields()) {
                    if (field.isAnnotationPresent(Id.class)) {
                        field.setAccessible(true);
                        return field;
                    }
                }
                c = c.getSuperclass();
            }
        }
        catch (Exception e) {
            throw new UnexpectedException("Error while determining the object @Id for an object of type " + clazz);
        }
        throw new UnexpectedException("Cannot get the object @Id for an object of type " + clazz);
    }

    @SuppressWarnings({"unchecked"})
    public Model findById(Object id) {
        Key<?> key = Datastore.key((String) id);
        return (Model) Datastore.find(key, false);
    }

    public List<Model> fetch(int offset, int length, String orderBy, String orderDirection, List<String> properties, String keywords, String where) {
        Query<? extends Model> query = getSearchQuery(keywords, where);
        if (orderBy != null && orderBy.length() > 0) {
            if ("DESC".equalsIgnoreCase(orderDirection)) {
                query.order("-" + orderBy);
            }
            else {
                query.order(orderBy);
            }
        }
        List<Model> list = new ArrayList<Model>();
        QueryResultIterable<? extends Model> itr = query.fetch();
        for (Model model : itr) {
            list.add(model);
        }
        return list;
    }

    public Long count(List<String> properties, String keywords, String where) {
        Query<? extends Model> query = getSearchQuery(keywords, where);
        return (long) query.countAll();
    }

    protected Query<? extends Model> getSearchQuery(String keywords, String where) {
        Query<? extends Model> query = Datastore.query(clazz);
        if (keywords != null && keywords.length() > 0) {
            String[] keyWordsAsArray = keywords.split(" ");
            List<SearchFieldValue> searchFieldValues = new ArrayList<SearchFieldValue>();
            String key = null;
            String value = "";
            for (String keyword : keyWordsAsArray) {
                if (key == null) {
                    int delim = keyword.indexOf(":");
                    if (delim != -1 && delim > 0 && delim < keyword.length() - 1) {
                        String fieldName = keyword.substring(0, delim);
                        String fieldValue = keyword.substring(delim + 1);
                        if (!fieldValue.startsWith("\"")) {
                            searchFieldValues.add(new SearchFieldValue(fieldName, fieldValue));
                        }
                        else {
                            key = fieldName;
                            if (fieldValue.length() > 1) {
                                if (fieldValue.endsWith("\"")) {
                                    value = fieldValue.substring(1, fieldValue.length() - 1);

                                }
                                else {
                                    value = fieldValue.substring(1);
                                }
                            }
                            else {
                                value = "";
                            }
                        }
                    }
                }
                else {
                    if (keyword.endsWith("\"")) {
                        value += " " + (keyword.length() > 1 ? keyword.substring(0, keyword.length() - 1) : "");
                        searchFieldValues.add(new SearchFieldValue(key, value));
                        key = null;
                        value = "";
                    }
                    else {
                        value += " " + keyword;
                    }
                }
            }
            if (key != null) {
                searchFieldValues.add(new SearchFieldValue(key, value));
            }
            boolean hasInequalityFilter = false;
            for (SearchFieldValue searchFieldValue : searchFieldValues) {
                String fieldName = searchFieldValue.name;
                String fieldValue = searchFieldValue.value;
                Field field = findField(fieldName);
                if (field != null) {
                    Class<?> type = field.getType();
                    if (type.equals(String.class)) {
                        if (!hasInequalityFilter) {
                            query.filter(fieldName + " >=", fieldValue);
                            query.filter(fieldName + " <", fieldValue + "\uFFFD");
                            hasInequalityFilter = true;
                        }
                        else {
                            Logger.warn("Datastore only allows one inequality filter per query, search by '" + fieldName + "' is silently ignored");
                        }
                    }
                    else if (Integer.class.isAssignableFrom(type) || int.class.isAssignableFrom(type)) {
                        query.filter(fieldName, Integer.parseInt(fieldValue));
                    }
                    else if (Long.class.isAssignableFrom(type) || long.class.isAssignableFrom(type)) {
                        query.filter(fieldName, Long.parseLong(fieldValue));
                    }
                    else if (Float.class.isAssignableFrom(type) || float.class.isAssignableFrom(type)) {
                        query.filter(fieldName, Float.parseFloat(fieldValue));
                    }
                    else if (Double.class.isAssignableFrom(type) || double.class.isAssignableFrom(type)) {
                        query.filter(fieldName, Double.parseDouble(fieldValue));
                    }
                    else if (Boolean.class.isAssignableFrom(type) || boolean.class.isAssignableFrom(type)) {
                        query.filter(fieldName, Boolean.valueOf(fieldValue));
                    }
                    else if (Date.class.equals(type)) {
                        try {
                            Date date = new SimpleDateFormat(I18N.getDateFormat()).parse(fieldValue);
                            query.filter(fieldName, date);
                        }
                        catch (ParseException e) {
                            // ignored
                        }
                    }
                    else if (type.isEnum()) {
                        query.filter(fieldName, fieldValue);
                    }
                }
            }
        }
        if (where != null && where.length() > 0) {
            // ignored - this is a legacy feature as per
            // http://groups.google.com/group/play-framework/browse_thread/thread/2acd3843ebe35575
            Logger.warn("'where' argument is legacy feature - it is not supported");
        }
        return query;
    }

    public void deleteAll() {
        boolean hasOne = true;
        while (hasOne) {
            QueryResultIterable<? extends Model> itr = Datastore
                    .query(clazz)
                    .limit(50)
                    .fetch();
            if (!itr.iterator().hasNext()) {
                hasOne = false;
            }
            for (Model model : itr) {
                Datastore.delete(model);
            }
        }
    }

    protected Field findField(String fieldName) {
        String[] paths = fieldName.split("\\.");
        int index = 0;
        Class<?> tclazz = clazz;
        while (!tclazz.equals(Object.class)) {
            boolean goToSuperClass = true;
            Field[] fields = tclazz.getDeclaredFields();
            for (Field field : fields) {
                if (field.getName().equals(paths[index])) {
                    field.setAccessible(true);
                    if (index == paths.length - 1) {
                        return field;
                    }
                    else {
                        index++;
                        tclazz = field.getType();
                        goToSuperClass = false;
                    }
                }
            }
            if (goToSuperClass) {
                tclazz = tclazz.getSuperclass();
            }
        }
        return null;
    }

    public List<Model.Property> listProperties() {
        List<Model.Property> properties = new ArrayList<Model.Property>();
        Set<Field> fields = new HashSet<Field>();
        Class<?> tclazz = clazz;
        while (!tclazz.equals(Object.class)) {
            Collections.addAll(fields, tclazz.getDeclaredFields());
            tclazz = tclazz.getSuperclass();
        }
        for (Field f : fields) {
            if (Modifier.isTransient(f.getModifiers())) {
                continue;
            }
            if (f.isAnnotationPresent(Transient.class)) {
                continue;
            }
            if (Collection.class.isAssignableFrom(f.getType())) {
                continue;
            }
            Model.Property mp = buildProperty(f);
            if (mp != null) {
                properties.add(mp);
            }
        }
        return properties;
    }

    protected Model.Property buildProperty(final Field field) {
        Model.Property modelProperty = new Model.Property();
        final Class<?> type = field.getType();
        modelProperty.type = type;
        modelProperty.field = field;
        modelProperty.name = field.getName();
        if (Model.class.isAssignableFrom(type)) {
            modelProperty.isRelation = true;
            modelProperty.isSearchable = true;
            modelProperty.relationType = type;
            modelProperty.choices = new Model.Choices() {
                @SuppressWarnings("unchecked")
                public List<Object> list() {
                    List<Object> list = new ArrayList<Object>();
                    QueryResultIterable<?> iterable = Datastore.query(type).fetch();
                    for (Object object : iterable) {
                        list.add(object);
                    }
                    return list;
                }
            };
        }
        if (type.isEnum()) {
            modelProperty.choices = new Model.Choices() {
                @SuppressWarnings({"unchecked", "RedundantCast"})
                public List<Object> list() {
                    return (List<Object>) Arrays.asList(type.getEnumConstants());
                }
            };
        }
        if (type.equals(String.class) ||
                Integer.class.isAssignableFrom(type) || int.class.isAssignableFrom(type) ||
                Long.class.isAssignableFrom(type) || long.class.isAssignableFrom(type) ||
                Float.class.isAssignableFrom(type) || float.class.isAssignableFrom(type) ||
                Double.class.isAssignableFrom(type) || double.class.isAssignableFrom(type) ||
                Boolean.class.isAssignableFrom(type) || boolean.class.isAssignableFrom(type) ||
                Date.class.equals(type) ||
                type.isEnum()) {
            modelProperty.isSearchable = true;
        }
        if (field.isAnnotationPresent(GeneratedValue.class)) {
            modelProperty.isGenerated = true;
            modelProperty.isSearchable = true;
        }
        return modelProperty;
    }

    public static class SearchFieldValue {

        public String name;
        public String value;

        SearchFieldValue(String name, String value) {
            this.name = name;
            this.value = value;
        }

    }

}
