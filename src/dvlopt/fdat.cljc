(ns dvlopt.fdat

  "Serialization and deserialization utilities for IMetas such as function and infinite sequences,
   amongst other things.

   See README for the big picture."

  {:author "Adam Helinski"}

  (:require         [cljs.analyzer.api]
                    [clojure.edn       :as edn]
            #?(:clj [dvlopt.fdat.track :as track])
                    [dvlopt.void       :as void]))


;;;;;;;;;; MAYBEDO
;;
;; Automatically understand `partial` and anonymous functions? Named free functions?
;; Would be really useful, but less efficient to use (eg. the result of `partial` is
;; considered variadic although the curried fn might have a fixed arity.
;;
;; Leveraging :arglists in some way from the metadata of functions defined by `defn`?
;;
;; Better error messages when symbols are not properly resolved?




;;;;;;;;;; Registry containg functions for rebuildable IMetas


(def ^:private -*registry

  ;; Global map of k -> f.

  (atom {`partial-1  (fn partial-1 [[f a]]
                       (partial f a))
         `partial-2  (fn partial-2 [[f a b]]
                       (partial f a b))
         `partial-3+ (fn partial-3+ [[f a b & more]]
                       (fn partial-variadic [& even-more]
                         (apply f a b (concat more
                                              even-more))))}))




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
   {'some.ns/my-fn-1     my-fn-1        ;; Args can by variadic
    'some.ns/my-fn-2     [2 my-fn-2]    ;; Optimized destructuring for 2 args
    'some.ns/my-fn-3     nil            ;; Removes that key
    'some.ns/interned-fn #'interned-fn  ;; Reference to a var
    }
   ```

   Providing the number of arguments will result in faster function application by using destructuring instead
   of `apply`. Arities 0 to 8 can be optimized that way. Beyond, reverts to using `apply`. Providing `:no-apply`
   instead of a number means the function will not be called, simply returned in exchange of args. Actually, using
   `:no-apply` implies that it could refer to any IMeta, not particularly a function.

   Using that last idea, `Vars` (functions interned by using `defn` or any IMeta interned using `def`) are treated
   specially. The value they hold gets annotated and then added to the registry with an arity of :no-apply.

   For instance, some event handler that does something, it does not need any initialization:

   ```clojure

   (defn event-handler
     [event]
     ...)


   (register {'some.ns/event-handler #'event-handler})

   (= (::k (meta event-handler))
      'some.ns/event-handler)
   ```

   We see our `event-handler` now has the intended key in its metadata and it is referenced in the registry.
   Thus, it is conveniently serializable."

  ([k->f]

   (swap! -*registry
          register
          k->f))


  ([registry k->f]

   (reduce-kv (fn update-ks [registry-2 k f]
                (if f
                  (assoc registry-2
                         k
                         (if (coll? f)
                           ((get -appliers
                                 (first f)
                                 -variadic-applier) (second f))
                           (-variadic-applier f)))
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

  ([k imeta]

   (vary-meta imeta
              assoc
              ::k
              k))


  ([k args imeta]

   (if-some [args-2 (not-empty args)]
     (vary-meta imeta
                merge
                {::args args
                 ::k    k})
     (snapshot k
               imeta))))




#?(:clj

(defn- -enforce-sym

  [sym]

  (if (symbol? sym)
    sym
    (throw (IllegalArgumentException. (str "Must be symbol: " sym))))))




#?(:clj

(def ^:private -compiling-cljs?
  
  ;; Hack adapted from `taoensso.encore/compiling-cljs?`.

  (some-> (find-ns 'cljs.analyzer)
          (ns-resolve '*cljs-file*)
          deref)))


#?(:clj

;; Depending on whether we are compiling for CLJ or CLJS, symbols get resolved differently.

(if -compiling-cljs?

  (defn- -resolve

    [env x]

    (-enforce-sym x)
    (let [resolved (:name (cljs.analyzer.api/resolve env
                                                     x))]
      (if (= (namespace resolved)
             "cljs.core")
        (symbol "clojure.core"
                (name resolved))
        resolved)))


  (defn- -resolve

    [_env x]

    (-enforce-sym x)
    (symbol (resolve x)))))




#?(:clj
   
(defn- -autoresolve

  ;; Autoresolved keys like `foo (akin to autoresolved keywords like ::foo) are passed to
  ;; macros inside a quote form while keywords need no special treatment.

  [x]

  (or (cond
        (qualified-ident? x) x
        (and (sequential? x)
             (= (first x)
                'quote))     (second x))
      (throw (IllegalArgumentException. (str "Key for registry must be qualified symbol or keyword: "
                                             x))))))




#?(:clj

(defn- -ns-enabled?

  ;; Is the namespace of the `k` enabled or was it disable by compile time elision?
  ;;
  ;; Defaults to current namespace when `k` is not provided.

  ([]

   (track/enabled? (symbol (str *ns*))))


  ([k]

   (track/enabled? (symbol (or (namespace k)
                               (str *ns*)))))))




#?(:clj

(defn- -?

  ;; Used by [[?]].

  [k args user-args? call]

  (if (-ns-enabled? k)
    (cond
      (empty? args)   `(snapshot '~k
                                 ~call)
      user-args?      `(snapshot '~k
                                 ~args
                                 ~call)
      :else           (let [arg-bindings (take (count args)
                                               (repeatedly gensym))]
                        `(let ~(vec (interleave arg-bindings
                                                args))
                           (snapshot '~k
                                     (vector ~@arg-bindings)
                                     ~(cons (first call)
                                             arg-bindings)))))
    call)))




#?(:clj

;; See [[?]] arity 1.
;;
;; Automatic annotation of interned Vars is completely different depending on the platform.
;; CLJS does not have any of that Var metaprogramming and will never have.

(if -compiling-cljs?

  (defn -interned

    [call]

    (if (-ns-enabled?)
      (let [sym (second call)]
        `(do
           ~call
           (set! ~sym
                 (vary-meta ~sym
                            assoc
                            ::k
                            '~(symbol (str *ns*)
                                      (name sym))))))
      call))


  (defn- -interned

    [call]

    (if (-ns-enabled?)
      (let [k (symbol (str *ns*)
                      (name (second call)))]
        `(alter-var-root ~call
                         vary-meta
                         assoc
                         ::k
                         '~k))
      call))))






#?(:clj

;; See [[?]] arity 1.
;;
;; Special treatment and optimization of (partial ...) forms.

(defn- -partial

  ;;

  [args call]

  (-? (let [n-args (count args)]
        (cond
          (<= n-args
              1)      (throw (IllegalArgumentException. (str "Partial application without providing arguments: "
                                                             call)))
          (= n-args
             2)      `partial-1
          (= n-args
             3)      `partial-2
          :else      `partial-3+))
      args
      false
      call)))




;; So that Kaocha does not complain when testing for CLJS.
;;
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

  ([call]

   (if (list? call)
     (let [[f-sym & args] call]
       (cond
         (#{'def
            'defn} f-sym) (-interned call)
         (= f-sym
            'partial)     (-partial args
                                    call)
         :else            (-? (-resolve &env
                                        f-sym)
                              args
                              false
                              call)))
     (-? (-resolve &env
                   call)
         nil
         nil
         call)))


  ([k call]

   (-? (-autoresolve k)
       (when (and (list? call)
                  (not (#{"fn"
                          "fn*"} (name (first call)))))
         (rest call))
       false
       call))


  ([k args call]

   (-? (-autoresolve k)
       args
       true
       call))))
