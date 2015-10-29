(ns the-playground.handlers.api-handlers
  (:require [the-playground.middleware :as m]
            [the-playground.schema :as s]
            [metrics.meters :refer [mark!]]
            [schema.core :as sc]))

(defn make-users-handler
  []
  (fn [req]
    {:status 200
     :body (sc/validate s/UsersResponse
                        {:users [{:id 123, :name "Bob"}
                                 {:id 321, :name "Jane"}]})}))

(def users-docs
  {:summary "Gets a list of users"
   :description "Lists all the users"
   :tags ["Users"]
   :responses {200 {:schema s/UsersResponse
                    :description "The list of users"}}})

(defn make-create-user-handler
  [metrics]
  (-> (fn [{:keys [body] :as req}]
        (mark! (:user-created metrics))
        {:status 201
         :body (sc/validate s/CreateUserResponse
                            {:user {:id 456 :name (:name body)}})})

      (m/wrap-validate-request s/CreateUserRequest)))

(def create-user-docs
  {:summary "Creates a user"
   :description "Creates a user"
   :tags ["Users"]
   :parameters {:body s/CreateUserRequest}
   :responses {201 {:schema s/CreateUserResponse
                    :description "The created user"}}} )

(defn make-articles-handler
  []
  (fn [req]
    {:status 200
     :body (sc/validate s/ArticlesResponse
                        {:articles [{:id 176, :title "Things I like", :text "I like cheese and bread."}
                                    {:id 346, :title "Superconductivity", :text "It's really hard to understand."}]})}))

(def articles-docs
  {:summary "Gets a list of articles"
   :description "Lists all the articles"
   :tags ["Articles"]
   :responses {200 {:schema s/ArticlesResponse
                    :description "The list of articles"}}})

