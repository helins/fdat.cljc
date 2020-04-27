(ns dvlopt.fdat.track

  ""

  {:author "Adam Helinski"}
  (:require [clojure.edn :as edn]
            [dvlopt.void :as void]))




;;;;;;;;;;


(def ^:private -*auth

  ;;

  (atom nil))
        



(defn auth

  ""

  []

  @-*auth)




(defn default!

  ""

  [enabled?]

  (swap! -*auth
         assoc
         :default
         enabled?))




(defn enabled?

  ""

  [nspace]

  (let [auth-2 (auth)
        status (get (:rules auth-2)
                    nspace)]
    (if (:default auth-2)
      (void/alt status
                true)
      (true? status))))




(defn- -read-prop

  ;;

  [^String prop]

  (println :got (or (System/getProperty prop)
                       (System/getenv (.replace prop
                                                "."
                                                "_"))))
  (edn/read-string (or (System/getProperty prop)
                       (System/getenv (.replace prop
                                                "."
                                                "_")))))




(defn env-reset!

  ""

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




(defn reset-auth!

  ""

  []

  (reset! -*auth
          {:default true
           :rules   {}}))




(defn rules!

  ""

  [ns->rule]

  (swap! -*auth
         update
         :rules
         (fn update-rules [rules]
           (reduce-kv void/assoc-strict
                      rules
                      ns->rule))))




;;;;;;;;;;


(env-reset!)
