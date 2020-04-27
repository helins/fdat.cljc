(ns dvlopt.fdat.nippy

  ""

  {:author "Adam Helinski"}

  (:require [dvlopt.fdat    :as fdat]
            [taoensso.nippy :as nippy])
  (:import clojure.lang.IMeta
           dvlopt.fdat.Memento
           dvlopt.fdat.IMemento
           java.io.DataOutput))




;;;;;;;;;;


(extend-protocol nippy/IFreezable2

  IMeta

    (-freeze-with-meta! [x ^DataOutput out]
      (let [x-2 (fdat/afy x)]
        (if (fdat/memento? x-2)
          (nippy/-freeze-without-meta! x-2
                                       out)
          (do
            (when-let [mta (meta x-2)]
              (.writeByte out
                          25)
              (nippy/-freeze-without-meta! mta
                                           out))
            (nippy/-freeze-without-meta! x-2
                                         out))))))




(nippy/extend-freeze Memento ::fdat/memento
                     
  [memento out]

  (nippy/freeze-to-out! out
                        (fdat/afy memento)))




(nippy/extend-thaw ::fdat/memento

  [in]

  (-> in
      nippy/thaw-from-in!
      fdat/memento
      fdat/build))
