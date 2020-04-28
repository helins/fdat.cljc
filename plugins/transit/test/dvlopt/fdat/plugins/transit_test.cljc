(ns dvlopt.fdat.plugins.transit-test

  "Testing ser/de using Transit."

  {:author "Adam Helinski"}

  (:require [clojure.test                :as t]
            [cognitect.transit           :as transit]
            [dvlopt.fdat                 :as fdat]
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




(defn recall-n

  "Ser/de `n` times."

  [imeta n]

  (fdat-test/recall-serde imeta
                          n
                          serialize
                          deserialize))




;;;;;;;;;; Assertions


(t/deftest recall

  (let [f-data     (serialize fdat-test/f)
        f-recalled (deserialize f-data)]
    (t/is (= 12
             (fdat-test/f 3)
             (f-recalled 3)
             ((recall-n f-recalled
                        10)
              3))
          "Rebuilding a function"))


   (let [sq-data     (serialize fdat-test/sq)
         sq-recalled (deserialize sq-data)]
    (t/is (= (take 100
                   fdat-test/sq)
             (take 100
                   sq-recalled)
             (take 100
                   (recall-n sq-recalled
                             10)))
          "Rebuilding an infinite sequence")))
