package me.fan87.javetzeroproxy.converter.prototype;


import com.caoccao.javet.enums.V8ProxyMode;
import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.interop.V8Scope;
import com.caoccao.javet.values.V8Value;
import com.caoccao.javet.values.reference.V8ValueGlobalObject;
import com.caoccao.javet.values.reference.V8ValueObject;
import me.fan87.javetzeroproxy.converter.CustomJavetProxyConverter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class CustomJavetProxyPrototypeStore {
    public static final String DUMMY_FUNCTION_STRING =
        "(() => {\n" +
            "  const DummyFunction = function () { console.log('Hello') };\n" +
            "  return DummyFunction;\n" +
            "})();";


    public static V8ValueObject createOrGetPrototype(CustomJavetProxyConverter converter, V8Runtime v8Runtime, V8ProxyMode v8ProxyMode, Class<?> clazz)
        throws JavetException {
        if (v8ProxyMode == V8ProxyMode.Object) return converter.getPrototypeOf(clazz, false);
        if (v8ProxyMode == V8ProxyMode.Class) return converter.getPrototypeOf(clazz, true);
        try (V8Scope v8Scope = v8Runtime.getV8Scope()) {
            V8ValueObject v8ValueObject = v8Scope.createV8ValueFunction(DUMMY_FUNCTION_STRING);
            try (V8ValueObject v8ValueObjectPrototype = createOrGetPrototype(converter, v8Runtime, V8ProxyMode.Object, clazz)) {
                v8ValueObject.setPrototype(v8ValueObjectPrototype);
            }
            v8Scope.setEscapable();
            return v8ValueObject;
        }
    }

}
