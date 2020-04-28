# FDat.plugins.transit

[![Clojars
Project](https://img.shields.io/clojars/v/dvlopt/fdat.svg)](https://clojars.org/dvlopt/fdat)

[![Clojars
Project](https://img.shields.io/clojars/v/dvlopt/fdat.plugins.transit.svg)](https://clojars.org/dvlopt/fdat.plugins.transit.cljc)

Compatible with Clojurescript.

FDat plugin for Transit.

## Usage

The core FDat library and Transit must be added to dependencies, they are not
pulled by default so the user can easily choose any modern version.

[For Transit Clojure, see here](https://github.com/cognitect/transit-clj).

[For Transit Clojurescript, see here](https://github.com/cognitect/transit-cljs).

Then, simply:

```clojure
(require '[cognitect.transit           :as transit]
         '[dvlopt.fdat                 :as fdat]
         '[dvlopt.fdat.plugins.transit :as fdat.plugins.transit])

;; We already prepare the registry, so we do not forget

(fdat/register {'clojure.core/range range})

;; Returns an argument map for Transit writers, contains :handlers and
;; :transform.

(fdat.plugins.transit/writer-options)

;; Returns a map of handlers for Transit readers.
;; Will use the given registry or the global one.

(fdat.plugins.transit/handler-in)


;; Serialization in Clojure, for example:

(import '(java.io ByteArrayInputStream
                  ByteArrayOutputStream))

(defn serialize
  [x]
  (let [out (ByteArrayOutputStream. 512)]
    (transit/write (transit/writer out
                                   :json
                                   (fdat.plugins.transit/writer-options))
                   x)
    out))


(defn deserialize
  [x]
  (transit/read (transit/reader (ByteArrayInputStream. (.toByteArray x))
                                :json
                                {:handlers (fdat.plugins.transit/handler-in)})))


;; Same ser/de but in Clojurescript, slightly different:

(defn serialize
  [x]
  (transit/write (transit/writer :json
                                 (fdat.plugins.transit/writer-options))
                x))


(defn deserialize
  [x]
  (transit/read (transit/reader :json
                                {:handlers (fdat.plugins.transit/handler-in)})
                x))


;; Then

(def rng
     (-> (fdat/? (range))
         serialize
         deserialize))
```

## License

Copyright © 2020 Adam Helinski

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
