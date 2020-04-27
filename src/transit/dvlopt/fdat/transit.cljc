(ns dvlopt.fdat.transit

  "Ser/de for Transit.
  
   See README for examples."

  {:author "Adam Helinski"}

  (:require [cognitect.transit :as transit]
            [dvlopt.fdat       :as fdat])
  (:import #?(:clj clojure.lang.Fn)))




;;;;;;;;;; Transit handlers


(defn handler-in

  "Must be merged with other `:handlers` (or used as is) for reads."

  ([]

   (handler-in (fdat/registry)))


  ([registry]

   {"fdat" (transit/read-handler (fn deserialize [x]
                                   (fdat/recall registry
                                                x)))}))




(defn- -tag

  ;; For CLJS compatibility.

  [_]

  "fdat")




(defn writer-options

  "Provides `:handlers` for writes as well as a necessary `:transform` function."

  ;; The Fn/MetaFn handlers will never be called since :transform effectively transforms annotated
  ;; functions into Memento, but without those handlers, :transform would not be called in the first
  ;; place.

  ([]

   {:handlers  {#?(:clj  Fn
                   :cljs MetaFn)    (transit/write-handler -tag
                                                           nil)
                dvlopt.fdat.Memento (transit/write-handler -tag
                                                           (fn serialize [x]
                                                             (:snapshot x)))}
    :transform (fn transform [x]
                 (or (fdat/memento x)
                     x))}))
