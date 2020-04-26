(ns dvlopt.fdat-test

  {:author "Adam Helinski"}

  (:require [clojure.test      :as t]
            [cognitect.transit :as transit]
            [dvlopt.fdat       :as fdat])
  #?(:cljs (:require-macros [dvlopt.fdat])))




;;;;;;;;;;



(defn pre-inc

  ([f]

   (fn curry [n]
     (pre-inc f
              n)))


  ([f n]

   (f (inc n))))




(defn mult

  ([n]

   (fn curry [m]
     (mult n
           m)))

  ([n m]

   (* n
      m)))




(fdat/register {'clojure.core/range  range
                `mult                mult
                `pre-inc             pre-inc})




(def f
     (fdat/? (pre-inc (fdat/? (mult 3)))))


(def f-data
     (fdat/datafy-deep f))


(def f-built
     (fdat/build-deep f-data))




(def sq
     (fdat/? (range)))


(def sq-data
     (fdat/datafy-deep sq))


(def sq-built
     (fdat/build-deep sq-data))




(defn rebuild-serde

  ""

  [imeta n serialize deserialize]

  (if (pos? n)
    (recur (-> imeta
               serialize
               deserialize)
           (dec n)
           serialize
           deserialize)
    imeta))




(defn rebuild-n

  ""

  [imeta n]

  (rebuild-serde imeta
                 n
                 fdat/datafy-deep
                 fdat/build-deep))




(t/deftest build

  (t/is (= 12
           (f 3)
           (f-built 3)
           ((rebuild-n f
                       10) 3))
        "Rebuilding a function")

  (t/is (= (take 100
                 (range))
           (take 100
                 sq-built)
           (take 100
                 (rebuild-n sq
                            10)))
        "Rebuilding an infinite sequence"))





