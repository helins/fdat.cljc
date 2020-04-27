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

  ([]

   @-*registry)


  ([k]

   (get (registry)
        k)))




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


(defrecord Memento [snapshot])




(defn memento

  ""

  [x]

  (let [mta (meta x)]
    (when (contains? mta
                     ::k)
      (Memento. mta))))




(defn recall

  ""

  ([mta]

   (recall registry
           mta))


  ([regsitry mta]

   (let [args (::args mta)
         k    (::k mta)
         f    (or (registry k)
                  (throw (ex-info (str "Key not found to rebuild from data: " k)
                                  {::args     args
                                   ::k        k
                                   ::registry registry})))]
     (vary-meta (if (seq args)
                  (apply f
                         args)
                  (f))
                merge
                mta))))




(defn snapshot

  ""

  ([imeta k]

   (vary-meta imeta
              assoc
              ::k
              k))


  ([imeta k args]

   (if-some [args-2 (not-empty args)]
     (vary-meta imeta
                merge
                {::args args
                 ::k    k})
     (snapshot imeta
               k))))




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
                   (snapshot ~(list* f-sym-2
                                     args-2)
                             '~f-sym-2
                             (vector ~@args-2))))
              call))))
