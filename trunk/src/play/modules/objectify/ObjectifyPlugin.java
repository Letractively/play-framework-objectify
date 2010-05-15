package play.modules.objectify;

import play.Play;
import play.PlayPlugin;
import play.exceptions.UnexpectedException;
import play.modules.gae.GAEPlugin;
import play.mvc.Scope;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * @author David Cheong
 * @since 20/04/2010
 */
public class ObjectifyPlugin extends PlayPlugin {

    protected static Boolean prod;

    protected void setup() {
        String models = Play.configuration.getProperty("objectify.models");
        if (models != null) {
            String[] modelsArray = models.split(",");
            for (String model : modelsArray) {
                ObjectifyService.register("models." + model.trim());
            }
        }
    }

    protected boolean isProd() {
        if (prod == null) {
            List<PlayPlugin> plugins = Play.plugins;
            for (PlayPlugin plugin : plugins) {
                if (plugin instanceof GAEPlugin) {
                    prod = ((GAEPlugin) plugin).prodGAE;
                    return prod;
                }
            }
            throw new IllegalStateException("Unable to determine GAE environment as GAEPlugin was not detected");
        }
        else {
            return prod;
        }
    }

    @Override
    public void onApplicationStart() {
        if (isProd()) {
            setup();
        }
    }

    @Override
    public Object bind(String name, Class clazz, Type type, Map<String, String[]> params) {
        String binderClassName = Play.configuration.getProperty("objectify.binder", ObjectifyBinder.class.getName());
        try {
            Class<? extends ObjectifyBinder> binderClass = (Class<? extends ObjectifyBinder>) Play.classloader.loadClass(binderClassName);
            ObjectifyBinder binder = binderClass.newInstance();
            Object result = binder.bind(name, clazz, type, params);
            return result == null ? super.bind(name, clazz, type, params) : result;
        }
        catch (Exception e) {
            throw new UnexpectedException("Unable to bind via binder: " + binderClassName + "," + e.getMessage(), e);
        }
    }

    @Override
    public void beforeInvocation() {
        if (!isProd()) {
            setup();
        }
    }

    @Override
    public void afterInvocation() {
        ObjectifyService.commitAll();
    }

    @Override
    public void beforeActionInvocation(Method actionMethod) {
        Scope.RenderArgs renderArgs = Scope.RenderArgs.current();
        ObjectifyService objectifyService = new ObjectifyService();
        renderArgs.put("Datastore", objectifyService);
        renderArgs.put("ofy", objectifyService);
    }

    @Override
    public void afterActionInvocation() {
    }

    @Override
    public void onInvocationException(Throwable e) {
        ObjectifyService.rollbackAll();
    }

    @Override
    public void invocationFinally() {
    }

}
