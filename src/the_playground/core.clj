(ns the-playground.core
  (:gen-class)
  (:require [bidi.ring :refer [make-handler]]
            [cats.core :as c]
            [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [nomad :as n]
            [org.httpkit.server :refer [run-server]]
            [yoyo.core :as yc]
            [yoyo :as y]
            [yoyo.system :as ys]))

(defn make-api-handler
  [config]
  (fn [req]
    (log/info "Request to /api")
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (str "Welcome to the API! The password is: " (:password config))}))

(defn make-not-found-handler
  []
  (fn [{:keys [uri]}]
    (log/info "Request to non-existent route:" uri)
    {:status 404
     :headers {"Content-Type" "text/html"}
     :body "Not found."}))

(defn make-routes
  []
  ["/" [["api" :api]
        [true :not-found]]])

(defn make-Δ-handler
  []
  (c/mlet [config (ys/ask :config)]
    (ys/->dep
      (yc/->component
        (make-handler
          (make-routes)
          {:api (make-api-handler config)
           :not-found (make-not-found-handler)})))))

(defn make-Δ-config
  []
  (ys/->dep
   (yc/->component
    (nomad/read-config
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
                    (ys/named make-Δ-handler :handler)
                    (ys/named make-Δ-http-server :http-server)}))

(defn -main
  []
  (log/info "Starting system")
  (y/set-system-fn! #'make-system)
  (y/start!))
