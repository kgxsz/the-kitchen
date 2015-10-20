(ns the-playground.core
  (:gen-class)
  (:require [bidi.ring :refer [make-handler]]
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

(s/defschema User {:id s/Str
                   :name s/Str
                   :address {:street s/Str
                             :city (s/enum :tre :hki)}})

(defn with-docs
  [handler docs]
  (vary-meta handler assoc :docs docs))

(defn make-api-handler
  [config]
  (-> (fn [{:keys [uri]}]
        (log/debug "Request to" uri)
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body (str "Welcome to the API! The password is: " (:password config))})

      (with-docs {:summary "API root"
                  :description "The playground API root"
                  :tags ["hello world"]
                  :responses {200 {:schema User
                                   :description "The user you're looking for"}
                              404 {:description "Not found brah!"}}})))

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
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (generate-string
            (s/with-fn-validation
              (rs/swagger-json
               {:info {:version "1.0.0"
                       :title "The Playground"
                       :description "A place to explore"}
                :tags [{:name "user"
                        :description "User stuff"}]
                :paths {"/api" {:get {}}}})))}))

(defn make-not-found-handler
  []
  (fn [{:keys [uri]}]
    (log/debug "Request to" uri
               {:status 404})))

(defn make-Δ-routes
  []
  (ys/->dep
   (yc/->component
    ["/" {"api" :api
          "swagger-ui" {true :swagger-ui}
          "api-docs" :api-docs
          true :not-found}])))

(defn make-Δ-api-handlers
  []
  (c/mlet [config (ys/ask :config)]
    (ys/->dep
     (yc/->component
      {:api (make-api-handler config)}))))

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
