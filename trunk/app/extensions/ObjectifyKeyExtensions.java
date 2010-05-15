package extensions;

import com.googlecode.objectify.Key;
import play.modules.objectify.ObjectifyService;
import play.templates.JavaExtensions;

/**
 * @author David Cheong
 * @since 23/04/2010
 */
public class ObjectifyKeyExtensions extends JavaExtensions {

    public static String str(Key<?> key) {
        return ObjectifyService.keyStr(key);
    }

    public static <T> T fetch(Key<T> key) {
        return ObjectifyService.find(key, false);
    }
    
}
