(ns dvlopt.fdat.external

  "Testing namespacing of symbol keys."

  {:author "Adam Helinski"})





;;;;;;;;;; Mimicks `dvlopt.fdat-test`


(defn pre-inc

  ([f]

   (fn curried [n]
     (pre-inc f
              n)))


  ([f n]

   (f (inc n))))




(defn mult-referred

  ([n]

   (fn curried [m]
     (mult-referred n
                    m)))

  ([n m]

   (* n
      m)))
