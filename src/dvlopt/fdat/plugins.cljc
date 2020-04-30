(ns dvlopt.fdat.plugins

  "Utilities helping writing plugins for serializers.
  
   The interested reader will carefully pay attention to how the current ones have been
   implemented. It is not that complicated but it is not exactly straightforward."
  
  (:require [dvlopt.fdat :as fdat]
            [dvlopt.void :as void]))




;;;;;;;;;; Datifying IMetas and rebuilding them


(defn develop

  "At deserialization, akin to developing a picture in the olden days, rebuild an IMeta
   from a :snapshot (see [[Memento]])."

  ([metadata]

   (develop fdat/registry
            metadata))


  ([registry metadata]

   (let [args (::fdat/args metadata)
         k    (::fdat/key metadata)
         f    (or (registry k)
                  (throw (ex-info (str "Key not found to rebuild from data: " k)
                                  (void/assoc {::fdat/key      k
                                               ::fdat/registry registry}
                                              ::fdat/args
                                              args))))]
     (vary-meta (f args)
                merge
                metadata))))




(defrecord Memento [snapshot])


(defn memento

  "If `x` has at least a `:dvlopt.fdat/key` in it metadata, meaning it has been captured
   and is eligeable for serialization, returns a `Memento` record.
  
   Nil otherwise. Safe to call on any value.
  

   Serializers typically deal in concrete types, thus here is one. It simply stores the
   metadata of the IMeta under `:snapshot`. This metadata will be needed at deserialization.

   See [[develop]]."

  [x]

  (let [mta (meta x)]
    (when (contains? mta
                     ::fdat/key)
      (Memento. mta))))
