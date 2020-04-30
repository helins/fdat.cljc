(ns dvlopt.fdat.plugins.transit-test

  "Testing ser/de using Transit."

  {:author "Adam Helinski"}

  (:require [clojure.test                :as t]
            [cognitect.transit           :as transit]
            [dvlopt.fdat.plugins.transit :as fdat.plugins.transit]
            [dvlopt.fdat-test            :as fdat-test])
  #?(:clj (:import (java.io ByteArrayInputStream
                            ByteArrayOutputStream))))




;;;;;;;;;; Ser/de


(defn serialize

  "Serializes using Transit."

  [x]

  (let [options (fdat.plugins.transit/writer-options)]
    #?(:clj  (let [out (ByteArrayOutputStream. 512)]
               (transit/write (transit/writer out
                                              :json
                                              options)
                              x)
               out)
       :cljs (transit/write (transit/writer :json
                                            options)
                            x))))




(defn deserialize

  "Deserializes using Transit."

  [x]

  (transit/read (transit/reader #?(:clj (ByteArrayInputStream. (.toByteArray x)))
                                :json
                                {:handlers (fdat.plugins.transit/handler-in)})
                #?(:cljs x)))





;;;;;;;;;; Assertions


(t/deftest capturing

  (fdat-test/serde-suite serialize
                         deserialize))
