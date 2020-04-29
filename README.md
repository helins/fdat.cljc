# FDat

[![Clojars
Project](https://img.shields.io/clojars/v/dvlopt/fdat.svg)](https://clojars.org/dvlopt/fdat)

[![cljdoc badge](https://cljdoc.org/badge/dvlopt/fdat)](https://cljdoc.org/d/dvlopt/fdat)

Compatible with Clojurescript.

"FDat" stands for "Functions as Data", although there is another mnemonic device
for remembering the name of the library.

Wouldn't it be nice to be able to serialize functions or infinite sequences?
Persist them to a file or sending them over the network without a flinch?
Sending events that actually look like events, like functions, instead of
translating them manually to some inconvenient data representation?

This is what this library strives to provide, automatic serialization and
deserialization of functions and all `IMeta`s for that matter, which notably
include sequences (functions in disguise). More precisely, instead of
serializing code, we serialize information about how an `IMeta`  has been
created, and deserialization is taking that information and knowing how to
recreate the original `IMeta`.

This library does not do the serialization itself. Rather, plugins are provided
for commonly used ones. As described below, currently,
[Nippy](https://github.com/ptaoussanis/nippy) and
[Transit](https://github.com/cognitect/transit-clj).

```clojure
;; For instance, using Nippy.

;; Normally, impossible, the sequence is infinite.

(-> (range)
    nippy/freeze)


;; Possible, but gets serialized as a realized sequence which can be really
;; large, it would be way cheaper to recompute it.
;;
;; Furthermore, it would be nice if we could transmit the idea of a random
;; range rather than the exact one we have computed in our process.

(-> (range (random-int 1000000000))
    nippy/freeze)


;; Does not work.

(-> my-function
    nippy/freeze)


;; What we need.

(defn random-range
  [n]
  (range (rand-int n)))


;; Serializing the idea of a random range.
;; Pretty much instantaneous.

(-> (fdat/? (random-range 1000000000))
    nippy/freeze)


;; In contrast, serializing our random range exactly as it is.
;; Takes a long time.

(-> (random-range 1000000000)
    nippy/freeze)
```

Code is data, this axiom holds true until one has to serialize functions, for
instance for sending an event to another process. Things get even more
complicated when code needs to be shared between processes running in different
environments, such as between Clojure and Clojurescript. We shall see how we can
surmount those obstacles.

# Usage

## Supported serializers

Currently, one can use either Nippy or Transit by requiring one of the plugins.
And everything is taken care of. No matter where those
functions/sequences/IMetas are in the data we serialize, they get handled
automatically, both at serialization and deserialization. We can now serialize
entire programs if we want to. Plugin examples just show how to do the setup.

For the [Nippy plugin, go here](./plugins/nippy).

For the [Transit plugin, go here](./plugins/transit).

But first, the following few sections explain how the magic works. The user is
in charge of two things. First, using the `fdat/?` macro when an IMeta such as a
function needs to be serializable. Second, just once, keeping a registry.

## Automatically annotating IMetas using the `?` macro

Annotating means keeping track of a key and (if needed) arguments. The key
refers to a function kept in a registry that we can use to rebuild our original
IMeta when providing (if needed) the original arguments. Manually, it looks
like this:

```clojure
(require '[dvlopt.fdat :as fdat])

(fdat/snapshot (range)
               'clojure.core/range)

(fdat/snapshot (random-range 1000000000)
               'user/random-range
               [1000000000])

(meta *1
      {::fdat/k    'user/random-range
       ::fdat/args [1000000000]})
```

Doing so, we attach an `::fdat/k` and `::fdat/args` to the metadata of the first
argument.

This is tedious and intrusive. Fortunately, the `fdat/?` macro does it for us
while taking care that arguments are evaled only once:

```clojure
;; Results in the same metadata annotation, but a lot more minimalistic.

(fdat/? (random-range 1000000000))
```

The symbol of the function is extracted as the key. Because a key should be
qualified, it is resolved automatically (eg. `random-range` becomes
`user/random-range`).

In Clojurescript, symbols that resolve to the `'cljs.core` namespace are
qualified as `'clojure.core` in order to garantee consistency when exchanging
data between Clojure and Clojurescript processes. For instance, `range` is
qualified as `clojure.core/range` instead of `cljs.core/range`.

A key can be provided explicitly, although this would be less often needed. When
provided explicitly, it can be a keyword, but qualified nonetheless.

```clojure
(fdat/? :some.namespace/keyword-key
        (random-range 1000000000))
```

Further, when arguments are provided, the end result is like manual annotation
using `dvlopt.fdat/snapshot` but it leverages compile time elision (see below).

An additional property is those functions become transparent for developping and
debugging, one can simply have a look at the metadata.

## Recalling how to build an IMeta from its former metadata

That was all for annotations. The second and last part is being able to recall
how to recreate the original `IMeta`s.

```clojure
;; We add to the global registry what we need. We do it once and for all.
;;
;; `range`         is variadic.
;; `random-range`  takes one argument, mentionning it explicitly is an optimization.

(fdat/register {'clojure.core.range range
                'user/random-range  [1 random-range]})
```

Given this registry, a serializer now has a way to rebuilt an `IMeta` with a key
and arguments.


## As a better alternative to event vectors

Without such a scheme, one has to use event vectors or a similar construct
in order to abstract computation as data:

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
the rest of the time.

Lastly, we want to be able to turn off automatic annotation (the `fdat/?`
macro), either entirely or for given namespaces. That means that we can add
those small annotation in our programs just in case we need serialization later
without paying any cost. This is especially important for libraries so they can
provide serialization without incuring any overhead if it is not needed by the
user.

## Custom registry and handling unknown keys

A registry is a function which maps a key to a function that will be used for
rebuilding an IMeta. The global registry is simply a map kept in an atom.

Keeping a custom registry can be useful for at least 2 reasons. First, testing
or stubbing. Second, during deserialization, if a registry does not provide a
function for a key, an exception is thrown. It is trivial to modify that
behavior. For instance, returning nil instead of throwing:

```clojure
(defn my-registry

   [k]

   (or (fdat/registry k)
       (fn handle [_args]
         nil)))
```

## Compile time elision

We can control annotations by `fdat/?` at compile time (and at runtime for
Clojure but not Clojurescript). Pay only for what you use. A library can be
prepared for serialization, and if the user does not need it, annotation can be
disabled so that there is no overhead what so ever.

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

# Adapting for a new serializer

In the unlikely event that Nippy or Transit are not enough, one can adapt this
scheme to another serializer (and contribute it here). One would study how these
plugins are written in the first place.

The following steps might look slightly counterintuitive. Indeed, it was tricky to
develop a way that would potentially work for any serializer, either from
Clojure or Clojurescript.

In short, serializers typically deals in concrete types. One must find a way to
modify how `clojure.lang.IMetas` are handled. Specifically, they should be turned
into a `dvlopt.fdat.Memento` by using the `dvlopt.fdat/memento` function which
either returns a Memento if it finds at least a `::fdat/k` in the metadata, or
nothing, meaning that the `IMeta` should be processed as usual.

Then, one must extend how a `Memento` itself is serialized. It should simply
extract `:snapshot` from it, which is the original metadata map of the `IMeta`,
which contains at least our beloved `::fdat/k` and `::fdat/args`, and pass it on
to be serialized as a regular map.

For unpacking, the only step is to extend how a `Memento` is deserialized. The
`dvlopt.fdat/recall` function must be called providing the retrieved metadata as
argument.

## Run tests

Run all tests (JVM and JS based ones):

```bash
$ ./bin/kaocha
```

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
