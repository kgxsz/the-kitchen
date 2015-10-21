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

(defn make-api-docs-handler
  [api-handlers routes]
  (fn [{:keys [uri]}]
    (log/debug "Request to" uri)
    (log/debug (b/match-route routes "/api/users" :request-method :get))
    (log/debug (b/path-for routes :users))
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (generate-string
            (s/with-fn-validation
              (rs/swagger-json
               {:info {:version "1.0.0"
                       :title "The Playground"
                       :description "A place to explore"}
                :tags [{:name "User"
                        :description "User stuff"}]
                :paths {(b/path-for routes :users) {:get (:docs (meta (:users api-handlers)))}
                        (b/path-for routes :articles) {:get (:docs (meta (:articles api-handlers)))}}})))}))

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
    ["/" {"api" {"/users" {:get :users}
                 "/articles" {:get :articles}}
          "swagger-ui" {:get {true :swagger-ui}}
          "api-docs" {:get :api-docs}
          true :not-found}])))

(defn make-Δ-api-handlers
  []
  (ys/->dep
   (yc/->component
    {:users (make-users-handler)
     :articles (make-articles-handler)})))

(defn make-Δ-aux-handlers
  []
  (c/mlet [routes (ys/ask :routes)
           api-handlers (ys/ask :api-handlers)]
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
