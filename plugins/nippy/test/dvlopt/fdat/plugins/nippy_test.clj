(ns dvlopt.fdat.plugins.nippy-test

  "Testing ser/de using Nippy."

  {:author "Adam Helinski"}

  (:require [clojure.test              :as t]
            [dvlopt.fdat               :as fdat]
            [dvlopt.fdat.plugins.nippy :as fdat.nippy]
            [dvlopt.fdat-test          :as fdat-test]
            [taoensso.nippy            :as nippy]))




;;;;;;;;;; Ser/de


(defn rebuild-n

  "Ser/de `n` times `imeta` using Nippy."

  [imeta n]

  (fdat-test/recall-serde imeta
                          n
                          nippy/freeze
                          nippy/thaw))




;;;;;;;;;; Used tests tests, also useful for dev


(def f-data
     (nippy/freeze fdat-test/f))


(def f-recalled
     (nippy/thaw f-data))




(def sq-data
     (nippy/freeze fdat-test/sq))


(def sq-rebuild
     (nippy/thaw sq-data))




;;;;;;;;;; Assertions


(t/deftest build

  (fdat.nippy/init)

  (t/is (= 12
           (fdat-test/f 3)
           (f-recalled 3)
           ((rebuild-n f-recalled
                       10) 3))
        "Rebuilding a function")


  (t/is (= (take 100
                 fdat-test/sq)
           (take 100
                 sq-rebuild)
           (take 100
                 (rebuild-n sq-rebuild
                            10)))
        "Rebuilding an infinite sequence"))
