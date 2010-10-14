package play.modules.objectify;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Query;
import play.Logger;
import play.db.Model;
import play.exceptions.UnexpectedException;
import play.libs.I18N;

import javax.persistence.Embedded;
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

    protected Class<Model> clazz;

    public ObjectifyModelFactory(Class<Model> clazz) {
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
        Query<Model> query = getSearchQuery(keywords, where);
        if (orderBy != null && orderBy.length() > 0) {
            if ("DESC".equalsIgnoreCase(orderDirection)) {
                query.order("-" + orderBy);
            }
            else {
                query.order(orderBy);
            }
        }
        return Utils.asList(query);
    }

    public Long count(List<String> properties, String keywords, String where) {
        Query<? extends Model> query = getSearchQuery(keywords, where);
        return (long) query.countAll();
    }

    protected Query<Model> getSearchQuery(String keywords, String where) {
        Query<Model> query = Datastore.query(clazz);
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
        boolean hasOne;
        do {
            hasOne = false;
            Query<? extends Model> query = Datastore
                    .query(clazz)
                    .limit(50);
            for (Model model : query) {
                Datastore.delete(model);
                hasOne = true;
            }
        } while (hasOne);
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
        for (Field field : fields) {
            Class<?> type = field.getType();
            if (Modifier.isTransient(field.getModifiers())) {
                continue;
            }
            if (field.isAnnotationPresent(Transient.class)) {
                continue;
            }
            if (Collection.class.isAssignableFrom(type) || type.isArray()) {
                Class rawClass = Utils.getManyFieldRawClass(field);
                if (!Key.class.isAssignableFrom(rawClass) && !rawClass.isEnum()) {
                    continue;
                }
            }
            Model.Property mp = buildProperty(field);
            if (mp != null) {
                properties.add(mp);
            }
        }
        return properties;
    }

    protected Model.Property buildProperty(final Field field) {

        final Model.Property modelProperty = new Model.Property();
        final Class<?> type = field.getType();

        boolean many = Collection.class.isAssignableFrom(type) || type.isArray();

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
                    Query<?> query = Datastore.query(modelProperty.relationType);
                    return (List<Object>) Utils.asList(query);
                }
            };
        }
        else if (Key.class.isAssignableFrom(type)) {
            modelProperty.isRelation = true;
            modelProperty.isSearchable = false;
            modelProperty.relationType = Utils.getSingleFieldRawClass(field);
            modelProperty.choices = new Model.Choices() {
                @SuppressWarnings("unchecked")
                public List<Object> list() {
                    Query<?> query = Datastore.query(modelProperty.relationType);
                    return (List<Object>) Utils.asList(query);
                }
            };
        }
        else if (many) {
            modelProperty.isMultiple = true;
            modelProperty.isRelation = true;
            modelProperty.isSearchable = false;
            final Class rawClass = Utils.getManyFieldRawClass(field);
            final Class rawType = Utils.getManyFieldRawType(field);
            if (Key.class.isAssignableFrom(rawClass)) {
                modelProperty.relationType = rawType;
                modelProperty.choices = new Model.Choices() {
                    @SuppressWarnings("unchecked")
                    public List<Object> list() {
                        Query<?> query = Datastore.query(modelProperty.relationType);
                        return (List<Object>) Utils.asList(query);
                    }
                };
            }
            else if (rawType.isEnum()) {
                modelProperty.relationType = rawType;
                modelProperty.choices = new Model.Choices() {
                    @SuppressWarnings("unchecked")
                    public List<Object> list() {
                        return (List<Object>) Arrays.asList(rawType.getEnumConstants());
                    }
                };
            }
            else {
                modelProperty.relationType = rawType;
                // crud api does not make this possible
                throw new UnexpectedException("CRUD does not allow non-Key collections from being managed");
            }
        }
        else if (type.isEnum()) {
            modelProperty.isSearchable = true;
            modelProperty.choices = new Model.Choices() {
                @SuppressWarnings({"unchecked", "RedundantCast"})
                public List<Object> list() {
                    return (List<Object>) Arrays.asList(type.getEnumConstants());
                }
            };
        }
        else if (field.getAnnotation(Embedded.class) != null) {
            modelProperty.relationType = field.getType();
            modelProperty.isSearchable = true;
        }
        else if (Utils.isSimpleType(type)) {
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
