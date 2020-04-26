(ns dvlopt.fdat.nippy

  ""

  {:author "Adam Helinski"}

  (:require [clojure.core.protocols :as clj.protocols]
            [dvlopt.fdat            :as fdat]
            [taoensso.nippy         :as nippy])
  (:import clojure.lang.IMeta
           java.io.DataOutput))




;;;;;;;;;;


(deftype Ghost [mta]

  clj.protocols/Datafiable

    (datafy [_]
      mta))




;;;;;;;;;;


(extend-protocol nippy/IFreezable2

  IMeta

    (-freeze-with-meta! [x ^DataOutput out]
      (let [mta (meta x)
            k   (::fdat/k mta)]
        (if k
          (nippy/-freeze-without-meta! (Ghost. mta)
                                       out)
          (do
            (when mta
              (.writeByte out
                          25)
              (nippy/-freeze-without-meta! mta
                                           out))
            (nippy/-freeze-without-meta! x
                                         out))))))




(nippy/extend-freeze Ghost ::Ghost
                     
  [ghost out]

  (nippy/freeze-to-out! out
                        (clj.protocols/datafy ghost)))




(nippy/extend-thaw ::Ghost

  [in]

  (let [mta (nippy/thaw-from-in! in)]
    (with-meta (fdat/build mta)
               mta)))
