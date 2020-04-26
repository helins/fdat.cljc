(ns dvlopt.fdat

  ""

  {:author "Adam Helinski"}

  (:require [dvlopt.void :as void]))




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




;;;;;;;;;; Re-building IMetas from data


(defn- -build

  ""

  [mta k args registry fmap-args]

  (let [f (or (get registry
                   k)
              (throw (ex-info "Key not found to rebuild from data"
                              {::args     args
                               ::k        k
                               ::registry registry})))]
    (vary-meta (if (seq args)
                 (apply f
                        (fmap-args args))
                 (f))
               merge
               mta)))




(defn build

  ""

  ([mta]

   (build mta
          (registry)))


  ([mta registry]
  
   (-build mta
           (::k mta)
           (::args mta)
           registry
           identity)))




(defn build-deep

  ""

  ([mta]

   (build-deep mta
               (registry)))


  ([mta registry]

   (-build mta
           (::k mta)
           (::args mta)
           registry
           (fn fmap-args [args]
             (map (fn recursive [arg]
                    (if-some [k (::k arg)]
                      (-build arg
                              k
                              (::args arg)
                              registry
                              fmap-args)
                      arg))
                  args)))))



(defn- -datafy
  
  ;;

  [fmap-mta x]

  (let [mta (meta x)]
    (if (contains? mta
                   ::k)
      (fmap-mta mta)
      (if (fn? x)
        (throw (ex-info "Function does not have sufficient meta for datafication"
                        {::fn x}))
        x))))




(defn datafy

  ""

  [x]

  (-datafy identity
           x))




(defn datafy-deep

  ""

  [x]

  (-datafy (fn recur-args [mta]
             (if-some [args (get mta
                                 ::args)]
               (assoc mta
                      ::args
                      (map datafy-deep
                           args))
               mta))
           x))




(defn track

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
                {::args args-2
                 ::k    k})
     (track imeta
            k))))




(defn reg-track

  ""

  ([imeta f k]

   (reg-track imeta
              f
              k
              nil))


  ([imeta f k args]

   (register k
             f)
   (track imeta
          k
          args)))







#?(:clj

   (defn ns-sym

     ""

     [sym]

     (if (namespace sym)
       sym
       (or (some-> (ns-resolve 'clojure.core
                               sym)
                   symbol)
           (symbol (str *ns*)
                   (str sym))))))



#?(:clj

   (defn- -k
 
     ;;
 
     [k]
 
     (if (symbol? k)
       (ns-sym k)
       k)))




#?(:clj
(defmacro ?

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
           (track ~(list* f-sym-2
                          args-2)
                  '~f-sym-2
                  (vector ~@args-2))))
      call)))
)





