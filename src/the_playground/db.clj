(ns the-playground.db
  (:require [the-playground.util :as u]
            [slingshot.slingshot :refer [throw+]]))


(defn get-user-by-user-id
  [user-id db]
  (if-let [user (some
                 (fn [user]
                   (when (= user-id (u/get-user-id user))
                     user))
                 (:users @db))]
    user
    (throw+ {:type :user-not-found})))


(defn user-exists?
  [name db]
  (some #(= name (u/get-name %)) (:users @db)))


(defn create-user!
  [name db]

  (when (user-exists? name db) (throw+ {:type :user-already-exists}))

  (let [user-id (str (+ 100 (rand-int 900)))
        user [{:name "user-id" :value user-id}
              {:name "name" :value name}]]

    (swap! db update :users conj user)
    user))
