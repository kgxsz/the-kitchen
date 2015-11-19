(ns the-playground.handlers.api-handlers
  (:require [the-playground.middleware :as m]
            [the-playground.schema :as s]
            [clojure.string :refer [lower-case]]
            [slingshot.slingshot :refer [try+ throw+]]
            [schema.core :as sc]))


(defn make-users-handler
  [db]
  (fn [req]
    {:status 200
     :body {:users (:users @db)}}))

(def users-doc
  {:summary "Gets a list of users"
   :description "Lists all the users"
   :tags ["User"]
   :responses {200 {:schema s/UsersResponse
                    :description "The list of users"}}})


(defn make-create-user-handler
  [db]
  (fn [{:keys [body request-method uri] :as req}]
    (try+
     (let [new-user {:id (+ 100 (rand-int 100)) :name (:name body)}]

       (when (some #(= (lower-case (:name %)) (lower-case (:name new-user))) (:users @db))
         (throw+ {:type :user-already-exists}))

       (swap! db update :users conj new-user)

       {:status 201
        :body {:user new-user}})

     (catch [:type :user-already-exists] e
       {:status 409
        :body {:error "User already exists"}}))))

(def create-user-doc
  {:summary "Creates a user"
   :description "Creates a user"
   :tags ["User"]
   :parameters {:body s/CreateUserRequest}
   :responses {201 {:schema s/CreateUserResponse
                    :description "The created user"}
               409 {:schema s/ErrorResponse
                    :description "The error response"}}})


(defn make-articles-handler
  [db]
  (fn [req]
    {:status 200
     :body {:articles (:articles @db)}}))

(def articles-doc
  {:summary "Gets a list of articles"
   :description "Lists all the articles"
   :tags ["Article"]
   :responses {200 {:schema s/ArticlesResponse
                    :description "The list of articles"}}})
