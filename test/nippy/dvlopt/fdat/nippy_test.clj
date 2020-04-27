(ns dvlopt.fdat.nippy-test

  {:author "Adam Helinski"}

  (:require [clojure.test      :as t]
            [dvlopt.fdat       :as fdat]
            [dvlopt.fdat.nippy :as fdat.nippy]
            [dvlopt.fdat-test  :as fdat-test]
            [taoensso.nippy    :as nippy]))




;;;;;;;;;; Ser/de


(defn rebuild-n

  ""

  [imeta n]

  (fdat-test/rebuild-serde imeta
                           n
                           nippy/freeze
                           nippy/thaw))




;;;;;;;;;;


(def f-data
     (nippy/freeze fdat-test/f))


(def f-built
     (nippy/thaw f-data))


(def sq-data
     (nippy/freeze fdat-test/sq))


(def sq-built
     (nippy/thaw sq-data))




;;;;;;;;;;


(t/deftest build

  (t/is (= 12
           (fdat-test/f 3)
           (fdat-test/f-built 3)
           ((rebuild-n fdat-test/f
                       10) 3))
        "Rebuilding a function")


  (t/is (= (take 100
                 (range))
           (take 100
                 fdat-test/sq-built)
           (take 100
                 (rebuild-n fdat-test/sq
                            10)))
        "Rebuilding an infinite sequence"))
