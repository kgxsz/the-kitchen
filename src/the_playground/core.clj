(ns the-playground.core
  (:gen-class)
  (:require [the-playground.middleware :as m]
            [the-playground.schema :as s]
            [bidi.bidi :as b]
            [bidi.ring :refer [make-handler]]
            [cats.core :as c]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [nomad :as n]
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.swagger.swagger2 :as rs]
            [schema.core :as sc]
            [yoyo :as y]
            [yoyo.core :as yc]
            [yoyo.system :as ys]))

(defn make-users-handler
  []
  (-> (fn [req]
        {:status 200
         :body (sc/validate s/UsersResponse
                 {:users [{:id 123, :name "Bob"}
                          {:id 321, :name "Jane"}]})})

      (m/wrap-docs {:summary "Gets a list of users"
                  :description "Lists all the users"
                  :tags ["Users"]
                  :responses {200 {:schema s/UsersResponse
                                   :description "The list of users"}}})))

(defn make-create-user-handler
  []
  (-> (fn [req]
        {:status 201
         :body (sc/validate s/CreateUserResponse
                 {:user {:id 456 :name "Alice"}})})

      (m/wrap-docs {:summary "Creates a user"
                  :description "Creates a user"
                  :tags ["Users"]
                  :responses {201 {:schema s/CreateUserResponse
                                   :description "The created user"}}})))

(defn make-articles-handler
  []
  (-> (fn [req]
        {:status 200
         :body (sc/validate s/ArticlesResponse
                 {:articles [{:id 176, :title "Things I like", :text "I like cheese and bread."}
                             {:id 346, :title "Superconductivity", :text "It's really hard to understand."}]})})

      (m/wrap-docs {:summary "Gets a list of articles"
                  :description "Lists all the articles"
                  :tags ["Articles"]
                  :responses {200 {:schema s/ArticlesResponse
                                   :description "The list of articles"}}})))

(defn make-api-docs-handler
  [api-handler-mapping route-mapping]
  (fn [req]
    {:status 200
     :body (sc/with-fn-validation
             (rs/swagger-json
               {:info {:version "1.0.0"
                       :title "The Playground"
                       :description "A place to explore"}
                :tags [{:name "User"
                        :description "User stuff"}]
                :paths (apply
                        merge-with
                        merge
                        (for [[handler-key _] api-handler-mapping
                              request-method [:get :post :put :delete :head :options]
                              :let [path (b/path-for route-mapping handler-key)]
                              :when (= handler-key (:handler (b/match-route route-mapping path :request-method request-method)))]
                          {path {request-method (:docs (meta (handler-key api-handler-mapping)))}}))}))}))

(defn make-not-found-handler
  []
  (fn [req] {:status 404}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn make-Δ-route-mapping
  []
  (ys/->dep
   (yc/->component
    ["/" {"api" {"/users" {:get :users
                           :post :create-user}
                 "/articles" {:get :articles}}
          "api-docs" {:get :api-docs}
          true :not-found}])))

(defn make-Δ-api-handler-mapping
  []
  (ys/->dep
   (yc/->component
    {:users (make-users-handler)
     :create-user (make-create-user-handler)
     :articles (make-articles-handler)})))

(defn make-Δ-aux-handler-mapping
  []
  (c/mlet [api-handler-mapping (ys/ask :api-handler-mapping)
           route-mapping (ys/ask :route-mapping)]
    (ys/->dep
     (yc/->component
      {:api-docs (make-api-docs-handler api-handler-mapping route-mapping)
       :not-found (make-not-found-handler)}))))

(defn make-Δ-handler
  []
  (c/mlet [route-mapping (ys/ask :route-mapping)
           api-handler-mapping (ys/ask :api-handler-mapping)
           aux-handler-mapping (ys/ask :aux-handler-mapping)]
    (ys/->dep
     (yc/->component
      (-> (make-handler route-mapping (merge api-handler-mapping
                                             aux-handler-mapping))
          (m/wrap-json-response)
          (wrap-cors :access-control-allow-origin [#"http://petstore.swagger.io"]
                     :access-control-allow-methods [:get :put :post :delete])
          (m/wrap-exception-catching)
          (m/wrap-logging))))))

(defn make-Δ-config
  []
  (ys/->dep
   (yc/->component
    (n/read-config
     (io/resource "config.edn")))))

(defn make-Δ-http-server
  []
  (c/mlet [http-server-port (ys/ask :config :http-server-port)
           handler (ys/ask :handler)]
    (ys/->dep
     (let [stop-fn! (run-server handler {:port http-server-port :join? false})]
        (log/info "Starting HTTP server on port" http-server-port)
        (yc/->component
          nil
          (fn []
            (log/info "Stopping HTTP server")
            (stop-fn! :timeout 500)))))))

(defn make-system
  []
  (ys/make-system #{(ys/named make-Δ-config :config)
                    (ys/named make-Δ-route-mapping :route-mapping)
                    (ys/named make-Δ-api-handler-mapping :api-handler-mapping)
                    (ys/named make-Δ-aux-handler-mapping :aux-handler-mapping)
                    (ys/named make-Δ-handler :handler)
                    (ys/named make-Δ-http-server :http-server)}))

(defn -main
  []
  (log/info "Starting system")
  (y/set-system-fn! #'make-system)
  (y/start!))
