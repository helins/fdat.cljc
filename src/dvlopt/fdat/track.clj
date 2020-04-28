(ns dvlopt.fdat.track

  "Black listing and white listing of namespaces for capturing.
  
   Cf. README"

  {:author "Adam Helinski"}

  (:require [clojure.edn :as edn]
            [dvlopt.void :as void]))




;;;;;;;;;;


(def ^:private -*auth

  ;; Global data. See [[clear!]] and [[env-reset!]].

  (atom nil))
        



(defn auth

  "Retrieves the current state."

  []

  @-*auth)




(defn clear!

  "C"

  []

  (reset! -*auth
          {:default true
           :rules   {}}))




(defn default!

  "Sets the :default value."

  [enabled?]

  (swap! -*auth
         assoc
         :default
         enabled?))




(defn enabled?

  "Is `nspace` enabled for capturing?"

  [nspace]

  (let [auth-2 (auth)
        status (get (:rules auth-2)
                    nspace)]
    (if (:default auth-2)
      (void/alt status
                true)
      (true? status))))




(defn- -read-prop

  ;; Reads a system property, defaults to env otherwise.

  [^String prop]

  (edn/read-string (or (System/getProperty prop)
                       (System/getenv (.replace prop
                                                "."
                                                "_")))))




(defn env-reset!

  "Resets to new rules following system properties and env."

  []

  (reset! -*auth
          {:default (void/alt (-read-prop "fdat.track.default")
                              true)
           :rules   (as-> {}
                          rules
                      (reduce #(assoc %1 %2 false)
                              rules
                              (-read-prop "fdat.track.black-list"))
                      (reduce #(assoc %1 %2 true)
                              rules
                              (-read-prop "fdat.track.white-list")))}))




(defn rules!

  "Sets new rules. If `rule` is false, means the namespace must be black-listed, true means
   white-listed. Nil means neither."

  [ns->rule]

  (swap! -*auth
         update
         :rules
         (fn update-rules [rules]
           (reduce-kv void/assoc-strict
                      rules
                      ns->rule))))




;;;;;;;;;; Crucial to do that before compiling any user code.


(env-reset!)
