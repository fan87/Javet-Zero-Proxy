package me.fan87.javetzeroproxy.converter.prototype;

import com.caoccao.javet.enums.V8ValueErrorType;
import com.caoccao.javet.exceptions.JavetError;
import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.interop.V8Scope;
import com.caoccao.javet.interop.callback.IJavetDirectCallable;
import com.caoccao.javet.interop.callback.JavetCallbackContext;
import com.caoccao.javet.interop.callback.JavetCallbackType;
import com.caoccao.javet.interop.proxy.IJavetReflectionObjectFactory;
import com.caoccao.javet.utils.JavetResourceUtils;
import com.caoccao.javet.utils.JavetVirtualObject;
import com.caoccao.javet.utils.SimpleMap;
import com.caoccao.javet.utils.V8ValueUtils;
import com.caoccao.javet.values.V8Value;
import com.caoccao.javet.values.reference.V8ValueFunction;
import com.caoccao.javet.values.reference.V8ValueGlobalObject;
import com.caoccao.javet.values.reference.V8ValueObject;
import com.caoccao.javet.values.reference.builtin.V8ValueBuiltInObject;
import kotlin.Pair;
import kotlin.collections.MapsKt;
import me.fan87.javetzeroproxy.converter.CustomJavetProxyConverter;
import me.fan87.javetzeroproxy.ScriptManager;
import me.fan87.javetzeroproxy.converter.CustomScoredExecutable;
import me.fan87.javetzeroproxy.converter.prototype.accessor.JSFieldAccessor;
import me.fan87.javetzeroproxy.converter.prototype.accessor.JSMethodAccessor;
import me.fan87.javetzeroproxy.converter.prototype.accessor.impl.ReflectionJSConstructorAccessor;
import me.fan87.javetzeroproxy.converter.prototype.accessor.impl.ReflectionJSFieldAccessor;
import me.fan87.javetzeroproxy.converter.prototype.accessor.impl.ReflectionJSMethodAccessor;

import java.lang.reflect.*;
import java.util.*;


public class JSClassPrototypeMaker<T> {
    // Bad code : /

    public static final String PRIVATE_PROPERTY_NATIVE_OBJECT_HANDLE = "Javet#nativeObjectHandle";
    public static final String PRIVATE_PROPERTY_CLASS_WRAPPER_OBJECT = "Javet#classWrapperObject";
    public static final String PRIVATE_PROPERTY_NATIVE_OBJECT_HANDLE_PROXY = "Javet#nativeObjectTargetProxyHandle";


    public static <E extends AccessibleObject> Pair<E, Object> execute(
        IJavetReflectionObjectFactory reflectionObjectFactory,
        Object targetObject,
        V8ValueObject thisObject,
        List<JSMethodAccessor> invokers,
        List<E> executables,
        JavetVirtualObject[] javetVirtualObjects) throws Throwable {
        List<CustomScoredExecutable<E>> scoredExecutables = new ArrayList<>();
        for (int i = 0; i < executables.size(); i++) {
            E executable = executables.get(i);
            JSMethodAccessor invoker = invokers.get(i);
            CustomScoredExecutable<E> scoredExecutable = new CustomScoredExecutable<>(
                reflectionObjectFactory, targetObject, thisObject, invoker, executable, javetVirtualObjects
            );
            scoredExecutable.calculateScore();
            double score = scoredExecutable.getScore();
            if (score > 0) {
                scoredExecutables.add(scoredExecutable);
            }
        }
        if (!scoredExecutables.isEmpty()) {
            scoredExecutables.sort((o1, o2) -> Double.compare(o2.getScore(), o1.getScore()));
            Throwable lastException = null;
            for (CustomScoredExecutable<E> scoredExecutable : scoredExecutables) {
                try {
                    return new Pair<>(scoredExecutable.executable, scoredExecutable.execute());
                } catch (Throwable t) {
                    lastException = t;
                }
            }
            if (lastException != null) {
                throw lastException;
            }
        }
        return null;
    }


    private final CustomJavetProxyConverter converter;
    private final V8Runtime runtime;
    private final V8ValueObject object;
    private final Class<T> clazz;
    private final boolean isStatic;


    private final List<Constructor<T>> constructors = new ArrayList<>();
    private final List<JSMethodAccessor> constructorsAccessor = new ArrayList<>();

    private final Map<String, JSFieldAccessor> staticFields = new LinkedHashMap<>();
    private final Map<String, JSFieldAccessor> nonStaticFields = new LinkedHashMap<>();

    private final Map<String, List<JSMethodAccessor>> staticMethodsAccessor = new LinkedHashMap<>();
    private final Map<String, List<JSMethodAccessor>> nonStaticMethodsAccessor = new LinkedHashMap<>();

    private final Map<String, List<Method>> staticMethods = new LinkedHashMap<>();
    private final Map<String, List<Method>> nonStaticMethods = new LinkedHashMap<>();


