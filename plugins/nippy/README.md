# FDat.plugins.nippy

[![Clojars
Project](https://img.shields.io/clojars/v/dvlopt/fdat.cljc.svg)](https://clojars.org/dvlopt/fdat.cljc)

[![Clojars
Project](https://img.shields.io/clojars/v/dvlopt/fdat.plugins.nippy.svg)](https://clojars.org/dvlopt/fdat.plugins.nippy)


Compatible with Clojurescript.

FDat plugin for [Nippy](https://github.com/ptaoussanis/nippy).

## Usage

The core FDat library and Nippy must be added to dependencies, they are not
pulled by default so the user can easily choose any modern version.

Then, simply:

```clojure
(require '[dvlopt.fdat               :as fdat]
         '[dvlopt.fdat.plugins.nippy :as fdat.plugins.nippy]
         '[taoensso.nippy            :as nippy])


;; Will use global registry

(fdat.plugins.nippy/init)

;; Will use the provided one

(fdat.plugins.nippy/init my-registry)

;; Adding this key to the global registry

(fdat/register {'clojure.core/range range})

;; Then
(def rng
     (-> (fdat/? (range))
         nippy/freeze
         nippy/thaw))
```

## License

Copyright © 2020 Adam Helinski

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
