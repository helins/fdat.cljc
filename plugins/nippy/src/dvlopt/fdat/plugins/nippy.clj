(ns dvlopt.fdat.plugins.nippy

  "Ser/de for Nippy.

   Simply must be required.
  
   See README for examples."

  {:author "Adam Helinski"}

  (:require [dvlopt.fdat    :as fdat]
            [taoensso.nippy :as nippy])
  (:import clojure.lang.IMeta
           dvlopt.fdat.Memento
           java.io.DataOutput))




;;;;;;;;;; Nippy extensions


(defn init

  "Prepares ser/de using the given `registry`, defaulting to the global one."

  ([]

   (init fdat/registry))


  ([registry]

   (extend-protocol nippy/IFreezable2

     IMeta

       ;; This one is a bit tricky as it does not use public values.
       ;; 25 is the byte header for metas.

       (-freeze-with-meta! [x ^DataOutput out]
         (if-some [memento (fdat/memento x)]
           (nippy/-freeze-without-meta! memento
                                        out)
           (do
             (when-let [mta (meta x)]
               (.writeByte out
                           25)
               (nippy/-freeze-without-meta! mta
                                            out))
             (nippy/-freeze-without-meta! x
                                          out)))))




   (nippy/extend-freeze Memento ::fdat/memento
                        
     [memento out]

     (nippy/freeze-to-out! out
                           (:snapshot memento)))




   (nippy/extend-thaw ::fdat/memento

     [in]

     (fdat/recall (nippy/thaw-from-in! in)))))
