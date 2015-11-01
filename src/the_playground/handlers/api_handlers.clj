(ns the-playground.handlers.api-handlers
  (:require [the-playground.middleware :as m]
            [the-playground.schema :as s]
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
        {:status 201
         :body (let [new-user {:id (+ 100 (rand-int 100)) :name (:name body)}]
                 (swap! db update :users conj new-user)
                 {:user new-user})})

      (m/wrap-validate {:request-schema s/CreateUserRequest
                        :response-schemata {201 s/CreateUserResponse}})

      (m/wrap-docs {:summary "Creates a user"
                    :description "Creates a user"
                    :tags ["Users"]
                    :parameters {:body s/CreateUserRequest}
                    :responses {201 {:schema s/CreateUserResponse
                                     :description "The created user"}}})))

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

