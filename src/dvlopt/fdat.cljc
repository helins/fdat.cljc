(ns dvlopt.fdat

  "Serialization and deserialization utilities for IMetas such as function and infinite sequences,
   amongst other things.

   See README for the big picture."

  {:author "Adam Helinski"}

  (:require         [clojure.edn       :as edn]
            #?(:clj [dvlopt.fdat.track :as track])
                    [dvlopt.void       :as void]))




;;;;;;;;;; Gathering declarations


(declare ^:private -afy-imeta)




;;;;;;;;;; Registry containg functions for rebuildable IMetas


(def ^:private -*registry

  ;; Global map of k -> f.

  (atom {}))




(defn registry

  "Access to the global registry."

  ([]

   @-*registry)


  ([k]

   (get (registry)
        k)))




(defn- -variadic-applier

  ;; Default applier.

  [f]

  (fn variadic-applier [args]
    (apply f
           args)))




(def ^:private -appliers

  ;; All appliers providing optimized destructuring and application of args.

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

  "Adds or removes functions for keys.

   `k->f` is a map where `k` is an arbitrary key (a qualified symbol or qualified keyword) and `f` specifies
   a function such as:

   ```clojure
   {'some.ns/my-fn-1 my-fn-1      ;; Args can by variadic
    'some.ns/my-fn-2 [2 my-fn-2]  ;; Optimized destructuring for 2 args
    'some.ns/my-fn-3 nil          ;; Removes that key
    }
   ```

   Providing the number of arguments will result in faster function application by using destructuring instead
   of `apply`. Arities 0 to 8 can be optimized that way. Beyond, reverts to using `apply`. Providing `:no-apply`
   instead of a number means the function will not be called, simply returned in exchange of args."

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




;;;;;;;;;; Datifying IMetas and rebuilding them


(defrecord Memento [snapshot])




(defn memento

  "If `x` has at least a ::key in it metadata, returns a Memento.
  
   Nil otherwise. Safe to call on any value.
  
   Serializers typically deal in concrete types. Here is one.
  
   A Memento simply stores metadata under `:snapshot`. Those metadata can then
   be given back to the serializer as a simple map.
  
   See also [[recall]]."

  [x]

  (let [mta (meta x)]
    (when (contains? mta
                     ::k)
      (Memento. mta))))




(defn recall

  "\"Recall how an IMeta was was using its former metadata.\"

   Given `metadata` containing a ::key and (if needed) ::args, rebuilds an IMeta by calling
   the appropriate function from the `registry` (global if not provided).
 
   Used as a last step in deserialization. Is NOT recursive, meaning that if an arg need to be
   recalled, it will not. This is actually what is needed as deserializers work that way, in
   a depth-first manner.
 
   See also [[memento]]."

  ([metadata]

   (recall registry
           metadata))


  ([regsitry metadata]

   (let [args (::args metadata)
         k    (::k metadata)
         f    (or (registry k)
                  (throw (ex-info (str "Key not found to rebuild from data: " k)
                                  (void/assoc {::k        k
                                               ::registry registry}
                                              ::args
                                              args))))]
     (vary-meta (f args)
                merge
                metadata))))




(defn snapshot

  "Manual annotations of how `imeta` can be [[recall]]ed using `k` and `args`.
   
   Simply puts that information in its metadata.

   Typically, the [[?]] macro is prefered as it does this automatically."

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




#?(:clj
   
(defn ns-sym

  "Shows how an unqualified symbol gets qualified in the context of [[?]].

   CLJ only."
  
  [sym]
  
  (if (qualified-symbol? sym)
    sym
    (or (some-> (ns-resolve 'clojure.core
                            sym)
                symbol)
        (symbol (str *ns*)
                (str sym))))))




#?(:clj

(defn- -?

  ;; Used bu [[?]].

  [k f-sym args call]

  (if (track/enabled? (symbol (namespace k)))
    (let [args-2 (take (count args)
                       (repeatedly gensym))]
      `(let ~(vec (interleave args-2
                             args))
         (snapshot ~(list* f-sym
                           args-2)
                   '~k
                   (vector ~@args-2))))
    call)))




#?(:clj
   
(defmacro ?

  "Captures how an imeta is created so that it can be turned into a [[memento]], thus become serializable.

   Analyses the given form which is a function call, extracting the first item as a key, the rest as args,
   and puts this information in the metadata after the form is evaled.

   ```clojure
   (? (range))

   (= (meta *1)
      {::key 'clojure.core/range})


   (? (my-f 1 2 3))

   (= (meta *1)
      {::key  'my.namespace/my-f
       ::args [1 2 3]})     
   ```

   Providing a key explicitly:

   ```clojure
   (? ::my-key
      (my-f 1 2 3))
   ```

   The function symbol, if it is not already, gets qualified either to 'clojure.core if it is part of it,
   or to the current namespace. Thus, function calls to other namespaces should always be qualified.
   This behavior mathes what can be achieved in CLJS.

   When supplied explicitely, a key is used as is. A key must be a qualified symbol or qualified keyword.

   Uses [[snapshot]] under the hood.
  
   README documents how this capturing can be turned on a per-namespace basis."

  ([[f-sym & args :as call]]
 
   (-? (if (symbol? f-sym)
         (ns-sym f-sym)
         (throw (IllegalArgumentException. (str "Function name must be symbol: " f-sym))))
       f-sym
       args
       call))

  ([k [f-sym & args :as call]]

   (-? k
       f-sym
       args
       call))))
