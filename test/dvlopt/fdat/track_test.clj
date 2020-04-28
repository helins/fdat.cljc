(ns dvlopt.fdat.track-test

  "Testing enabling and disabling of capturing in namespaces."

  {:author "Adam Helinski"}

  (:require [clojure.test      :as t]
            [dvlopt.fdat.track :as fdat.track]))




;;;;;;;;;; Assertions


(t/deftest env-reset!

  (System/setProperty "fdat.track.default"
                      "false")
  (System/setProperty "fdat.track.black-list"
                      "[bl-1,bl-2]")
  (System/setProperty "fdat.track.white-list"
                      "[wl-1,wl-2]")

  (let [auth (fdat.track/auth)]
    (fdat.track/env-reset!)
    (t/is (= {:default false
              :rules   {'bl-1 false
                        'bl-2 false
                        'wl-1 true
                        'wl-2 true}}
             (fdat.track/auth))
          "Reading rules from env or system properties")
    (t/is (true? (fdat.track/enabled? 'wl-1)))
    (t/is (false? (fdat.track/enabled? 'bl-1)))
    (t/is (false? (fdat.track/enabled? 'else)))
    (fdat.track/default! true)
    (t/is (true? (fdat.track/enabled? 'else)))
    (fdat.track/clear!)
    (fdat.track/default! (:default auth))
    (fdat.track/rules! (:rules auth))))
