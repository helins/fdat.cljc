# FDat.plugins.nippy

[![Clojars
Project](https://img.shields.io/clojars/v/dvlopt/fdat.plugins.nippy.svg)](https://clojars.org/dvlopt/fdat.plugins.nippy)

[FDat](https://github.com/dvlopt/fdat.cljc) plugin for
[Nippy](https://github.com/ptaoussanis/nippy). More examples are provided in the
core README.

## Usage

Packaged with the latest version of the core [FDat
library](https://github.com/dvlopt/fdat.cljc) but Nippy must be provided by the
user.

Then, simply:

```clojure
(require '[dvlopt.fdat               :as fdat]
         '[dvlopt.fdat.plugins.nippy :as fdat.plugins.nippy]
         '[taoensso.nippy            :as nippy])


;; Will use global registry

(fdat.plugins.nippy/init)

;; We could have provided one

(fdat.plugins.nippy/init my-registry)
```

## License

Copyright © 2020 Adam Helinski

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
