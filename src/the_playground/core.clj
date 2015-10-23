(ns the-playground.core
  (:gen-class)
  (:require [bidi.ring :refer [make-handler]]
            [bidi.bidi :as b]
            [cats.core :as c]
            [clojure.tools.logging :as log]
            [clojure.string :refer [replace-first]]
            [clojure.java.io :as io]
            [nomad :as n]
            [org.httpkit.server :refer [run-server]]
            [yoyo.core :as yc]
            [yoyo :as y]
            [yoyo.system :as ys]
            [schema.core :as s]
            [ring.util.response :refer (url-response)]
            [cheshire.core :refer :all]
            [ring.swagger.swagger2 :as rs]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(s/defschema User {:id s/Str
                   :name s/Str
                   :address {:street s/Str
                             :city (s/enum :tre :hki)}})

(s/defschema Article {:id s/Str
                      :title s/Str
                      :text s/Str})

(s/defschema UsersResponse [User])

(s/defschema CreateUserResponse User)

(s/defschema ArticlesResponse [Article])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn with-docs
  [handler docs]
  (vary-meta handler assoc :docs docs))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn make-users-handler
  []
  (-> (fn [{:keys [uri]}]
        (log/debug "Request to" uri)
        {:status 200
         :headers {"Content-Type" "application/json"}
         :body (str "A list of users")})

      (with-docs {:summary "Gets a list of users"
                  :description "Lists all the users"
                  :tags ["Users"]
                  :responses {200 {:schema UsersResponse
                                   :description "The list of users"}}})))

(defn make-create-user-handler
  []
  (-> (fn [{:keys [uri]}]
        (log/debug "Request to" uri)
        {:status 200
         :headers {"Content-Type" "application/json"}
         :body (str "Create a user")})

      (with-docs {:summary "Creates a user"
                  :description "Creates a user"
                  :tags ["Users"]
                  :responses {201 {:schema CreateUserResponse
                                   :description "The created user"}}})))

(defn make-articles-handler
  []
  (-> (fn [{:keys [uri]}]
        (log/debug "Request to" uri)
        {:status 200
         :headers {"Content-Type" "application/json"}
         :body (str "A list of articles")})

      (with-docs {:summary "Gets a list of articles"
                  :description "Lists all the articles"
                  :tags ["Articles"]
                  :responses {200 {:schema ArticlesResponse
                                   :description "The list of articles"}}})))

(defn make-swagger-ui-handler
  []
  (fn [{:keys [uri]}]
    (log/debug "Request to" uri)
    (if-let [resource (->> (if (= uri "/swagger-ui") "/index.html" "")
                           (str (replace-first uri #"/" ""))
                           (io/resource))]
      (url-response resource)
      {:status 404})))

(defn generate-api-docs
  [api-handlers routes]
  (s/with-fn-validation
    (rs/swagger-json
     {:info {:version "1.0.0"
             :title "The Playground"
             :description "A place to explore"}
      :tags [{:name "User"
              :description "User stuff"}]
      :paths (apply
              merge-with
              merge
              (for [[handler-key _] api-handlers
                    request-method [:get :post :put :delete :head :options]
                    :let [path (b/path-for routes handler-key)]
                    :when (= handler-key (:handler (b/match-route routes path :request-method request-method)))]
                {path {request-method (:docs (meta (handler-key api-handlers)))}}))})))

(defn make-api-docs-handler
  [api-handlers routes]
  (fn [{:keys [uri]}]
    (log/debug "Request to" uri)
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (-> (generate-api-docs api-handlers routes)
               generate-string)}))

(defn make-not-found-handler
  []
  (fn [{:keys [uri]}]
    (log/debug "Request to" uri)
    {:status 404}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn make-Δ-routes
  []
  (ys/->dep
   (yc/->component
    ["/" {"api" {"/users" {:get :users
                           :post :create-user}
                 "/articles" {:get :articles}}
          "swagger-ui" {:get {true :swagger-ui}}
          "api-docs" {:get :api-docs}
          true :not-found}])))

(defn make-Δ-api-handlers
  []
  (ys/->dep
   (yc/->component
    {:users (make-users-handler)
     :create-user (make-create-user-handler)
     :articles (make-articles-handler)})))

(defn make-Δ-aux-handlers
  []
  (c/mlet [api-handlers (ys/ask :api-handlers)
           routes (ys/ask :routes)]
    (ys/->dep
     (yc/->component
      {:swagger-ui (make-swagger-ui-handler)
       :api-docs (make-api-docs-handler api-handlers routes)
       :not-found (make-not-found-handler)}))))

(defn make-Δ-handler
  []
  (c/mlet [routes (ys/ask :routes)
           api-handlers (ys/ask :api-handlers)
           aux-handlers (ys/ask :aux-handlers)]
    (ys/->dep
     (yc/->component
      (merge api-handlers aux-handlers)))))

(defn make-Δ-config
  []
  (ys/->dep
   (yc/->component
    (nomad/read-config
     (io/resource "config.edn")))))

(defn make-Δ-http-server
  []
  (c/mlet [http-server-port (ys/ask :config :http-server-port)
           routes (ys/ask :routes)
           handler (ys/ask :handler)]
    (ys/->dep
     (let [stop-fn! (run-server (make-handler routes handler)
                                {:port http-server-port :join? false})]
        (log/info "Starting HTTP server on port" http-server-port)
        (yc/->component
          nil
          (fn []
            (log/info "Stopping HTTP server")
            (stop-fn! :timeout 500)))))))

(defn make-system
  []
  (ys/make-system #{(ys/named make-Δ-config :config)
                    (ys/named make-Δ-routes :routes)
                    (ys/named make-Δ-api-handlers :api-handlers)
                    (ys/named make-Δ-aux-handlers :aux-handlers)
                    (ys/named make-Δ-handler :handler)
                    (ys/named make-Δ-http-server :http-server)}))

(defn -main
  []
  (log/info "Starting system")
  (y/set-system-fn! #'make-system)
  (y/start!))
