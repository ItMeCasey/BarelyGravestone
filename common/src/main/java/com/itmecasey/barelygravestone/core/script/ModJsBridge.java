package com.itmecasey.barelygravestone.core.script;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.logging.Logger;

/**
 * Gives ModJS scripts a global {@code BarelyGravestone} (the {@link ScriptApi}). ModJS is optional, so everything here goes
 * through reflection: ModJS offers {@code ExtraBindings.register(BindingProvider)} and the provider's
 * {@code install(type, cx, scope)} adds globals to a script scope. Without ModJS nothing is loaded and nothing fails.
 *
 * <p>The global exists in server and startup scripts only (graves live on the server). ModJS builds a fresh scope for each script
 * type on every load and reload, so the handlers are cleared when the server scripts are installed again.
 */
public final class ModJsBridge {
    private static final Logger LOG = Logger.getLogger("barelygravestone");

    private ModJsBridge() {
    }

    /** Installs the binding when ModJS is on the classpath; returns whether it did. */
    public static boolean install(ScriptApi api) {
        try {
            Class<?> provider = Class.forName("com.itmecasey.modjs.script.BindingProvider");
            Class<?> extra = Class.forName("com.itmecasey.modjs.script.ExtraBindings");
            Class<?> context = Class.forName("org.mozilla.javascript.Context");
            Class<?> scriptable = Class.forName("org.mozilla.javascript.Scriptable");
            Class<?> scriptableObject = Class.forName("org.mozilla.javascript.ScriptableObject");
            Method javaToJs = context.getMethod("javaToJS", Object.class, scriptable);
            Method put = scriptableObject.getMethod("putProperty", scriptable, String.class, Object.class);
            InvocationHandler handler = (proxy, method, args) -> {
                if (method.getName().equals("install") && args != null && args.length == 3) {
                    String type = scriptType(args[0]);
                    if (type.equals("CLIENT")) {
                        return null;
                    }
                    if (type.equals("SERVER")) {
                        ScriptHooks.clear();
                    }
                    put.invoke(null, args[2], "BarelyGravestone", javaToJs.invoke(null, api, args[2]));
                    return null;
                }
                return switch (method.getName()) {
                    case "toString" -> "BarelyGravestone binding";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                };
            };
            Object binding = Proxy.newProxyInstance(provider.getClassLoader(), new Class<?>[] {provider}, handler);
            extra.getMethod("register", provider).invoke(null, binding);
            LOG.info("Barely Gravestone: ModJS scripts can use the BarelyGravestone global");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOG.warning("Barely Gravestone: could not add the BarelyGravestone global to ModJS: " + e);
            return false;
        }
    }

    /** The script type's name ({@code SERVER}, {@code STARTUP}, {@code CLIENT}), or an empty string for anything unknown. */
    static String scriptType(Object type) {
        if (type instanceof Enum<?> e) {
            return e.name();
        }
        return type == null ? "" : type.toString().toUpperCase(java.util.Locale.ROOT);
    }
}
