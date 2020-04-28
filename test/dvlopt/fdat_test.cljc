(ns dvlopt.fdat-test

  "Testing the core API."

  {:author "Adam Helinski"}

  (:require [clojure.test         :as    t]
            [dvlopt.fdat          :as    fdat]
            [dvlopt.fdat.external :as    fdat.external
                                  :refer [mult-referred]])
  #?(:cljs (:require-macros [dvlopt.fdat])))




;;;;;;;;;; Useful for testing ser/de in other namespaces


(defn recall-serde

  "Ser/de `n` times `imeta`."

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

  "Basic shallow recalling using [[recall-serde]]."

  [imeta n]

  (recall-serde imeta
                n
                fdat/memento
                (comp fdat/recall
                      :snapshot)))




;;;;;;;;;; Curriable functions


(defn pre-inc

  ([f]

   (fn curried [n]
     (pre-inc f
              n)))


  ([f n]

   (f (inc n))))




(defn mult

  ([n]

   (fn curried [m]
     (mult n
           m)))

  ([n m]

   (* n
      m)))




;; Adding those functions to the global registry.

(fdat/register {'clojure.core/range          range
                `fdat.external/mult-referred mult-referred
                `fdat.external/pre-inc       fdat.external/pre-inc
                ::mult                       [1 mult]
                `pre-inc                     [1 pre-inc]})




;;;;;;;;;; Used tests tests, also useful for dev


(def f
     (fdat/? (pre-inc (fdat/? ::mult
                              (mult 3)))))


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




;;;;;;;;;; Assertions


(t/deftest recall

  (t/is (= 12
           (f 3)
           (f-recalled 3)
           ((recall-n f
                      10) 3)
           ((-> (fdat/? (fdat.external/pre-inc (fdat/? (mult-referred 3))))
                fdat/memento
                :snapshot
                fdat/recall)
            3))
        "Rebuilding a function")

  (t/is (= (take 100
                 (range))
           (take 100
                 sq-recalled)
           (take 100
                 (recall-n sq
                            10)))
        "Rebuilding an infinite sequence"))
