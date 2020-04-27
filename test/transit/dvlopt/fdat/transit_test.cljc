(ns dvlopt.fdat.transit-test

  {:author "Adam Helinski"}

  (:require [clojure.test        :as t]
            [cognitect.transit   :as transit]
            [dvlopt.fdat         :as fdat]
            [dvlopt.fdat.transit :as fdat.transit]
            [dvlopt.fdat-test    :as fdat-test])
  #?(:clj (:import (java.io ByteArrayInputStream
                            ByteArrayOutputStream))))




;;;;;;;;;; Ser/de


(defn serialize

  ""

  [x]

  (let [options (fdat.transit/writer-options)]
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

  ""

  [x]

  (transit/read (transit/reader #?(:clj (ByteArrayInputStream. (.toByteArray x)))
                                :json
                                {:handlers (fdat.transit/handler-in)})
                #?(:cljs x)))




(defn rebuild-n

  ""

  [imeta n]

  (fdat-test/rebuild-serde imeta
                           n
                           serialize
                           deserialize))




;;;;;;;;;;


(t/deftest build

  (let [f-data  (serialize fdat-test/f)
        f-built (deserialize f-data)]
    (t/is (= 12
             (fdat-test/f 3)
             (f-built 3)
             ((rebuild-n fdat-test/f
                         10) 3))
          "Rebuilding a function"))


   (let [sq-data  (serialize fdat-test/sq)
         sq-built (deserialize sq-data)]
    (t/is (= (take 100
                   (range))
             (take 100
                   fdat-test/sq-built)
             (take 100
                   (rebuild-n fdat-test/sq
                              10)))
          "Rebuilding an infinite sequence")))
