# FDat.plugins.transit

[![Clojars
Project](https://img.shields.io/clojars/v/dvlopt/fdat.plugins.transit.svg)](https://clojars.org/dvlopt/fdat.plugins.transit)

Compatible with Clojurescript.

[FDat](https://github.com/dvlopt/fdat.cljc) plugin for Transit. Specific
examples resides in the core README.

## Usage

Packaged with the latest version of the core [FDat
library](https://github.com/dvlopt/fdat.cljc) but Transit must be provided by the user.

[For Transit Clojure, see here](https://github.com/cognitect/transit-clj).

[For Transit Clojurescript, see here](https://github.com/cognitect/transit-cljs).

Then :

```clojure
(require '[cognitect.transit           :as transit]
         '[dvlopt.fdat                 :as fdat]
         '[dvlopt.fdat.plugins.transit :as fdat.plugins.transit])


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
```

## License

Copyright © 2020 Adam Helinski

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
