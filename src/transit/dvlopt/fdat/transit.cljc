(ns dvlopt.fdat.transit

  ""

  {:author "Adam Helinski"}

  (:require [cognitect.transit :as transit]
            [dvlopt.fdat       :as fdat])
  (:import #?(:clj clojure.lang.Fn)))




;;;;;;;;;;


(defn handler-in

  ""

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

  ""

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
