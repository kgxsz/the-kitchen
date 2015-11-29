(ns the-playground.db
  (:require [the-playground.util :as u]
            [clj-uuid :as uuid]
            [slingshot.slingshot :refer [throw+]]))


(defn get-user-by-user-id
  [user-id db]
  (if-let [user (some
                 (fn [user] (when (= user-id (:user-id user)) user))
                 (:users @db))]
    user
    (throw+ {:type :user-not-found})))


(defn user-exists?
  [name db]
  (some #(= name (:name %)) (:users @db)))


(defn create-user!
  [user db]
  (when (user-exists? (:name user) db) (throw+ {:type :user-already-exists}))
  (let [user (merge user {:user-id (uuid/v1)})]
    (swap! db update :users conj user)
    user))
