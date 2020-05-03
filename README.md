# FDat, meaning "Functions as Data" (and nothing else)

[![Clojars
Project](https://img.shields.io/clojars/v/dvlopt/fdat.svg)](https://clojars.org/dvlopt/fdat)

[![cljdoc badge](https://cljdoc.org/badge/dvlopt/fdat)](https://cljdoc.org/d/dvlopt/fdat)

Compatible with Clojurescript.


- [Supported serializers](#supported-serializers)
- [Common usage](#common-usage)
    - [The omniscient `?` macro](#omniscient-?)
    - [Maintaining a registry](#maintaining-registry)
    - [Mastering `?` and its (not so) many faces](#mastering-?)
    - [`!`, a useful but dangerous counterpart](#!-counterpart)
    - [Registries](#registries)
    - [Serializing ideas (what if Plato was a Clojurist)](#serializing-ideas)
- [Going further](#going-further)
    - [As a better alternative to event vectors & friends](#event-vectors)
    - [Compile time elision](#compile-time-elision)
- [Library authoring](#library-authoring)
    - [Writing serializable programs and libraries](#serializable-programs)
    - [Adapting for a new serializer](#new-serializer)
- [Run tests](#run-tests)


Code is data, this axiom holds true until one has to serialize some functions.
To further complicate the issue, sometimes data is a function in disguise.
Notoriously, sequences are indeed "logical lists". How could one save an
infinite sequence to a file, or send a closure over the wire?

If a function is a black box one cannot open and readily share, them one can at
least describe "what" this black box do instead of unsuccessfully focusing on
"how" it does it.

For instance, using the plugin for the excellent
[Nippy](https://github.com/ptaoussanis/nippy) serializer:

```clojure
(require '[dvlopt.fdat               :as fdat :refer [?]]
         '[dvlopt.fdat.plugins.nippy :as fdat.plugins.nippy])
         '[taoensso.nippy            :as nippy])


(fdat.plugins.nippy/init)


(?
 (defn hello
   [x]
   (str "Hello " x)))


(fdat/register [hello])


(def ba
     (nippy/freeze hello))


(def hello-2
     (nippy/thaw ba))


(println (hello "world"))
```

This small excerpt introduces two important notions: discreetly keeping track of
"what" a function is by using the `?` macro, this information being data that we
can easily serialize, and maintaining a `registry` that we use in order to know
what to do with that knowledge at deserialization.

This must sound quite abstract, so let us explore all that.

Feel free to clone this repo and start a REPL, all examples are in
[./dev/user.clj](./dev/user.clj) :

```bash
$ clj -A:test:dev:nrepl
```

# Supported serializers <a name="supported-serializers">

For the [Nippy plugin, go here](./plugins/nippy).

For the [Transit plugin, go here](./plugins/transit).

However, main concepts are described here only.

## Common usage <a name="common-usage">

### The omniscient `?` macro <a name="omniscient-?">

Functions are `IMetas`, meaning they can hold metadata. Actually, everything we
will discuss applies to all `IMetas`. Sequences are one other useful example.
Later, we will even see how all of this is useful for any data structure.

The `?` macro is capable of analyzing a form producing an `IMeta` and annotating
it in its metadata.

The first information it extracts is the `key`:

```clojure
(?
 (defn foo []))


(= (meta foo)
   {::fdat/key 'user/foo})
```

In order to benefit from our scheme, an `IMeta` must have a `key` which will
hold a special meaning when we will discuss `registries`. The macro derives the
key from the name in `defn`. A key must be always qualified. The automatic
extraction is particularly convenient. In rare cases, we may find it useful to
explicitly provide a key:

```clojure
(? ^{::fdat/key :some.namespace/my-key}
   (def bar
        (fn [])))


(= (meta bar)
   {::fdat/key :some.namespace/my-key})
```

We provided our own key by adding metadata to the form using the [metadata
reader](https://clojure.org/reference/reader?source=post_page---------------------------#_metadata).
Furthermore, besides a symbol, we notice it can also be a keyword, qualified
nonetheless.

Besides definitions, the `?` macro also understands function calls, trusting
they produce an `IMeta`:

```clojure
(= (meta
     (? (range 1000000)))
   {::fdat/key  'clojure.core/range
    ::fdat/args [1000000]})
```

Interesting, the macro keeps track of `arguments` as well. Heck, it even
understands partial application and treats it specially:

```clojure
(= (meta
     (? (partial range
                 10)))
   {::fdat/key  'dvlopt.fdat/partial-1
    ::fdat/args [range 10]})
```

You might begin to understand where this is going. If I gave you a specific key
and, if needed, some arguments, maybe you could make your own `IMeta` instead of
the one I cannot send you.

### Maintaining a registry <a name="maintaining-registry">

A `registry` is simply a map of `key` -> `(fn [args] imeta)`. In other words, a
key unlocks the secret to producing an `IMeta` given arguments. We did not
mention one up until now because we were using the global registry. We shall see
later it is often useful to maintain your own.

Remember that first `hello` function? After capturing it using `?`, we added it
to the global registry:

```clojure
(fdat/register [hello])
```

The `dvlopt.fdat/register` function looks for a `key` in the metadata and also
for an indication on how to apply arguments. Being a simple function, `hello`
does not need any arguments to exist, it has been defined once and simply
exists by itself. However, a fair share of our programs is about producing
`IMetas`, sequences, functions returning functions.

```clojure
(? ^{::fdat/apply 1}
   (defn curried-add
     ([x]
      (fn curry [y]
        (curried-add x
                     y)))
     ([x y]
      (+ x
         y))))


(fdat/register [curried-add])
```

By adding `::fdat/apply 1` to the metadata, we are saying that the `IMeta` we
are interested in is the result of applying `1` argument to `curried-add` rather
than `curried-add` itself. We did not simply add `curried-add` to the registry,
we added a function that takes a vector of one argument and applies it to
`curried-add`.

`::fdat/apply` can optimize for arities 0 to 8. Specifying `:variadic` can apply
any arity (but is slower) and `:none` meaning no application is the default.

Remember that `?` can capture function calls and keep track of arguments.

```clojure
(def plus-3
     (? (curried-add 3)))


(= (meta plus-3)
   {::fdat/key  'user/curried-add
    ::fdat/args [3]})
```

Note that what needs to be in the registry in not `plus-3` but the key it refers
to, which is `user/curried-add` and which we already added indeed.

For instance, and this is generally what any plugin does, the Nippy plugin
extends Nippy so that `IMetas` with a `key` receive special treatment: they are
discarded, only their metadata, being a simple map, is serialized. At
deserialization, this metadata map containing a `key` and (if needed)
`arguments` is automatically used against a `registry` (defaulting to the global
one) in order to produce (or rather "reproduce") the intended `IMeta`.

```clojure
(= 7
   ((-> plus-3
        nippy/freeze
        nippy/thaw)   4))
```

If you have understood that, then you have understood the whole idea and the sky
becomes the limit, although the advent of space age made that expression
obsolete.


### Mastering `?` and its (not so) many faces <a name="mastering-?">

We have seen that `?` understands definitions (`def` and `defn`), calls
(`(some-fn 1 2 3)`), as well as `partial` application. Partial application is
valuable because it often provides flexibility. Actually, we can rewrite
`curried-add` so that it is simply `add`.

```clojure
(?
 (defn add [x y] (+ x y)))

(= (meta add)
   {::fdat/key 'user/add})

(fdat/register [add])

(? (partial add
            3))

(= (meta *1)
   {::fdat/key  'dvlopt.fdat/partial-1
    ::fdat/args [add 3]})
```

A `registry` has several special keys such as `'dvlopt.fdat/partial-1` for
optimizing partial application. Interestingly but unsurprisingly, `add` figures
in the arguments, as a function, in this metadata map that was supposed to be
serializable data. But this function itself has metadata about how to serialize
it, indeed we have captured `add` using `?`. Due to the "depth-first" manner
serializers operate, this just works: `add` will be serialized before this outer
metadata map. As long as we use `?` properly, any `IMeta` can be an argument.

Once again, the result of that partial application is not what needs to be in
the registry but rather `add` because it is part of the metadata that needs to
be serializable. It means that once we have `add` in our registry, we can
capture any partial application without caring further.

Do not feel discouraged if your head is spinning a little bit. It shows how a
simple idea can expand.

Capturing free functions and other forms is far less common. Just in case, one
can mention explicitly everything, even extra metadata that should be attached.
The advantage of still using `?` to do so is that we can benefit from compile
time elision as described later.

```clojure
(? ^{::fdat/key some.ns/foo
     ::extra    {:possible? true}}
   (fn [] 42))
```

Lastly, when an explicit key is an unquoted symbol (what we have done so far),
it must resolve to an existing definition. `some.ns/foo` must indeed exist.
When quoted, the namespace is resolved following aliases but the name need not
to resolve to anything existing. For instance, remembering that `fdat` is an
alias for the `dvlopt.fdat` namespace...

```clojure
(? ^{::fdat/key `fdat/invented}
   (fn [] 42))
```

...will have `'dvlopt.fdat/invented` as its key, although it does not refer to
anything real.


### `!`, a useful but dangerous counterpart <a name="!-counterpart">

While `?` is about capturing how `IMetas` are created, one might want to reuse
already existing once. For instance, we do not have to write `add`, we can use
Clojure's `+`.

```clojure
(fdat/! +)


(= (meta +)
   {::fdat/key 'clojure.core/+})


(fdat/register [+])


(? (partial +
            3))
```

Although useful, it can be dangerous. For instance, two libraries might need
`+`. All is fine if the key is extracted automatically, but the following
example should be avoided:

```clojure
(fdat/! ^{::fdat/key ::my-key}
        +)
```

It is best to create an alias and use `?`, the culprit being that we have to use
that alias instead of `+`:

```clojure
(? (def add
        +))

(fdat/register [add])

(= (meta add)
   {::fdat/key 'user/add})  ;; but in reality points to `+`
```

### Registries <a name="registries">

At first, one might think it is cumbersome having to add all those `IMetas` to
some `registry`. Actually, this is a feature.

First of all, a `registry` represent some context. Let us imagine an online
shopping app. The frontend is built in Clojurescript while the backend is built
in Clojure.

Functions are akin to verbs. They represent some action, but without a
sentence providing context, they mean hardly anything. "Eat" is abstract until
we conclude that "we eat an apple".

Similarly, `(add-to-cart item)` does not mean much by itself, but we agree it is
supposed to mean something. On the client side, it would probably be
implemented as sending a request to the server. On the backend, it would
probably be implemented as updating the database. Same name, same argument,
different implementations. Each context provides its own specific meaning to
shared abstract verbs.

Furthermore, within a same context, even a same process, one can get creative by
using several registries. One example would be handling authorization where a
role provides access to a specific registry. Depending on the role of the user
issuing a request on our backend, we would use one registry or another. Some
roles would have access to some verbs, and a verb could be implemented
differently depending on the role.

At deserialization, if a key is not found in a registry, an exception is thrown.
Instead of providing a registry as map, one could provide a function for
handling that. For instance, still relying on the global registry, handling
based on the namespace of the key not-found:

```clojure
(defn my-registry
  [k]
  (or (fdat/registry k)
      (fn [_args]
        (case (namespace k)
          "some.ns"    ...
          "another.ns" ...
          ...))))
```


### Serializing ideas (what if Plato was a Clojurist) <a name="serializing-ideas">

Plato was keen about making a distinction between the physical world and the
"world of Ideas". A pale copy from an inimitable perfection.

Data structures, our beloved maps and vectors, pure data, often are but a mere
instance of an absolute idea. For instance, let us imagine we have a vector of
random numbers, nested somewhere in the deep maps of our program. Those random
numbers are used for some cryptographic purposes.

If we wanted to serialize that state of our program, it would probably be a
terrible idea to serialize those random numbers and share them as such. How
unsafe.  What we would like to serialize is the idea of a vector of random
numbers, rather than that specific vector.

```clojure
(? ^{::fdat/apply 1}
   (defn entropy
     [n]
     (vec (repeatedly n
                      rand))))


(fdat/register [entropy])


(def random-numbers
     (? (entropy 2)))


(= (meta random-numbers)
   {::fdat/key  'user/entropy
    ::fdat/args [2]})


(= random-numbers
   [0.54614 0.90789])


(= (-> random-numbers
       nippy/freeze
       nippy/thaw)
   [0.764213 0.19736])  ;; <!> Different numbers
```

That's it. As simple as that. Hope that elicited a Socratic awakening.

Besides serializing impossibles things such as infinite sequences, we see that
even our finite and concrete datastructures can benefit from all this.

## Going further <a name="going-further">

### As a better alternative to event vectors & friends <a name="event-vectors">

Without all that, the typical method of sending any "action", "verb", "event" or
whatnot or so, involves using an event vector or similar:

```clojure
[:my-fn-or-event 1 2 :arguments]
```

We argue that this is intrusive and inefficient.

First of all, one has to adopt a particular style and stick to it. It often
involves writing a program twice: the functions and all the translation between
those functions and their data representations. It does not feel too smart.

Second, what is supposed to be a mere function call turns into vector
destructuring and manipulation, if only but to access arguments.

Third, all those vector manipulations really slow down our programs. We want to
pay the cost of serialization only at serialization, while things run as usual
the rest of the time. We do pay a minor fee for capturing (attaching metadata to
those `IMetas`), but those `IMetas` work as intended.

Fourth, serialization is not handled. Those event vectors are deserialized as
vectors, we have to do some additional work in order to do anything with them.

Lastly, we want to be able to turn off capturing, either entirely or for given
namespaces. That means that we can add those small annotation in our programs
just in case we need serialization later without paying any cost. This is
especially important for libraries so they can provide serialization without
incuring any overhead if it is not needed by the user.


### Compile time elision <a name="compile-time-elision">

We can control annotations by `?` at compile time (and at runtime for Clojure
but not Clojurescript). Pay only for what we use.

For compilation (either Clojure or Clojurescript), one can provide the following
`-D` properties in `deps.edn` or, if using Shadow-CLJS for Clojurescript, in
`shadow-cljs.edn`:

```clojure
;; Turning off by default, except for the namespaces in the white list.
;; Namespaces are provided in a vector where the usual white spaces must be ",".

{:jvm-opts ["-Dfdat.track.default=false"
            "-Dfdat.track.white-list=[allowed-ns-1,allowed-ns-2]"]}


;; Otherwise, is turned on by default, but we can black list namespaces.

{:jvm-opts ["-Dfdat.track.black-list=[fobidden-ns-1,forbidden-ns-2]"]}

```

Alternatively, one can set environment properties, replacing dots with
underscores:

```bash
env fdat_track_default=false  my_command ...
```

At runtime, in Clojure only, one can dynamically do the same using the
`dvlopt.fdat.track` namespace.

Interestingly, the namespaces refers to the namespaces of the `keys` that
`?` captures, not the namespaces where the capturing happens. For instance,
assuming the `foo.bar` namespace has been turned off, the following capturing
still happens:

```clojure
(in-ns 'foo.bar)

(? ^{::key whatever/foo}
   (defn foo []))


(= (meta foo)
   {::fdat/key 'whatever/foo})
```

## Library authoring <a name="library-authoring">

### Writing serializable programs and libraries <a name="serializable-programs">

Thanks to compile time elision and due to the fact that the `?` macro is so
minimalistic, it does not hurt to write our code as if it needed to be
serializable, knowing that we can turn of capturing at compile time and incur no
overhead.

Instead of `register`ing to the global registry, a library should always propose
a collection of the `IMetas` it captures and let the user be in charge of the
registering.

### Adapting for a new serializer <a name="new-serializer">

In the unlikely event that Nippy or Transit are not enough, one can adapt this
scheme to another serializer (and contribute it here). One would study how these
plugins are written in the first place.

The `dvlopt.fdat.plugins` offers two needed utilities.

In short, serializers typically deals in concrete types. One must find a way to
modify how `IMetas` are handled. Specifically, they should be turned into a
`dvlopt.fdat.Memento` by using the `dvlopt.fdat.plugins/memento` function which
either returns a Memento if it finds at least a `key` in the metadata, or
nothing, meaning that the `IMeta` should be processed as usual.

Then, one must extend how a `Memento` record itself is serialized. It should
simply extract `:snapshot` from it, which is the original metadata map of the
`IMeta`, which contains at least our beloved `key` and (if needed) `arguments`,
then pass it on to be serialized as a regular map.

For unpacking, the only step is to extend how a `Memento` is deserialized. The
`dvlopt.fdat.plugins/develop` function must be called providing the retrieved
metadata as argument.

Those steps might look slightly counterintuitive. Indeed, it was tricky to
develop a way that would potentially work for any serializer, either from
Clojure or Clojurescript.


## Run tests <a name="run-tests">

Run all tests (JVM and JS based ones):

```bash
$ ./bin/kaocha
```

Due to some compilation shenaningans, testing both JS environments in the same
run can fail.

For Clojure only:

```bash
$ ./bin/kaocha jvm
```

For Clojurescript on NodeJS, `ws` must be installed:
```bash
$ npm i ws
```
Then:
```
$ ./bin/kaocha node
```

For Clojurescript in the browser (which might need to be already running):
```bash
$ ./bin/kaocha browser
```

## License

Copyright © 2020 Adam Helinski

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
