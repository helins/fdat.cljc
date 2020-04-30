(ns dvlopt.fdat.plugins.nippy-test

  "Testing ser/de using Nippy."

  {:author "Adam Helinski"}

  (:require [clojure.test              :as t]
            [dvlopt.fdat               :as fdat :refer [?]]
            [dvlopt.fdat.plugins.nippy :as fdat.plugins.nippy]
            [dvlopt.fdat-test          :as fdat-test]
            [taoensso.nippy            :as nippy]))




;;;;;;;;;; Used tests tests, also useful for dev


(fdat.plugins.nippy/init)



(t/deftest capturing

  (fdat-test/serde-suite nippy/freeze
                         nippy/thaw))
