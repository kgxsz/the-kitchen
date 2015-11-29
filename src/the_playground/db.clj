(ns the-playground.db
  (:require [the-playground.util :as u]
            [clj-uuid :as uuid]
            [slingshot.slingshot :refer [throw+]]))


(defn get-users-by
  [key-name value db]
  (filter (fn [user] (= value (key-name user))) (:users @db)))


(defn get-user-by
  [key-name value db]
  (first (get-users-by key-name value db)))


(defn user-exists?
  [user db]
  (not (empty? (get-users-by :email-address (:email-address user) db))))


(defn create-user!
  [user db]
  (when (user-exists? user db) (throw+ {:type :user-already-exists}))
  (let [user (merge user {:user-id (uuid/v1)})]
    (swap! db update :users conj user)
    user))
