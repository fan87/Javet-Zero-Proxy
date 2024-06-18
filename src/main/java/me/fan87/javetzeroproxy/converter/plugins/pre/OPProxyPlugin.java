package me.fan87.javetzeroproxy.converter.plugins.pre;

import com.caoccao.javet.entities.JavetEntityPropertyDescriptor;
import com.caoccao.javet.enums.V8ValueSymbolType;
import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interfaces.IJavetBiFunction;
import com.caoccao.javet.interfaces.IJavetEntityPropertyDescriptor;
import com.caoccao.javet.interfaces.IJavetEntitySymbol;
import com.caoccao.javet.interfaces.IJavetUniFunction;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.interop.V8Scope;
import com.caoccao.javet.interop.binding.IClassProxyPlugin;
import com.caoccao.javet.interop.binding.IClassProxyPluginFunction;
import com.caoccao.javet.interop.callback.IJavetDirectCallable;
import com.caoccao.javet.interop.callback.JavetCallbackContext;
import com.caoccao.javet.interop.callback.JavetCallbackType;
import com.caoccao.javet.interop.proxy.IJavetDirectProxyHandler;
import com.caoccao.javet.interop.proxy.IJavetProxyHandler;
import com.caoccao.javet.interop.proxy.JavetDirectProxyObjectHandler;
import com.caoccao.javet.interop.proxy.plugins.*;
import com.caoccao.javet.utils.JavetResourceUtils;
import com.caoccao.javet.utils.StringUtils;
import com.caoccao.javet.utils.V8ValueUtils;
import com.caoccao.javet.values.V8Value;
import com.caoccao.javet.values.primitive.V8ValueBoolean;
import com.caoccao.javet.values.primitive.V8ValueLong;
import com.caoccao.javet.values.primitive.V8ValueString;
import com.caoccao.javet.values.reference.*;
import com.caoccao.javet.values.reference.builtin.V8ValueBuiltInObject;
import com.caoccao.javet.values.reference.builtin.V8ValueBuiltInSymbol;
import me.fan87.javetzeroproxy.converter.CustomJavetProxyConverter;
import me.fan87.javetzeroproxy.converter.AbstractV8ObjectPlugin;

import java.util.*;
import java.util.stream.Collectors;

import static me.fan87.javetzeroproxy.converter.prototype.JSClassPrototypeMaker.PRIVATE_PROPERTY_CLASS_WRAPPER_OBJECT;
import static me.fan87.javetzeroproxy.converter.prototype.JSClassPrototypeMaker.PRIVATE_PROPERTY_NATIVE_OBJECT_HANDLE_PROXY;

public class OPProxyPlugin extends AbstractV8ObjectPlugin {

    protected static final IClassProxyPlugin[] DEFAULT_PROXY_PLUGINS = new IClassProxyPlugin[]{
        JavetProxyPluginMap.getInstance(),
        JavetProxyPluginSet.getInstance(),
        JavetProxyPluginList.getInstance(),
        JavetProxyPluginArray.getInstance(),
    };

    private final List<IClassProxyPlugin> plugins = new ArrayList<>();
    private final List<ProxyPluginHandler> directHandler = new ArrayList<>();
    private final List<V8ValueObject> handlers = new ArrayList<>();

