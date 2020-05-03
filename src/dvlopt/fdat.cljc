(ns dvlopt.fdat

  "Allowing any IMeta (functions, sequences, finite, infinite, etc) to be serializable.

   Provides the all-purpose [[?]] macro which discreely adds needed metadata to those IMetas and
   the [[register]] function which which keeps track of how to deserialize them.

   The serialization itself happens via a plugin (eg. Nippy plugin).

   Also provides what is needed to write a plugin for a serializer.

   <!>

   It is important to read the README first which truly provides the big picture.
  
   <!>"

  {:author "Adam Helinski"}

  (:require #?(:clj [cljs.analyzer.api])
            #?(:clj [dvlopt.fdat.track :as track])
                    [dvlopt.void       :as void]))




;;;;;;;;;; MAYBEDO
;;
;; Leveraging :arglists in some way from the metadata of functions defined by `defn`?
;;
;; Capturing for forms like (do), (let), (if)?




;;;;;;;;;; Registry containing functions for rebuilding IMetas


(def basic-registry

  "All registries must be based on this one."

  {`partial-1  (fn partial-1 [[f a]]
                 (partial f a))
   `partial-2  (fn partial-2 [[f a b]]
                 (partial f a b))
   `partial-3+ (fn partial-3+ [[f a b & more]]
                 (fn partial-variadic [& even-more]
                   (apply f a b (concat more
                                        even-more))))})




(def ^:private -*registry

  ;; See [[register]].

  (atom basic-registry))




(defn registry

  "Access to the global registry.
  
   See [[register]]."

  ([]

   @-*registry)


  ([k]

   (get (registry)
        k)))




(defn- -applier-none

  ;; Default applier, ignores arguments and simply retrieve the enclosed IMeta.

  [imeta]

  (fn apply-none [_args]
    imeta))




(def ^:private -appliers

  ;; All appliers providing optimized destructuring and application of args.
  ;;
  ;; An applier knows of to apply arguments to a function.

  {:none     -applier-none
   :variadic (fn applier-variadic [f]
               (fn apply-all [args]
                 (apply f
                        args)))
   0         (fn applier-n-0 [f]
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
                 (f a b c d e f g h)))})




(defn register

  "To a `registry`, adds or removes `IMetas` captured by [[?]].

   If none is provided, modfies the global one.

   A registry is a map of keys from captured `IMetas` to functions accepting arguments and
   applying them to those `IMetas`.

   While capturing is essential for serialization, having a `registry` is essential for
   deserialization."

  
  ([imetas]

   (swap! -*registry
          register
          imetas))


  ([registry imetas]

   (reduce (fn register-imeta [registry-2 imeta]
             (let [mta (meta imeta)]
               (if-some [k (::key mta)]
                 (if (= (namespace k)
                        "dvlopt.fdat")
                   (throw (ex-info (str "Cannot overwrite protected key: "
                                        k)
                                   {::key k}))
                   (assoc registry-2
                          k
                          (let [applier (get -appliers
                                             (::apply mta)
                                             -applier-none)]
                            (applier imeta))))
                 (throw (ex-info "No key supplied in metadata"
                                 {::imeta imeta})))))
           registry
           imetas))


  ([registry imetas & more]

   (reduce register
           registry
           (list* imetas
                  more))))




;;;;;;;;;; Utilities for [[?]] and [[!]]


#?(:clj (do


(def ^:private -compiling-cljs?
  
  ;; Hack adapted from `taoensso.encore/compiling-cljs?`.

  (some-> (find-ns 'cljs.analyzer)
          (ns-resolve '*cljs-file*)
          deref))




(defn -enforce-resolved

  [resolved original-sym]

  (or resolved
      (throw (IllegalArgumentException. (str "Unable to resolve symbol: "
                                             original-sym)))))


(defn- -enforce-sym

  [sym]

  (if (symbol? sym)
    sym
    (throw (IllegalArgumentException. (str "Must be symbol: " sym)))))




(defn- -ns-enabled?

  ;; Is the namespace of the `sym` enabled or was it disable by compile time elision?
  ;;
  ;; Defaults to current namespace when `k` is not provided.

  ([]

   (track/enabled? (symbol (str *ns*))))


  ([sym-resolved]

   (track/enabled? (symbol (or (namespace sym-resolved)
                               (str *ns*))))))))




;;;;;;;;;; Resolving symbols and using keys


#?(:clj (do

;; Depending on whether we are compiling for CLJ or CLJS, symbols get resolved differently.
;;
;; On CLJS, symbols resolving to the `cljs.core` namespace ends up being rather namespaced under
;; `clojure.core` for consistency.

(if -compiling-cljs?

  (defn- -resolved-sym

    [env sym]

    (let [resolved (-> (cljs.analyzer.api/resolve env
                                                  (-enforce-sym sym))
                       :name
                       (-enforce-resolved sym))]
      (if (= (namespace resolved)
             "cljs.core")
        (symbol "clojure.core"
                (name resolved))
        resolved)))


  (defn- -resolved-sym

    [_env sym]

    (-> (resolve (-enforce-sym sym))
        (-enforce-resolved sym)
        symbol)))




(defn- -key

  ;; Tries to make a valid key given what is provided (eg. unnamespaced symbol).
  ;;
  ;; Throws aggressively, not failing fast will produce unwanted results.

  [env x]

  (cond
    (keyword? x)      (if (qualified-keyword? x)
                        x
                        (throw (IllegalArgumentException. (str "A keyword key must be qualified: "
                                                               x))))
    (symbol? x)       (-resolved-sym env
                                     x)
    (and (sequential? x)
         (= (first x)
            'quote))  (let [sym (second x)]
                        (if (qualified-symbol? sym)
                          sym
                          (throw (IllegalArgumentException. (str "Quoted symbol must be qualified: "
                                                                 sym)))))
    :else             (throw (IllegalArgumentException. (str "Key must be keyword or symbol: "
                                                             x)))))))




;;;;;;;;;; Preparing metadata for [[?]]


#?(:clj (do


(defn- -?meta

  ;; For now, simply updates the key.

  [mta k]
  
  (assoc mta
         ::key
         `(quote ~k)))))




