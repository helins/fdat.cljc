(ns dvlopt.fdat

  ""

  {:author "Adam Helinski"}

  (:require [clojure.core.protocols :as clj.protocols]
            [dvlopt.void            :as void]))




;;;;;;;;;; Gathering declarations


(declare ^:private -afy-imeta)




;;;;;;;;;; Registry containg functions for rebuildable IMetas


(def ^:private -*registry

  ;;

  (atom {}))




(defn registry

  ""

  []

  @-*registry)




(defn- -register

  ;;

  [registry k f]

  (void/assoc-strict registry
                     k
                     f))




(defn register

  ""

  ([hmap]

   (swap! -*registry
          (fn register-kv [registry]
            (reduce-kv -register
                       registry
                       hmap))))


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




;;;;;;;;;; Datifying IMetas and rebuilding them


(defprotocol IMemento

  ""

  (build [this]
         [this registry])


  (rebuild [this]
           [this registry]))




(defn- -build

  ;;

  [mta k args registry fmap-args]

  (let [f (or (get registry
                   k)
              (throw (ex-info (str "Key not found to rebuild from data: " k)
                              {::args     args
                               ::k        k
                               ::registry registry})))]
    (vary-meta (if (seq args)
                 (apply f
                        (fmap-args args))
                 (f))
               merge
               (assoc mta
                      `clj.protocols/datafy
                      -afy-imeta))))




(deftype Memento [mta]


  clj.protocols/Datafiable

    (datafy [_]
      mta)


  IMemento

    (build [this]
      (build this
             (registry)))


    (build [this registry]
      (-build mta
              (::k mta)
              (::args mta)
              registry
              identity))



    (rebuild [this]
      (rebuild this
               (registry)))


    (rebuild [this registry]
      (-build mta
              (::k mta)
              (::args mta)
              registry
              (partial map
                       (fn recursive [arg]
                         (if (instance? Memento
                                        arg)
                           (rebuild arg)
                           arg))))))




(defn memento

  ""

  [mta]

  (Memento. mta))




(defn memento?

  ""

  [x]

  (instance? Memento
             x))




(defn- -afy-imeta

  ;;

  [imeta]

  (Memento. (dissoc (meta imeta)
                    `clj.protocols/datafy)))




(defn afy

  ""

  [x]

  (clj.protocols/datafy x))




(defn- -recall

  ""

  [imeta k mta]

  (vary-meta imeta
             merge
             (-> mta
                 (assoc ::k
                        k)
                 (assoc `clj.protocols/datafy
                        -afy-imeta))))




(defn recall

  ""

  ([imeta k]

   (-recall imeta
            k
            nil))


  ([imeta k args]

   (-recall imeta
            k
            (some->> (not-empty args)
                     (assoc {}
                            ::args)))))




(defn reg-recall

  ""

  ([imeta f k]

   (reg-recall imeta
               f
               k
               nil))


  ([imeta f k args]

   (register k
             f)
   (recall imeta
           k
           args)))







#?(:clj (defn ns-sym

          ""
     
          [sym]
     
          (if (namespace sym)
            sym
            (or (some-> (ns-resolve 'clojure.core
                                    sym)
                        symbol)
                (symbol (str *ns*)
                        (str sym))))))




#?(:clj (defmacro ?

          ""

          [[f-sym & args :as call]]

          (let [f-sym-2 (if (symbol? f-sym)
                          (ns-sym f-sym)
                          (throw (IllegalArgumentException. (str "Function name must be symbol: " f-sym))))]
            (if (ns-enabled? (symbol (namespace f-sym-2)))
              (let [args-2 (take (count args)
                                 (repeatedly gensym))]
                `(let ~(vec (interleave args-2
                                       args))
                   (recall ~(list* f-sym-2
                                   args-2)
                           '~f-sym-2
                           (vector ~@args-2))))
              call))))
