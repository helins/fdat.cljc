(ns user

  "For daydreaming in the REPL." 

  (:require [cognitect.transit                :as transit]
            [criterium.core                   :as C]
            [clojure.core.protocols           :as clj.protocols]
            [clojure.repl]
            [clojure.test                     :as t]
            [dvlopt.fdat                      :as fdat :refer [?]]
            [dvlopt.fdat.plugins              :as fdat.plugins]
            [dvlopt.fdat.plugins.nippy        :as fdat.plugins.nippy]
            [dvlopt.fdat.plugins.transit      :as fdat.plugins.transit]
            [dvlopt.fdat.track                :as fdat.track]
            [dvlopt.fdat-test                 :as fdat-test]
            [dvlopt.fdat.plugins.nippy-test   :as fdat.plugins.nippy-test]
            [dvlopt.fdat.plugins.transit-test :as fdat.plugins.transit-test]
            [dvlopt.void                      :as void]
            [taoensso.nippy                   :as nippy]))




;;;;;;;;;;


(comment 
  
  ;; EXAMPLES FROM README


  (fdat.plugins.nippy/init)
  


  
  (?
   (defn hello
     [x]
     (str "Hello " x)))
  
  
  (fdat/register [hello])
  
  
  (def bytes
       (nippy/freeze hello))
  
  
  (def hello-2
       (nippy/thaw bytes))
  
  
  (println (hello "world"))




  (?
   (defn foo []))


  (= (meta foo)
     {::fdat/key 'user/foo})





  (? ^{::fdat/key :some.namespace/my-key}
     (def bar
          (fn [])))


  (= (meta bar)
     {::fdat/key :some.namespace/my-key})




  (= (meta
     (? (range 1000000)))
   {::fdat/key  'clojure.core/range
    ::fdat/args [1000000]})




  (? ^{::fdat/apply 1}
    (defn curried-add
      ([x]
       (fn curry [y]
         (curried-add x
                      y)))
      ([x y]
       (+ x
          y))))


  (fdat/register [curried-add])




  (def plus-3
       (? (curried-add 3)))


  (= (meta plus-3)
     {::fdat/key  'user/curried-add
      ::fdat/args [3]})

  (= 7
     ((-> plus-3
          nippy/freeze
          nippy/thaw)   4))




  (?
   (defn add [x y] (+ x y)))

  (= (meta add)
     {::fdat/key 'user/add})

  (fdat/register [add])

  (? (partial add
              3))

  (= (meta *1)
     {::fdat/key  'dvlopt.fdat/partial-1
      ::fdat/args [add 3]})




  ;; Throws, `some.ns/foo` cannot be resolved.

  (? ^{::fdat/key some.ns/foo
       ::extra    {:possible? true}}
     (fn [] 42))

  ;; Okay, because symbol is quoted

  (? ^{::fdat/key `fdat/invented}
     (fn [] 42))

  (meta *1)




  (fdat/! +)


  (= (meta +)
     {::fdat/key 'clojure.core/+})


  (fdat/register [+])


  (? (partial +
              3))
  (meta *1)




  ;; Serializing ideas (what if Plato was a Clojurist)

  (? ^{::fdat/apply 1}
     (defn entropy
       [n]
       (vec (repeatedly n
                        rand))))
  
  
  (fdat/register [entropy])
  
  
  (def random-numbers
       (? (entropy 2)))
  
  
  (= (meta random-numbers)
     {::fdat/key  'user/entropy
      ::fdat/args [2]})
  
  
  
  (not= (-> random-numbers
            nippy/freeze
            nippy/thaw)
        random-numbers)
  )
