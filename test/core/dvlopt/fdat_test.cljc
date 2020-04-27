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
                `mult                [1 mult]
                `pre-inc             [1 pre-inc]})




(def f
     (fdat/? (pre-inc (fdat/? (mult 3)))))


(def f-memento
     (fdat/memento f))


(def f-recalled
     (fdat/recall (:snapshot f-memento)))




(def sq
     (fdat/? (range)))


(def sq-memento
     (fdat/memento sq))


(def sq-recalled
     (fdat/recall (:snapshot sq-memento)))




(defn recall-serde

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




(defn recall-n

  ""

  [imeta n]

  (recall-serde imeta
                n
                fdat/memento
                (comp fdat/recall
                      :snapshot)))




(t/deftest recall

  (t/is (= 12
           (f 3)
           (f-recalled 3)
           ((recall-n f
                      10) 3))
        "Rebuilding a function")

  (t/is (= (take 100
                 (range))
           (take 100
                 sq-recalled)
           (take 100
                 (recall-n sq
                            10)))
        "Rebuilding an infinite sequence"))