    public JSClassPrototypeMaker(CustomJavetProxyConverter converter, V8Runtime runtime, V8ValueObject object, Class<T> clazz, boolean isStatic) {
        this.converter = converter;
        this.runtime = runtime;
        this.object = Objects.requireNonNull(object);
        if (object.isNullOrUndefined()) {
            throw new IllegalArgumentException("object is null or undefined");
        }
        this.clazz = clazz;
        this.isStatic = isStatic;

        try {
            init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void init() throws NoSuchFieldException, NoSuchMethodException {
        for (Constructor<?> constructor : clazz.getConstructors()) {
            constructors.add((Constructor<T>) constructor);
            constructorsAccessor.add(new ReflectionJSConstructorAccessor(constructor));
        }
        for (Field field : clazz.getDeclaredFields()) {
            if (!Modifier.isPublic(field.getModifiers())) continue;
            List<String> fieldNames = new ArrayList<>();
            fieldNames.add(field.getName());
            fieldNames.addAll(Objects.requireNonNull(converter.scriptManager.getMapper()).getPossibleFieldName(field));

            for (String fieldName : fieldNames) {
                // These are reserved
                if (fieldName.equals("class")) continue;
                if (fieldName.equals("getClass")) continue;
                if (Modifier.isStatic(field.getModifiers())) {
                    staticFields.put(fieldName, new ReflectionJSFieldAccessor(field));
                } else {
                    if (!isStatic) {
                        nonStaticFields.put(fieldName, new ReflectionJSFieldAccessor(field));
                    }
                }
            }
        }
        for (Method method : clazz.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) continue;
            List<String> methodNames = new ArrayList<>();
            methodNames.add(method.getName());
            methodNames.addAll(Objects.requireNonNull(converter.scriptManager.getMapper()).getPossibleMethodName(method));

            for (String methodName : methodNames) {
                // These are reserved
                if (methodName.equals("class")) continue;
                if (Modifier.isStatic(method.getModifiers())) {
                    staticMethods.computeIfAbsent(methodName, it -> new ArrayList<>()).add(method);
                    staticMethodsAccessor.computeIfAbsent(methodName, it -> new ArrayList<>()).add(new ReflectionJSMethodAccessor(method));
                } else {
                    if (!isStatic) {
                        nonStaticMethods.computeIfAbsent(methodName, it -> new ArrayList<>()).add(method);
                        nonStaticMethodsAccessor.computeIfAbsent(methodName, it -> new ArrayList<>()).add(new ReflectionJSMethodAccessor(method));
                    }
                }
            }
        }
    }

    public void apply() throws JavetException {
        V8ValueBuiltInObject builtInObject = runtime == converter.scriptManager.getV8Runtime() ?
            converter.scriptManager.getBuiltInObject() : runtime.getGlobalObject().getBuiltInObject();
        bindBasic();
        bindConstructor();
        bindFields();
        bindMethods();
        if (object.isNullOrUndefined()) {
            throw new IllegalArgumentException("object is null or undefined");
        }
        assert builtInObject != null;
    }

    private void bindBasic() throws JavetException {
        if (isStatic) {
            object.bindProperty(
                new JavetCallbackContext("class", JavetCallbackType.DirectCallGetterAndNoThis,
                    (IJavetDirectCallable.GetterAndNoThis<Exception>) () -> runtime.toV8Value(clazz)
                )
            );
        }
        if (!isStatic) {
//            object.bindFunction(
//                new JavetCallbackContext("getClass", JavetCallbackType.DirectCallThisAndResult,
//                    (IJavetDirectCallable.ThisAndResult<?>) (thisObj, arguments) -> runtime.toV8Value(runtime.toObject(thisObj).getClass()))
//            );
        }
    }

    private void bindConstructor() throws JavetException {
        object.bindFunction(
            new JavetCallbackContext("__javet__constructor", JavetCallbackType.DirectCallThisAndResult,
                (IJavetDirectCallable.ThisAndResult<?>) this::construct)
        );
    }
    private void bindFields() throws JavetException {
        for (Map.Entry<String, JSFieldAccessor> entry : nonStaticFields.entrySet()) {
            object.bindProperty(
                new JavetCallbackContext(entry.getKey(), JavetCallbackType.DirectCallGetterAndThis,
                    (IJavetDirectCallable.GetterAndThis<Exception>) (thisObj) -> {
                        try {
                            Object obj = converter.getBoundObject(thisObj);
                            if (obj == null) {
                                runtime.throwError(V8ValueErrorType.ReferenceError, "The receiver of this function (\"this\") is an incorrect type");
                            }
                            return runtime.toV8Value(entry.getValue().get(obj));
                        } catch (Throwable e) {
                            e.printStackTrace();
                            return runtime.createV8ValueUndefined();
                        }
                    }
                ),
                entry.getValue().isFinal() ? null : new JavetCallbackContext(entry.getKey(), JavetCallbackType.DirectCallSetterAndThis,
                    (IJavetDirectCallable.SetterAndThis<Exception>) (thisObj, value) -> {
                        try {
                            Object obj = converter.getBoundObject(thisObj);
                            entry.getValue().set(obj, runtime.toObject(value));
                            return value;
                        } catch (Throwable e) {
                            e.printStackTrace();
                            return runtime.createV8ValueUndefined();
                        }
                    }
                )
            );
        }
        for (Map.Entry<String, JSFieldAccessor> entry : staticFields.entrySet()) {
            object.bindProperty(
                new JavetCallbackContext(entry.getKey(), JavetCallbackType.DirectCallGetterAndNoThis,
                    (IJavetDirectCallable.GetterAndNoThis<Exception>) () -> {
                        try {
                            return runtime.toV8Value(entry.getValue().get(null));
                        } catch (Throwable e) {
                            e.printStackTrace();
                            return runtime.createV8ValueUndefined();
                        }
                    }
                ),
                entry.getValue().isFinal() ? null : new JavetCallbackContext(entry.getKey(), JavetCallbackType.DirectCallSetterAndNoThis,
                    (IJavetDirectCallable.SetterAndNoThis<Exception>) (value) -> {
                        try {
                            entry.getValue().set(null, runtime.toObject(value));
                            return value;
                        } catch (Throwable e) {
                            e.printStackTrace();
                            return runtime.createV8ValueUndefined();
                        }
                    }
                )
            );
        }
    }

    private void bindMethods() throws JavetException {
        for (Map.Entry<String, List<Method>> entry : staticMethods.entrySet()) {
            if (nonStaticFields.containsKey(entry.getKey())) continue;
            if (staticFields.containsKey(entry.getKey())) continue;
            List<JSMethodAccessor> accessor = staticMethodsAccessor.get(entry.getKey());
            object.bindFunction(new JavetCallbackContext(entry.getKey(), JavetCallbackType.DirectCallNoThisAndResult,
                (IJavetDirectCallable.NoThisAndResult<Exception>) (arguments) -> {
                    try {
                        Pair<Method, Object> execute = execute(
                            runtime.getConverter().getConfig().getReflectionObjectFactory(),
                            null,
                            null,
                            accessor,
                            entry.getValue(),
                            V8ValueUtils.convertToVirtualObjects(arguments)
                        );
                        assert execute != null;
                        if (execute.getFirst().getReturnType() == void.class) return runtime.createV8ValueUndefined();
                        return runtime.toV8Value(execute.getSecond());
                    } catch (Throwable e) {
                        e.printStackTrace();
                        return runtime.createV8ValueUndefined();
                    }
                }
            ));
        }
        for (Map.Entry<String, List<Method>> entry : nonStaticMethods.entrySet()) {
            if (nonStaticFields.containsKey(entry.getKey())) continue;
            if (staticFields.containsKey(entry.getKey())) continue;
            List<JSMethodAccessor> accessor = nonStaticMethodsAccessor.get(entry.getKey());
            object.bindFunction(new JavetCallbackContext(entry.getKey(), JavetCallbackType.DirectCallThisAndResult,
                (IJavetDirectCallable.ThisAndResult<Exception>) (thisObj, arguments) -> {
                    try {
                        V8ValueObject valueObject = (V8ValueObject) thisObj;


                        Object boundObject = converter.getBoundObject(valueObject);

                        if (boundObject == null) {
                            runtime.throwError(V8ValueErrorType.ReferenceError, "The receiver of this function (\"this\") is an incorrect type");
                            return null;
                        }
                        Pair<Method, Object> execute = execute(
                            runtime.getConverter().getConfig().getReflectionObjectFactory(),
                            boundObject,
                            valueObject,
                            accessor,
                            entry.getValue(),
                            V8ValueUtils.convertToVirtualObjects(arguments)
                        );
                        if (execute == null) {
                            throw new JavetException(JavetError.ExecutionFailure, MapsKt.mapOf(new Pair<>(JavetError.PARAMETER_MESSAGE, "Could not find the target method with the requested parameters")));
                        }
                        assert execute != null;
                        if (execute.getFirst().getReturnType() == void.class) return runtime.createV8ValueUndefined();
                        return runtime.toV8Value(execute.getSecond());
                    } catch (Throwable e) {
                        e.printStackTrace();
                        return runtime.createV8ValueUndefined();
                    }
                }
            ));

        }
    }



    // Bound Functions
    public V8Value construct(V8Value target, V8Value... arguments) throws JavetException {
        if (!constructors.isEmpty()) {
            try {
                Object execute = execute(
                    runtime.getConverter().getConfig().getReflectionObjectFactory(),
                    null,
                    (V8ValueObject) target,
                    constructorsAccessor,
                    constructors,
                    V8ValueUtils.convertToVirtualObjects(arguments)
                ).component2();
                converter.bindObject(target, execute);
                return target;
            } catch (JavetException e) {
                throw e;
            } catch (Throwable t) {
                throw new JavetException(JavetError.CallbackMethodFailure,
                    SimpleMap.of(
                        JavetError.PARAMETER_METHOD_NAME, "constructor",
                        JavetError.PARAMETER_MESSAGE, t.getMessage()), t);
            } finally {
                if (arguments != null) {
                    JavetResourceUtils.safeClose(arguments);
                }
            }
        }
        return runtime.createV8ValueUndefined();
    }
}
