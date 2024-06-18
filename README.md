# Javet Zero Proxy

> [!CAUTION]
> This project is not intended to be used by anyone. Please
> read the code carefully before deciding if you want
> to use it.

[Javet](https://github.com/caoccao/Javet) is a way to embed JavaScript into Java.
However, the interoperability speed between JavaScript and Java, particularly when 
calling Java from JavaScript, is insufficient for my need.


Instead of creating a fork of Javet, I decided to develop a custom implementation 
of the converter and interceptor to better fit my need.

### How is it faster?

Binding functions to an object is relatively slow, however, in Javet's converter 
implementation, for every Java object, it binds a new set of callbacks to the new object.

The issue can be addressed by using prototypes, and instead of proxy,
we can simply create a regular object - although it may use more memory compared
to proxy, the implementation is simpler, and it fits my need better.

### Fine-tuning Guide

There are several options you can fine-tune to suit your needs

The followings are the test scripts I'm using to test those options.
However, you may need to test other stuff to figure out
what works best for you.

Test Script A

```js
method.getParameters()[0].getDeclaringExecutable().getParameters()[0].getName()
```

#### proxyForBridgeSupport

When you want to have interop write access to Map, Array, Set, or List,
this option has to be turned on. However, if all you want is read
access to Java Map/Array/Set/List, keeping this option off will boost
the performance by a LOT.

For reference, executing Test Script A for 10000 times used to take around
1100ms (high subjective, it's running on my laptop), with this option
turned off - since I'm not doing any write operations to arrays,
it went down to 550ms.


#### useSameBinding

This option caches JavaScript values for their bound object, i.e., when you
have an object `A`, and have tried to convert `A` to a JavaScript value,
the JavaScript will be cached for the object `A` in the future, that is until
every reference of the JavaScript value has been removed, and the value
has been deleted by the garbage collector.

This avoids rebinding objects, but uses more memory. It's useful
for scenarios where most of the objects used by JavaScript is going to be
the same, however, when every object is different, it should be a little
bit slower than this option turned off.


### Known Issues
1. Function mode does not work at all
2. Annotations do nothing currently
3. There COULD be potential memory leaks, however that is not confirmed

### Behavior Differences
1. `javet.package` can now be accessed with `Java`


### Possible Optimizations
#### Done
- Remove every single use of Proxy
- Make it prototype based

#### Untested
- Improve how methods and fields are being accessed (instead of reflection)
- Remove the need of Proxy for bridge

<sub><sup>If you have any idea, feel free to open an issue, and I can test it out</sup></sub>

#### Doesn't work
- Cache `setPrototypeOf`


### TO-DO
1. Remove Kotlin as a dependency
2. Project banner

<p align="right"><sub><sup>Open Source Project</sup></sub></p>
