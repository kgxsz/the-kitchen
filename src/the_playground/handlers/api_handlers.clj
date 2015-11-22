(ns the-playground.handlers.api-handlers
  (:require [the-playground.middleware :as m]
            [the-playground.schema :as s]
            [bidi.bidi :as b]
            [clojure.string :refer [lower-case]]
            [slingshot.slingshot :refer [try+ throw+]]
            [schema.core :as sc]))


(defn make-users-handler
  [route-mapping db]
  (fn [{:keys [handler-key] :as request}]
    {:status 200
     :body {:collection {:version 1.0
                         :href (b/path-for route-mapping :users)
                         :items [{:href "/api/users/123"
                                  :data [{:name "id" :value "123"}
                                         {:name "name" :value "Jenny"}]
                                  :links []}
                                 {:href "/api/users/456"
                                  :data [{:name "id" :value "456"}
                                         {:name "name" :value "John"}]
                                  :links []}
                                 {:href "/api/users/789"
                                  :data [{:name "id" :value "789"}
                                         {:name "name" :value "Rachel"}]
                                  :links []}]
                         :links []
                         :queries []
                         :template {:data [{:prompt "The user's name" :name "name" :value ""}]}}}}))


(defn make-users-doc
  [route-mapping]
  {:path (b/path-for route-mapping :users)
   :method :get
   :handler-doc {:summary "Gets a list of users"
                 :description "Lists all the users"
                 :tags ["User"]
                 :responses {200 {:schema s/Collection
                                  :description "The list of users"}}}})


(defn make-create-user-handler
  [route-mapping db]
  (fn [{:keys [body request-method uri] :as req}]
    (try+
     (let [new-user "keigo"]

       #_(when (some #(= (lower-case (:name %)) (lower-case (:name new-user))) (:users @db))
         (throw+ {:type :user-already-exists}))

       #_(swap! db update :users conj new-user)

       {:status 201
        :body {:collection {:version 1.0
                            :href (b/path-for route-mapping :users)
                            :items [{:href "/api/users/123"
                                     :data [{:name "id" :value "123"}
                                            {:name "name" :value "Jenny"}]
                                     :links []}]}}})

     (catch [:type :user-already-exists] e
       {:status 409
        :body {:error "User already exists"}}))))

(defn make-create-user-doc
  [route-mapping]
  {:path (b/path-for route-mapping :create-user)
   :method :post
   :handler-doc {:summary "Creates a user"
                 :description "Creates a user"
                 :tags ["User"]
                 :parameters {:body s/CreateUserTemplate}
                 :responses {201 {:schema s/Collection
                                  :description "The created user"}
                             409 {:schema s/ErrorResponse
                                  :description "The error response"}}}})


(defn make-user-handler
  [route-mapping db]
  (fn [{:keys [handler-key] :as request}]
    {:status 200
     :body {:collection {:version 1.0
                         :href (b/path-for route-mapping :users)
                         :items [{:href "/api/users/123"
                                  :data [{:name "id" :value "123"}
                                         {:name "name" :value "Jenny"}]
                                  :links []}]}}}))

(defn make-user-doc
  [route-mapping]
  {:path (b/path-for route-mapping :user :user-id ":user-id")
   :method :get
   :handler-doc {:summary "Gets a user"
                 :description "Returns a user"
                 :tags ["User"]
                 :parameters {:path {:user-id sc/Num}}
                 :responses {200 {:schema s/Collection
                                  :description "The list of articles"}}}})