    public OPProxyPlugin(CustomJavetProxyConverter converter, V8Runtime runtime) {
        plugins.addAll(Arrays.asList(DEFAULT_PROXY_PLUGINS));
        directHandler.addAll(Arrays.stream(DEFAULT_PROXY_PLUGINS).map(it -> new ProxyPluginHandler(converter, runtime, it)).collect(Collectors.toList()));
        for (ProxyPluginHandler javetDirectProxyHandler : directHandler) {
            try {
                V8ValueObject v8ValueObject = runtime.createV8ValueObject();
                javetDirectProxyHandler.setV8Runtime(runtime);
                IJavetProxyHandler<?, ?> javetProxyHandler =
                    new JavetDirectProxyObjectHandler<>(runtime, javetDirectProxyHandler);

                List<JavetCallbackContext> javetCallbackContexts = v8ValueObject.bind(javetProxyHandler);
                try (V8ValueLong v8ValueLongHandle = runtime.createV8ValueLong(javetCallbackContexts.get(0).getHandle())) {
                    v8ValueObject.setPrivateProperty(PRIVATE_PROPERTY_PROXY_TARGET, v8ValueLongHandle);
                }
                handlers.add(v8ValueObject);
            } catch (JavetException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public V8Value apply(CustomJavetProxyConverter converter, V8Runtime runtime, V8Value output, Object object, Aborter aborter) throws JavetException {
        aborter.abort();
        output.close();

        V8Value originalBinding = converter.getBindingOf(object);
        if (originalBinding != null) return originalBinding;

        Class<?> objectClass = object.getClass();
        int idx = 0;
        for (int i = 0; i < plugins.size(); i++) {
            if (plugins.get(i).isProxyable(objectClass)) {
                idx = i;
                break;
            }
        }
        V8ValueObject handler = handlers.get(idx);
        ProxyPluginHandler javetDirectProxyHandler = directHandler.get(idx);
        V8Value v8Value;

        try (V8Scope v8Scope = runtime.getV8Scope()) {
            V8ValueObject finalOutput;
            V8Value targetObject = null;
            try {
                targetObject = javetDirectProxyHandler.createTargetObject();
                if (targetObject == null) {
                    targetObject = v8Scope.createV8ValueObject();
                }
                V8ValueProxy proxy = v8Scope.createV8ValueProxy(targetObject);

                if (converter.scriptManager.getUseSameBinding()) {
                    long handle = converter.bindObject(proxy, object);
                    ((V8ValueObject) targetObject).setPrivateProperty(PRIVATE_PROPERTY_NATIVE_OBJECT_HANDLE_PROXY, handle);
                } else {
                    converter.bindObject(targetObject, object);
                }

                try (IV8ValueObject iV8ValueObjectHandler = proxy.getHandler()) {
                    V8ValueBuiltInObject builtinObject = getBuiltinObject(runtime);
                    builtinObject.setPrototypeOf((V8Value) iV8ValueObjectHandler, handler).close();
                }
                finalOutput = proxy;

            } finally {
                JavetResourceUtils.safeClose(targetObject);
            }
            v8Value = finalOutput;
            v8Scope.setEscapable();
        }
        return v8Value;

    }

    @Override
    public boolean canApply(CustomJavetProxyConverter converter, V8Runtime runtime, V8Value output, Object object) {
        return Arrays.stream(DEFAULT_PROXY_PLUGINS).anyMatch(it -> it.isProxyable(object.getClass()));
    }

    public static V8Value applyProxyHandler(CustomJavetProxyConverter converter, V8Runtime runtime, Object object, IJavetDirectProxyHandler<?> javetDirectProxyHandler) throws JavetException {
        V8Value v8Value;

        try (V8Scope v8Scope = runtime.getV8Scope()) {
            V8ValueObject finalOutput;
            V8Value targetObject = null;
            try {
                javetDirectProxyHandler.setV8Runtime(runtime);
                targetObject = javetDirectProxyHandler.createTargetObject();
                if (targetObject == null) {
                    targetObject = v8Scope.createV8ValueObject();
                }
                V8ValueProxy proxy = v8Scope.createV8ValueProxy(targetObject);

                if (converter.scriptManager.getUseSameBinding()) {
                    long handle = converter.bindObject(proxy, object);
                    ((V8ValueObject) targetObject).setPrivateProperty(PRIVATE_PROPERTY_NATIVE_OBJECT_HANDLE_PROXY, handle);
                } else {
                    converter.bindObject(targetObject, object);
                }

                try (IV8ValueObject iV8ValueObjectHandler = proxy.getHandler()) {
                    IJavetProxyHandler<?, ?> javetProxyHandler =
                        new JavetDirectProxyObjectHandler<>(runtime, javetDirectProxyHandler);

                    List<JavetCallbackContext> javetCallbackContexts = iV8ValueObjectHandler.bind(javetProxyHandler);
                    try (V8ValueLong v8ValueLongHandle = runtime.createV8ValueLong(javetCallbackContexts.get(0).getHandle())) {
                        iV8ValueObjectHandler.setPrivateProperty(PRIVATE_PROPERTY_PROXY_TARGET, v8ValueLongHandle);
                    }
                }
                finalOutput = proxy;

            } finally {
                JavetResourceUtils.safeClose(targetObject);
            }
            v8Value = finalOutput;
            v8Scope.setEscapable();
        }
        return v8Value;
    }

    public static class ProxyPluginHandler implements IJavetDirectProxyHandler<Exception> {

        private CustomJavetProxyConverter converter;
        private V8Runtime runtime;
        private IClassProxyPlugin plugin;

        public ProxyPluginHandler(CustomJavetProxyConverter converter, V8Runtime runtime, IClassProxyPlugin plugin) {
            this.converter = converter;
            this.runtime = runtime;
            this.plugin = plugin;
        }

        public Object getTargetObject(V8Value target) throws JavetException {
            if (converter.scriptManager.getUseSameBinding()) {
                V8ValueObject obj = (V8ValueObject) target;
                Long handle = obj.getPrivatePropertyLong(PRIVATE_PROPERTY_NATIVE_OBJECT_HANDLE_PROXY);
                return converter.getBoundObject(runtime, handle);
            } else {
                return converter.getBoundObject(target);
            }
        }

        public V8Value createTargetObject() {
            return Optional.ofNullable(getProxyPlugin())
                .map(p -> p.getTargetObjectConstructor(getClass()))
                .map(f -> {
                    try {
                        return f.invoke(getV8Runtime(), this);
                    } catch (Throwable ignored) {
                    }
                    return null;
                })
                .orElse(null);
        }


        public V8Value proxyApply(V8Value target, V8Value thisObject, V8ValueArray arguments) throws JavetException, Exception {
            return null;
        }

        public V8ValueBoolean proxyDeleteProperty(V8Value target, V8Value property) throws JavetException, Exception {
            boolean deleted = false;
            IClassProxyPlugin classProxyPlugin = getProxyPlugin();
            if (classProxyPlugin != null) {
                if (classProxyPlugin.isDeleteSupported(getClass())) {
                    deleted = classProxyPlugin.deleteByObject(getTargetObject(target), getV8Runtime().toObject(property));
                }
            }
            if (deleted) {
                return getV8Runtime().createV8ValueBoolean(true);
            }
            return null;
        }

        public V8Value proxyGet(V8Value target, V8Value property, V8Value receiver) throws JavetException, Exception {
            V8Value v8Value = null;
            IClassProxyPlugin classProxyPlugin = getProxyPlugin();
            if (classProxyPlugin != null) {
                if (classProxyPlugin.isIndexSupported(getClass()) && property instanceof V8ValueString) {
                    String propertyString = ((V8ValueString) property).getValue();
                    if (StringUtils.isDigital(propertyString)) {
                        final int index = Integer.parseInt(propertyString);
                        if (index >= 0) {
                            Object result = classProxyPlugin.getByIndex(getTargetObject(target), index);
                            if (result != null) {
                                v8Value = getV8Runtime().toV8Value(result);
                            }
                        }
                    }
                }
                if (v8Value == null) {
                    IClassProxyPluginFunction<Exception> classProxyPluginFunction = null;
                    if (property instanceof V8ValueString) {
                        String propertyName = ((V8ValueString) property).getValue();
                        classProxyPluginFunction = classProxyPlugin.getProxyGetByString(getClass(), propertyName);
                    } else if (property instanceof V8ValueSymbol) {
                        V8ValueSymbol propertySymbol = (V8ValueSymbol) property;
                        String description = propertySymbol.getDescription();
                        classProxyPluginFunction = classProxyPlugin.getProxyGetBySymbol(getClass(), description);
                    }
                    if (classProxyPluginFunction != null) {
                        v8Value = classProxyPluginFunction.invoke(getV8Runtime(), getTargetObject(target));
                    }
                }
            }
            if (v8Value == null && property instanceof V8ValueString) {
                final String propertyString = ((V8ValueString) property).getValue();
                Optional<IJavetUniFunction<String, ? extends V8Value, Exception>> optionalGetter =
                    Optional.ofNullable(proxyGetStringGetterMap()).map(m -> m.get(propertyString));
                if (optionalGetter.isPresent()) {
                    v8Value = optionalGetter.get().apply(propertyString);
                } else if (IJavetProxyHandler.FUNCTION_NAME_TO_JSON.equals(propertyString)) {
                    v8Value = getV8Runtime().createV8ValueFunction(
                        new JavetCallbackContext(
                            IJavetProxyHandler.FUNCTION_NAME_TO_JSON,
                            V8ValueSymbolType.BuiltIn,
                            JavetCallbackType.DirectCallNoThisAndResult,
                            (IJavetDirectCallable.NoThisAndResult<?>) this::toJSON));
                } else if (IJavetProxyHandler.FUNCTION_NAME_TO_V8_VALUE.equals(propertyString)) {
                    v8Value = getV8Runtime().createV8ValueFunction(
                        new JavetCallbackContext(
                            IJavetProxyHandler.FUNCTION_NAME_TO_V8_VALUE,
                            V8ValueSymbolType.BuiltIn,
                            JavetCallbackType.DirectCallNoThisAndResult,
                            (IJavetDirectCallable.NoThisAndResult<?>) this::symbolToPrimitive));
                }
            } else if (v8Value == null && property instanceof V8ValueSymbol) {
                final V8ValueSymbol propertySymbol = (V8ValueSymbol) property;
                final String description = propertySymbol.getDescription();
                Optional<IJavetUniFunction<V8ValueSymbol, ? extends V8Value, Exception>> optionalGetter =
                    Optional.ofNullable(proxyGetSymbolGetterMap()).map(m -> m.get(description));
                if (optionalGetter.isPresent()) {
                    v8Value = optionalGetter.get().apply(propertySymbol);
                } else if (V8ValueBuiltInSymbol.SYMBOL_PROPERTY_TO_PRIMITIVE.equals(description)) {
                    v8Value = getV8Runtime().createV8ValueFunction(
                        new JavetCallbackContext(
                            V8ValueBuiltInSymbol.SYMBOL_PROPERTY_TO_PRIMITIVE,
                            V8ValueSymbolType.BuiltIn,
                            JavetCallbackType.DirectCallNoThisAndResult,
                            (IJavetDirectCallable.NoThisAndResult<?>) this::symbolToPrimitive));
                } else if (V8ValueBuiltInSymbol.SYMBOL_PROPERTY_ITERATOR.equals(description)) {
                    v8Value = getV8Runtime().createV8ValueFunction(
                        new JavetCallbackContext(
                            V8ValueBuiltInSymbol.SYMBOL_PROPERTY_ITERATOR,
                            V8ValueSymbolType.BuiltIn,
                            JavetCallbackType.DirectCallNoThisAndResult,
                            (IJavetDirectCallable.NoThisAndResult<?>) this::symbolIterator));
                }
            }
            return v8Value;
        }

        public V8Value proxyGetOwnPropertyDescriptor(V8Value target, V8Value property) throws JavetException, Exception {
            V8Value v8Value = null;
            IJavetEntityPropertyDescriptor<V8Value> javetEntityPropertyDescriptor = null;
            try {
                if (property instanceof V8ValueString) {
                    final String propertyString = ((V8ValueString) property).getValue();
                    Optional<IJavetUniFunction<String, ? extends V8Value, Exception>> optionalGetter =
                        Optional.ofNullable(proxyGetStringGetterMap()).map(m -> m.get(propertyString));
                    if (optionalGetter.isPresent()) {
                        v8Value = optionalGetter.get().apply(propertyString);
                    }
                    if (v8Value == null) {
                        IClassProxyPlugin classProxyPlugin = getProxyPlugin();
                        if (classProxyPlugin != null) {
                            javetEntityPropertyDescriptor =
                                classProxyPlugin.getProxyOwnPropertyDescriptor(getTargetObject(target), propertyString);
                            javetEntityPropertyDescriptor.setValue(getV8Runtime().createV8ValueUndefined());
                        }
                    } else {
                        javetEntityPropertyDescriptor =
                            new JavetEntityPropertyDescriptor<>(true, true, true, v8Value);
                    }
                } else if (property instanceof V8ValueSymbol) {
                    final V8ValueSymbol propertySymbol = (V8ValueSymbol) property;
                    final String description = propertySymbol.getDescription();
                    Optional<IJavetUniFunction<V8ValueSymbol, ? extends V8Value, Exception>> optionalGetter =
                        Optional.ofNullable(proxyGetSymbolGetterMap()).map(m -> m.get(description));
                    if (optionalGetter.isPresent()) {
                        v8Value = optionalGetter.get().apply(propertySymbol);
                    }
                    if (v8Value != null) {
                        javetEntityPropertyDescriptor =
                            new JavetEntityPropertyDescriptor<>(true, true, true, v8Value);
                    }
                }
                if (javetEntityPropertyDescriptor != null) {
                    return getV8Runtime().toV8Value(javetEntityPropertyDescriptor);
                }
            } finally {
                JavetResourceUtils.safeClose(v8Value);
            }
            return null;
        }

        public V8Value proxyGetPrototypeOf(V8Value target) throws JavetException, Exception {
            return null;
        }

        public Map<String, IJavetUniFunction<String, ? extends V8Value, Exception>> proxyGetStringGetterMap() {
            return null;
        }

        public Map<String, IJavetBiFunction<String, V8Value, Boolean, Exception>> proxyGetStringSetterMap() {
            return null;
        }

        public Map<String, IJavetUniFunction<V8ValueSymbol, ? extends V8Value, Exception>> proxyGetSymbolGetterMap() {
            return null;
        }

        public Map<String, IJavetBiFunction<V8ValueSymbol, V8Value, Boolean, Exception>> proxyGetSymbolSetterMap() {
            return null;
        }

        public V8ValueBoolean proxyHas(V8Value target, V8Value property) throws JavetException, Exception {
            boolean hasProperty = false;
            IClassProxyPlugin classProxyPlugin = getProxyPlugin();
            if (classProxyPlugin != null && classProxyPlugin.isHasSupported(getClass())) {
                hasProperty = classProxyPlugin.hasByObject(getTargetObject(target), getV8Runtime().toObject(property));
            }
            if (!hasProperty && property instanceof V8ValueString) {
                String propertyString = ((V8ValueString) property).toPrimitive();
                Map<String, IJavetUniFunction<String, ? extends V8Value, Exception>> stringGetterMap = proxyGetStringGetterMap();
                if (stringGetterMap != null && !stringGetterMap.isEmpty()) {
                    hasProperty = stringGetterMap.containsKey(propertyString);
                }
            } else if (!hasProperty && property instanceof V8ValueSymbol) {
                V8ValueSymbol propertySymbol = (V8ValueSymbol) property;
                String description = propertySymbol.getDescription();
                Map<String, IJavetUniFunction<V8ValueSymbol, ? extends V8Value, Exception>> symbolGetterMap = proxyGetSymbolGetterMap();
                if (symbolGetterMap != null && !symbolGetterMap.isEmpty()) {
                    hasProperty = symbolGetterMap.containsKey(description);
                }
            }
            if (hasProperty) {
                return getV8Runtime().createV8ValueBoolean(true);
            }
            return null;
        }

        /**
         * Proxy handler.ownKeys().
         * The handler.ownKeys() method is a trap for the [[OwnPropertyKeys]] object internal method,
         * which is used by operations such as Object.keys(), Reflect.ownKeys(), etc.
         *
         * @param target the target
         * @return the V8 value array
         * @throws JavetException the javet exception
         * @throws E              the custom exception
         * @since 2.2.0
         */
        public V8ValueArray proxyOwnKeys(V8Value target) throws JavetException, Exception {
            Object[] keys = null;
            try {
                Map<String, IJavetUniFunction<String, ? extends V8Value, Exception>> stringGetterMap = proxyGetStringGetterMap();
                if (stringGetterMap != null && !stringGetterMap.isEmpty()) {
                    keys = new Object[stringGetterMap.size()];
                    int index = 0;
                    for (String key : stringGetterMap.keySet()) {
                        keys[index++] = getV8Runtime().createV8ValueString(key);
                    }
                }
                if (keys == null) {
                    IClassProxyPlugin classProxyPlugin = getProxyPlugin();
                    if (classProxyPlugin.isOwnKeysSupported(getClass())) {
                        keys = classProxyPlugin.getProxyOwnKeys(getTargetObject(target));
                        for (int i = 0; i < keys.length; i++) {
                            Object key = keys[i];
                            if (key instanceof String) {
                                keys[i] = getV8Runtime().createV8ValueString((String) key);
                            } else if (key instanceof IJavetEntitySymbol) {
                                keys[i] = getV8Runtime().createV8ValueSymbol(((IJavetEntitySymbol) key).getDescription());
                            } else {
                                keys[i] = getV8Runtime().createV8ValueString(String.valueOf(key));
                            }
                        }
                    }
                }
                if (keys != null) {
                    return V8ValueUtils.createV8ValueArray(getV8Runtime(), keys);
                }
            } finally {
                JavetResourceUtils.safeClose(keys);
            }
            return null;
        }

        /**
         * Proxy handler.set().
         * The handler.set() method is a trap for the [[Set]] object internal method,
         * which is used by operations such as using property accessors to set a property's value.
         *
         * @param target        the target
         * @param propertyKey   the property key
         * @param propertyValue the property value
         * @param receiver      the receiver
         * @return the V8 value boolean
         * @throws JavetException the javet exception
         * @throws E              the custom exception
         * @since 2.2.0
         */
        public V8ValueBoolean proxySet(
            V8Value target, V8Value propertyKey, V8Value propertyValue, V8Value receiver)
            throws JavetException, Exception {
            boolean isSet = false;
            IClassProxyPlugin classProxyPlugin = getProxyPlugin();
            if (classProxyPlugin != null
                && classProxyPlugin.isIndexSupported(getClass())
                && propertyKey instanceof V8ValueString) {
                String propertyKeyString = ((V8ValueString) propertyKey).getValue();
                if (StringUtils.isDigital(propertyKeyString)) {
                    final int index = Integer.parseInt(propertyKeyString);
                    if (index >= 0) {
                        isSet = classProxyPlugin.setByIndex(getTargetObject(target), index, getV8Runtime().toObject(propertyValue));
                    }
                }
            }
            if (!isSet && propertyKey instanceof V8ValueString) {
                String propertyKeyString = ((V8ValueString) propertyKey).toPrimitive();
                Map<String, IJavetBiFunction<String, V8Value, Boolean, Exception>> stringSetterMap = proxyGetStringSetterMap();
                if (stringSetterMap != null && !stringSetterMap.isEmpty()) {
                    IJavetBiFunction<String, V8Value, Boolean, Exception> setter = stringSetterMap.get(propertyKeyString);
                    if (setter != null) {
                        isSet = setter.apply(propertyKeyString, propertyValue);
                    }
                }
            } else if (!isSet && propertyKey instanceof V8ValueSymbol) {
                V8ValueSymbol propertyKeySymbol = (V8ValueSymbol) propertyKey;
                String description = propertyKeySymbol.getDescription();
                Map<String, IJavetBiFunction<V8ValueSymbol, V8Value, Boolean, Exception>> symbolSetterMap = proxyGetSymbolSetterMap();
                if (symbolSetterMap != null && !symbolSetterMap.isEmpty()) {
                    IJavetBiFunction<V8ValueSymbol, V8Value, Boolean, Exception> setter = symbolSetterMap.get(description);
                    if (setter != null) {
                        isSet = setter.apply(propertyKeySymbol, propertyValue);
                    }
                }
            }
            if (isSet) {
                return getV8Runtime().createV8ValueBoolean(true);
            }
            return null;
        }

        /**
         * Register string getter.
         *
         * @param propertyName the property name
         * @param getter       the getter
         * @since 2.2.1
         */
        public void registerStringGetter(
            String propertyName,
            IJavetUniFunction<String, ? extends V8Value, Exception> getter) {
            proxyGetStringGetterMap().put(propertyName, getter);
        }

        /**
         * Register string getter function.
         *
         * @param propertyName the property name
         * @param getter       the getter
         * @since 2.2.1
         */
        public void registerStringGetterFunction(
            String propertyName,
            IJavetDirectCallable.NoThisAndResult<?> getter) {
            proxyGetStringGetterMap().put(
                propertyName,
                innerPropertyName -> getV8Runtime().createV8ValueFunction(
                    new JavetCallbackContext(
                        innerPropertyName,
                        JavetCallbackType.DirectCallNoThisAndResult,
                        getter)));
        }

        /**
         * Register string setter.
         *
         * @param propertyName the property name
         * @param setter       the setter
         * @since 2.2.1
         */
        public void registerStringSetter(
            String propertyName,
            IJavetBiFunction<String, V8Value, Boolean, Exception> setter) {
            proxyGetStringSetterMap().put(propertyName, setter);
        }

        /**
         * Register symbol getter function.
         *
         * @param propertyName the property name
         * @param getter       the getter
         * @since 2.2.1
         */
        public void registerSymbolGetterFunction(
            String propertyName,
            IJavetDirectCallable.NoThisAndResult<?> getter) {
            proxyGetSymbolGetterMap().put(
                propertyName,
                propertySymbol -> getV8Runtime().createV8ValueFunction(
                    new JavetCallbackContext(
                        propertySymbol.getDescription(),
                        JavetCallbackType.DirectCallNoThisAndResult,
                        getter)));
        }

        /**
         * Symbol iterator.
         *
         * @param v8Values the V8 values
         * @return the V8 value
         * @throws JavetException the javet exception
         * @throws E              the custom exception
         * @since 2.2.0
         */
        public V8Value symbolIterator(V8Value... v8Values) throws JavetException, Exception {
            return getV8Runtime().createV8ValueUndefined();
        }

        /**
         * Symbol toPrimitive.
         *
         * @param v8Values the V8 values
         * @return the V8 value
         * @throws JavetException the javet exception
         * @throws E              the custom exception
         * @since 2.2.0
         */
        public V8Value symbolToPrimitive(V8Value... v8Values) throws JavetException, Exception {
            return getV8Runtime().createV8ValueNull();
        }

        public V8Value toJSON(V8Value... v8Values) throws JavetException, Exception {
            return getV8Runtime().createV8ValueObject();
        }
        @Override
        public IClassProxyPlugin getProxyPlugin() {
            return plugin;
        }

        @Override
        public V8Runtime getV8Runtime() {
            return runtime;
        }

        @Override
        public void setV8Runtime(V8Runtime v8Runtime) {
            runtime = v8Runtime;
        }
    }
}
