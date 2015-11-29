(ns the-playground.handlers.api-handlers
  (:require [the-playground.db :as db]
            [the-playground.middleware :as m]
            [the-playground.schema :as s]
            [the-playground.util :as u]
            [bidi.bidi :as b]
            [clojure.string :refer [lower-case]]
            [schema.core :as sc]
            [schema.coerce :as scr]
            [slingshot.slingshot :refer [try+]]))


(defn coerce-in
  [data]
  (->> data
       (map (fn [{:keys [name value]}] {(keyword name) value}))
       (apply merge)
       ((fn [m] (update m :user-id scr/string->uuid)))))

(coerce-in [{:name "user-id" :value "66679f70-96cc-11e5-837e-2380a99a9487"}
                  {:name "name" :value "Keigo"}])


(defn coerce-out
  [data]
  (map (fn [[k v]] {:name (name k) :value (str v)}) data))


(defn wrap-users-collection
  "Ensures that the outgoing data is properly formed into a users collection."
  [handler route-mapping]
  (fn [request]
    (let [{:keys [body] :as response} (handler request)]
      (assoc response :body {:collection (merge {:version 1.0
                                                 :href (b/path-for route-mapping :users)
                                                 :items []
                                                 :template {:data [{:prompt "the user's name" :name "name" :value ""}]}}
                                                body)}))))


(defn make-users-handler
  [route-mapping db]

  (fn [{:keys [handler-key] :as request}]
    (let [items (map
                  (fn [user]
                    {:href (b/path-for route-mapping :user :user-id (:user-id user))
                     :data (coerce-out user)})
                  (:users @db))]

      {:status 200
       :body {:items items}})))


(defn make-users-doc
  [route-mapping]
  {:path (b/path-for route-mapping :users)
   :method :get
   :handler-doc {:summary "gets a list of users"
                 :description "gets a list of users"
                 :tags ["user"]
                 :responses {200 {:schema s/Collection
                                  :description "the list of users was retrieved successfully"}}}})


(defn make-create-user-handler
  [route-mapping db]

  (fn [{:keys [body request-method uri] :as request}]
    (try+
     (let [data (-> body :template :data)
           user (db/create-user! (coerce-in data) db)
           item {:href (b/path-for route-mapping :user :user-id (:user-id user))
                 :data (coerce-out user)}]

       {:status 201
        :body {:items [item]}})

     (catch [:type :user-already-exists] _
       {:status 409
        :body {:error {:title "user-already-exists" :code "409" :message "could not create a new user because the user already exists"}}}))))

(defn make-create-user-doc
  [route-mapping]
  {:path (b/path-for route-mapping :create-user)
   :method :post
   :handler-doc {:summary "creates a user"
                 :description "creates a user"
                 :tags ["user"]
                 :parameters {:body s/CreateUserTemplate}
                 :responses {201 {:schema s/Collection
                                  :description "the user was created successfully"}
                             409 {:schema s/Collection
                                  :description "could not create a new user because the user already exists"}}}})


(defn make-user-handler
  [route-mapping db]

  (fn [{:keys [handler-key route-params] :as request}]
    (try+
      (let [user-id (-> route-params :user-id scr/string->uuid)
            user (db/get-user-by-user-id user-id db)
            item {:href (b/path-for route-mapping :user :user-id (:user-id user))
                  :data (coerce-out user)}]

        {:status 200
         :body {:items [item]}})

      (catch [:type :user-not-found] _
        {:status 404
         :body {:error {:title "user-not-found" :code "404" :message "the user does not exist, or no longer exists"}}}))))

(defn make-user-doc
  [route-mapping]
  {:path (b/path-for route-mapping :user :user-id ":user-id")
   :method :get
   :handler-doc {:summary "gets a specific user"
                 :description "gets a specific user"
                 :tags ["user"]
                 :parameters {:path {:user-id sc/Str}}
                 :responses {200 {:schema s/Collection
                                  :description "the user was retrieved successfully"}
                             404 {:schema s/Collection
                                  :description "the user does not exist, or no longer exists"}}}})
