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




(defn- -variadic-applier

  ;;

  [f]

  (fn variadic-applier [args]
    (apply f
           args)))




(def ^:private -appliers

  {0         (fn applier-n-0 [f]
               (fn apply-n-0 [_args]
                 (f)))
   1         (fn applier-n-1 [f]
               (fn apply-n-1 [[a]]
                 (f a)))
   2         (fn applier-n-2 [f]
               (fn apply-n-2 [[a b]]
                 (f a b)))
   3         (fn applier-n-3 [f]
               (fn apply-n-3 [[a b c]]
                 (f a b c)))
   4         (fn applier-n-4 [f]
               (fn apply-n-4 [[a b c d]]
                 (f a b c d)))
   5         (fn applier-n-5 [f]
               (fn apply-n-5 [[a b c d e]]
                 (f a b c d e)))
   6         (fn applier-n-6 [f]
               (fn apply-n-6 [[a b c d e f]]
                 (f a b c d e f)))
   7         (fn applier-n-7 [f]
               (fn apply-n-7 [[a b c d e f g]]
                 (f a b c d e f g)))
   8         (fn applier-n-8 [f]
               (fn apply-n-8 [[a b c d e f g h]]
                 (f a b c d e f g h)))
   :no-apply (fn not-applier [f]
               (fn no-apply[_args]
                 f))})




(defn register

  ""

  ([k->f]

   (swap! -*registry
          register
          k->f))


  ([registry k->f]

   (reduce-kv (fn update-ks [registry-2 k f]
                (if f
                  (assoc registry-2
                         k
                         (if (fn? f)
                           (-variadic-applier f)
                           ((get -appliers
                                 (first f)
                                 -variadic-applier) (second f))))
                  (dissoc registry-2
                          k)))
              registry
              k->f)))




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
                                  (void/assoc {::k        k
                                               ::registry registry}
                                              ::args
                                              args))))]
     (vary-meta (f args)
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