;;;;;;;;;; Subimplementations of [[?]] depending on the kind of form it receives


#?(:clj

(defn- -?call

  ;; Used by [[?]] when the form is a regular function call.
  ;;
  ;; MAYBEDO. For the time being, cannot distinguish between a function call and
  ;; a macro call.

  [env expr mta]

  (let [[f-sym & args] expr
        k              (or (some->> (::key mta)
                                    (-key env))
                           (-resolved-sym env
                                          f-sym))]
    (if (-ns-enabled? k)
      (let [mta-2 (-?meta mta
                          k)]
        (if args
          (let [arg-bindings (vec (take (count args)
                                        (repeatedly gensym)))]
            `(let ~(vec (interleave arg-bindings
                                    args))
               (vary-meta ~(cons f-sym
                                 arg-bindings)
                          merge
                          ~(assoc mta-2
                                  ::args
                                  arg-bindings))))
          `(vary-meta ~expr
                      merge
                      ~mta-2)))
      expr))))




#?(:clj (do


(defn- -key-interned

  ;; Qualifies the given symbol to the current namespace if no key is explicitly provided.

  [env mta var-sym]

  (or (some->> (::key mta)
               (-key env))
      (symbol (str *ns*)
              (name var-sym))))


;; Used by [[?]] when the form is a definition (`defn` or `def`).
;;
;; Automatic annotation of interned Vars is completely different depending on the platform.
;; CLJS does not have any of that Var metaprogramming and will never have.

(if -compiling-cljs?

  (defn- -?interned

    [env expr mta]

    (let [var-sym (second expr)
          k       (-key-interned env
                                 mta
                                 var-sym)]
      (if (-ns-enabled? k)
        `(let [v# ~expr]
           (set! ~var-sym
                 (vary-meta ~var-sym
                            merge
                            ~(-?meta mta
                                     k)))
           v#)
         expr)))


  (defn- -?interned

    [env expr mta]

    (let [k (-key-interned env
                           mta
                           (second expr))]
      (if (-ns-enabled? k)
        `(let [v# ~expr]
           (alter-var-root v#
                           vary-meta
                           merge
                           ~(-?meta mta
                                    k))
           v#)
         expr))))))




