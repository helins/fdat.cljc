(ns dvlopt.fdat-test

  "Testing the core API."

  {:author "Adam Helinski"}

  (:require [clojure.test :as t]
            [dvlopt.fdat  :as fdat #?(:clj  :refer
                                      :cljs :refer-macros) [?]])
  #?(:cljs (:require-macros [dvlopt.fdat])))




;;;;;;;;;; Utilities


(defn recall-serde

  "Ser/de `n` times `imeta`."

  [n serialize deserialize imeta]

  (if (pos? n)
    (recur (dec n)
           serialize
           deserialize
           (-> imeta
               serialize
               deserialize))
    imeta))




;;;;;;;;;; Using `?` and `!` in various ways
;;
;; Creating functions and sequences


(?
 (defn inc-mult

   "Simple function that will be furthered recaptured in various ways."

   ([x]

    (? (partial inc-mult
                x)))

   ([x y]

    (* (inc x)
       y))))




(def partial-inc-mult
     (? (partial inc-mult
                 2)))




(? ^{::fdat/apply 1}

 (defn curried-inc-mult

   ""
   
   ([x]

    (fn curried [y]
      (inc-mult x
                y)))))




(? ^{::fdat/apply 1}

  (defn curried-inc-mult-tracked

    ""

    ([x]

     (? ^{::fdat/key  curried-inc-mult-tracked
          ::fdat/args [x]}
        (fn curried [y]
          (inc-mult x
                    y))))))




(fdat/! ^{::fdat/apply :variadic}
        range)


(def sq-infinite
     (? (range)))


(def sq-finite
     (? (range 100)))




;;;;;;;;;; Not forgetting to register our keys


(fdat/register [inc-mult
                curried-inc-mult
                curried-inc-mult-tracked
                range])




;;;;;;;;;; Tests


(defn serde-suite

  "A serializer shall use this little suite to test its capacities."

  [serializer deserializer]

  (let [recall-n (partial recall-serde
                          10
                          serializer
                          deserializer)]

    (t/is (= (inc-mult 2
                       3)
             ((inc-mult 2) 3)
             (partial-inc-mult 3)
             ((recall-n (inc-mult 2)) 3)
             ((recall-n partial-inc-mult) 3)
             ((recall-n (? (curried-inc-mult 2))) 3)
             ((recall-n (curried-inc-mult-tracked 2)) 3))
          "Ser/de functions captured in different ways")


    (let [n (count sq-finite)]
      (t/is (= (take n
                     (range))
               sq-finite
               (take n
                     sq-infinite)
               (take n
                     (recall-n sq-infinite)))
            "Ser/de an infinite sequence"))))
