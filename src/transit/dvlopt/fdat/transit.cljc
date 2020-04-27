(ns dvlopt.fdat.transit

  ""

  {:author "Adam Helinski"}

  (:require [cognitect.transit :as transit]
            [dvlopt.fdat       :as fdat])
  (:import #?(:clj (clojure.lang Fn
                                 IMeta))
           #_dvlopt.fdat.Memento))


;;;;;;;;;;


(defn handler-in

  ""

  ([]

   (handler-in (fdat/registry)))


  ([registry]

   {"fdat" (transit/read-handler (fn deserialize [mta]
                                   (fdat/build (fdat/memento mta)
                                               registry)))}))




(defn- -tag

  ;;

  [_]

  "fdat")




(def ^:private -write-handler

  ;;

  (transit/write-handler -tag
                         nil))




(defn writer-options

  ""

  ([]

   {:handlers  {#?(:clj  Fn
                   :cljs MetaFn)    -write-handler
                dvlopt.fdat.Memento (transit/write-handler -tag
                                                           fdat/afy)}
    :transform (fn transform [x]
                 (if (meta x)
                   (fdat/afy x)
                   x))}))
