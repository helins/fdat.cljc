(ns dvlopt.fdat

  ""

  {:author "Adam Helinski"}

  (:require [clojure.core.protocols :as clj.protocols]
            [dvlopt.void            :as void])
  (:import (java.io ByteArrayInputStream
                    ByteArrayOutputStream)))




;;;;;;;;;; Registry


(def ^:private -*registry

  ;;

  (atom {}))




(defn registry

  ""

  []

  @-*registry)




(defn register

  ""

  ([hmap]

   (swap! -*registry
          void/merge
          hmap))


  ([k f]

   (swap! -*registry
          void/assoc-strict
          k
          f)))




(def ^:private -*enabled

  ;;

  (atom {:all?       true
         :namespaces {}}))
        



(defn ns-enabled?

  ""

  [nspace]

  (let [enabled @-*enabled
        status  (get (:namespaces enabled)
                     nspace)]
    (if (:all? enabled)
      (not (false? status))
      (true? status))))




(defn ns-enable-all

  ""

  [enable?]

  (swap! -*enabled
         assoc
         :all?
         enable?))



(defn ns-enable

  ""

  [ns->enabled?]

  (swap! -*enabled
         update
         :namespaces
         void/merge
         ns->enabled?))




;;;;;;;;;; Re-building from data


(defprotocol IBuildable

  ""

  (build [this]
         [this registry]

    ""))



(declare build*)

(defrecord Buildable [k args]

  IBuildable

    (build [this]
      (build this
             (registry)))

    (build [this registry]
      (build* k
              args
              registry))

  clj.protocols/Datafiable

    (datafy [_]
      [k args]))




(defn build*

  ""

  ([k args]

   (build* k
           args
           (registry)))


  ([k args registry]

   (println :building k args)
   (vary-meta (apply (or (get registry
                              k)
                         (throw (ex-info "Key not found to rebuild from data"
                                         {::args     args
                                          ::k        k
                                          ::registry registry})))
                      (map (fn recursive [arg]
                             (if (instance? Buildable
                                            arg)
                               (build arg
                                      registry)
                               arg))
                           args))
              assoc
              `clj.protocols/datafy
              (fn data [_]
                (Buildable. k
                            args)))))
              


(defn build*-2

  ""

  ([k args]

   (build*-2 k
           args
           (registry)))


  ([k args registry]

   (println :building k args)
   (apply (or (get registry
                   k)
              (throw (ex-info "Key not found to rebuild from data"
                              {::args     args
                               ::k        k
                               ::registry registry})))
          args)))




(def datafy

  ""

  clj.protocols/datafy)



(defn- -datafy

  ;;

  [f]

  (let [mta (meta f)]
    (Buildable. (::k mta)
                (::args mta))))




(defn track

  [metable k args]

  (vary-meta metable
             merge
             {;`clj.protocols/datafy -datafy
              ::args args
              ::k k}))



            ; (let [args-2 (map datafy
            ;                   args)]
            ;   (with-meta (fn datafy [_]
            ;                (Buildable. k
            ;                            args-2))
            ;              {::datafy? true}))))




(defmacro ?

  [[f-sym & args :as call]]

  (let [f-sym-2 (if (qualified-symbol? f-sym)
                  f-sym
                  (symbol (str *ns*)
                          (str f-sym)))]
    (if (ns-enabled? (symbol (namespace f-sym-2)))
      (let [args-2 (take (count args)
                         (repeatedly gensym))]
        `(let ~(vec (interleave args-2
                               args))
           (track ~(list* f-sym-2
                          args-2)
                  '~f-sym-2
                  (vector ~@args-2))))
      call)))






(comment



(require '[cognitect.transit :as transit])



(def out (ByteArrayOutputStream. 4096))

(def writer (transit/writer out
                            :json
                            {:handlers {clojure.lang.Fn (transit/write-handler "fdat"
                                                                               (fn [x]
                                                                                 (println :x x (meta x))
                                                                                 (meta x)))}}))


(transit/write writer ft/f)


(def in (ByteArrayInputStream. (.toByteArray out)))


(def reader (transit/reader in
                            :json
                            {:handlers {"fdat" (transit/read-handler (fn [mta]
                                                                       (with-meta (build*-2 (::k mta)
                                                                                            (::args mta))
                                                                                  mta)))}}))
(transit/read reader)







(require '[taoensso.nippy :as nippy])




(when (find-ns 'taoensso.nippy)


  (extend-protocol nippy/IFreezable2

    clojure.lang.IMeta

      (-freeze-with-meta! [x ^ByteArrayOutputStream out]
        (let [mta (meta x)]
          (when-not (::k mta)
            (.writeByte out
                        25)
            (nippy/-freeze-without-meta! mta
                                         out)))
          (nippy/-freeze-without-meta! x
                                       out)))


  (nippy/extend-freeze clojure.lang.Fn ::Fn
                       
    [f out]
  
    (let [mta (meta f)]
      (println :freeze-f mta f)
      (nippy/freeze-to-out! out
                            (cond
                              (contains? mta
                                         ::k) mta
                              :else           (nippy/throw-unfreezable f)))))


  (nippy/extend-thaw ::Fn
  
    [in]
  
    (let [mta (nippy/thaw-from-in! in)]
    (println :thaw-f mta)
      (with-meta (build*-2 (::k mta)
                           (::args mta))
                 mta)))


  )
  



)
  
(comment

  (require '[dvlopt.fdat-test :as ft])

  (nippy/thaw (nippy/freeze ft/f))
  (nippy/thaw (nippy/freeze *1))


  )