#?(:clj

;; Used by [[?]] when the form is a call to `partial`.
;;
;; Partial application is optimized and really convenient. Compile time elision happens relative to the
;; function that is being applied.

(defn- -?partial

  ;;

  [env expr mta]

  (let [expr-applied   (rest expr)
        [f-sym
         & args]       expr-applied
        f-sym-resolved (if (symbol? f-sym)
                         (-resolved-sym env
                                        f-sym)
                         (throw (IllegalArgumentException. (str "Partial application must be on symbol, not: "
                                                                f-sym))))]
    (if (-ns-enabled? f-sym-resolved)
      (let [n-args       (count args)
            arg-bindings (vec (take n-args
                                    (repeatedly gensym)))
            mta-2        (-> mta
                             (-?meta (cond
                                       (zero? n-args) (throw (IllegalArgumentException.
                                                               (str "Partial application without providing arguments: "
                                                                    expr)))
                                       (= n-args
                                          1)          `partial-1
                                       (= n-args
                                          2)          `partial-2
                                       :else          `partial-3+))
                             (assoc ::args
                                    (vec (cons f-sym
                                               arg-bindings))))]
        `(let ~(vec (interleave arg-bindings
                                args))
           (vary-meta ~(list* 'partial
                              f-sym
                              arg-bindings)
                      merge
                      ~mta-2)))))))




#?(:clj

(defn- -?unknown

  ;; Used by [[?]] when a key cannot be extracted from the form.

  [env form mta]

  (let [k (or (some->> (::key mta)
                       (-key env))
              (throw (IllegalArgumentException. (str "Key cannot be extracted and must be provided explicitly for: "
                                                     form))))]
    (if (-ns-enabled? k)
      `(vary-meta ~form
                  merge
                  ~(-?meta mta
                           k))
      form))))




#?(:clj
   
(defmacro ?

  "Captures how an `IMeta` is created and puts that information into the metadata of this very same `IMeta`,
   making it eligeable for serialization.

   README provides complete examples of the various kind of forms this macro understands."

  ([expr]

   (let [f (if (list? expr)
             (let [sym (first expr)]
               (cond
                 (#{'def
                    'defn
                    'defn-} sym) -?interned
                 (= sym
                    'partial)    -?partial
                 (#{'fn
                    'fn*} sym)   -?unknown
                 (symbol? sym)   -?call
                 :else           -?unknown))
             -?unknown)]
     (f &env
        expr
        (-> (meta expr)
            (dissoc :column)
            (dissoc :line)))))))




;;;;;;;;;;


#?(:clj (do

;; Core implementation of [[!]] varies depending on the platform.
;;
;; Once again, this is due to the fact that CLJS does not have real Vars.

(if -compiling-cljs?

  (defn- -!

    [env sym mta]

    (let [sym-resolved (-resolved-sym env
                                      sym)
          k            (or (some->> (::key mta)
                                    (-key env))
                           sym-resolved)]
      (when (-ns-enabled? sym-resolved)
        `(set! ~sym-resolved
               (vary-meta ~sym-resolved
                          merge
                          ~(-?meta mta
                                   sym-resolved))))))


  (defn- -!

    [env sym mta]

    (if-some [resolved (resolve sym)]
      (let [k (or (some->> (::key mta)
                           (-key env))
                  (symbol resolved))]
        (when (-ns-enabled? k)
          `(alter-var-root ~resolved
                           vary-meta
                           merge
                           ~(-?meta mta
                                    k)))))))




(defmacro !

  "While [[?]] captures an IMeta when it is created, it is sometimes needed to annotate
   already existing ones.
  
   This should be done scarcely as it is potentially dangerous to annotate IMetas one does
   not control. For instance, annotating a function from the standard library can result in
   unexpected results if two parties do that. It would be best to create an alias using `def`.
  
   Key will be the resolved symbol unless one is provided in the metadata, akin to [[?]]."

  [sym]

  (-! &env
      (-enforce-sym sym)
      (meta sym)))))
