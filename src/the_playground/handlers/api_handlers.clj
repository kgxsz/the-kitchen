(ns the-playground.handlers.api-handlers
  (:require [the-playground.middleware :as m]
            [the-playground.schema :as s]
            [clojure.string :refer [lower-case]]
            [slingshot.slingshot :refer [try+ throw+]]
            [schema.core :as sc]))

(defn make-users-handler
  [db]
  (-> (fn [req]
        {:status 200
         :body {:users (:users @db)}})

      (m/wrap-validate {:response-schemata {200 s/UsersResponse}})

      (m/wrap-docs {:summary "Gets a list of users"
                    :description "Lists all the users"
                    :tags ["Users"]
                    :responses {200 {:schema s/UsersResponse
                                     :description "The list of users"}}})))

(defn make-create-user-handler
  [db]
  (-> (fn [{:keys [body request-method uri] :as req}]
        (try+
          (let [new-user {:id (+ 100 (rand-int 100)) :name (:name body)}]

            (when (some #(= (lower-case (:name %)) (lower-case (:name new-user))) (:users @db))
              (throw+ {:type :user-already-exists}))

            (swap! db update :users conj new-user)

            {:status 201
             :body {:user new-user}})

          (catch [:type :user-already-exists] e
            {:status 409
             :body {:error "User already exists"}})))

      (m/wrap-validate {:request-schema s/CreateUserRequest
                        :response-schemata {201 s/CreateUserResponse
                                            409 s/ErrorResponse}})

      (m/wrap-docs {:summary "Creates a user"
                    :description "Creates a user"
                    :tags ["Users"]
                    :parameters {:body s/CreateUserRequest}
                    :responses {201 {:schema s/CreateUserResponse
                                     :description "The created user"}
                                409 {:schema s/ErrorResponse
                                     :description "The error response"}}})))

(defn make-articles-handler
  [db]
  (-> (fn [req]
        {:status 200
         :body {:articles (:articles @db)}})

      (m/wrap-validate {:response-schemata {200 s/ArticlesResponse}})

      (m/wrap-docs {:summary "Gets a list of articles"
                    :description "Lists all the articles"
                    :tags ["Articles"]
                    :responses {200 {:schema s/ArticlesResponse
                                     :description "The list of articles"}}})))
