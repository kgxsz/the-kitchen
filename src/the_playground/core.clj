(ns the-playground.core
  (:gen-class)
  (:require [yoyo :as y]
            [nomad :as n]
            [yoyo.core :as yc]
            [yoyo.system :as ys]
            [org.httpkit.server :refer [run-server]]
            [bidi.ring :refer [make-handler]]
            [cats.core :as c]
            [clojure.java.io :as io]
            [taoensso.timbre :refer [info]]))

(defn make-api-handler
  [config]
  (fn [req]
    (info "Request to /api")
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (str "Welcome to the API!")}))

(defn make-not-found-handler
  []
  (fn [req]
    (info "Request to unknown route")
    {:status 404
     :headers {"Content-Type" "text/html"}
     :body "Not found."}))

(defn make-routes
  []
  ["/" [["api" :api]
        [true :not-found]]])

(defn make-Δ-config
  []
  (ys/->dep
   (yc/->component
    (nomad/read-config
     (io/file "config.edn")))))

(defn make-Δ-routes
  []
  (ys/->dep
   (yc/->component
     (make-routes))))

(defn make-Δ-handler-fns
  []
  (c/mlet [config (ys/ask :config)]
    (ys/->dep
      (yc/->component
        {:api (make-api-handler config)
         :not-found (make-not-found-handler)}))))

(defn make-Δ-http-server
  []
  (c/mlet [options (ys/ask :config :http-server-options)
           routes (ys/ask :routes)
           handler-fns (ys/ask :handler-fns)]
    (ys/->dep
      (let [stop-fn! (run-server (make-handler routes handler-fns) options)]
        (info "Starting HTTP server on port" (:port options))
        (yc/->component
          options
          (fn []
            (info "Stopping HTTP server")
            (stop-fn! :timeout (:timeout options))))))))

(defn make-system []
  (ys/make-system #{(ys/named make-Δ-config :config)
                    (ys/named make-Δ-routes :routes)
                    (ys/named make-Δ-handler-fns :handler-fns)
                    (ys/named make-Δ-http-server :http-server)}))

(defn -main []
  (y/set-system-fn! #'make-system)
  (info "Starting system")
  (y/start!))
