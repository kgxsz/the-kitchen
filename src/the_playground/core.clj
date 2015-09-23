(ns the-playground.core
  (:require [yoyo :as y]
            [nomad :as n]
            [yoyo.core :as yc]
            [yoyo.system :as ys]
            [org.httpkit.server :refer [run-server]]
            [bidi.ring :refer [make-handler]]
            [cats.core :as c]
            [clojure.java.io :as io]
            [clojure.tools.nrepl.server :as nrepl-server]
            [cider.nrepl :refer (cider-nrepl-handler)]
            [taoensso.timbre :refer [info]]))

(defn api-handler [req]
  (info "Request to /api")
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body "Welcome to the API!"})

(defn not-found-handler
  [req]
  (info "Request to unknown route")
  {:status 404
   :headers {"Content-Type" "text/html"}
   :body "Not found."})

(def routes
  ["/" [["api" :api]
        [true :not-found]]])

(def handler-fns
  {:api api-handler
   :not-found not-found-handler})

(defn make-Δ-config
  []
  (ys/->dep
    (yc/->component
      (nomad/read-config
        (io/file "config.edn")))))

(defn make-Δ-handler
  []
  (ys/->dep
    (yc/->component
      (make-handler routes handler-fns))))

(defn make-Δ-http-server
  []
  (c/mlet [options (ys/ask :config :http-server-options)
           handler (ys/ask :handler)]
    (ys/->dep
      (let [stop-fn! (run-server handler options)]
        (info "Starting HTTP server on port" (:port options))
        (yc/->component
          options
          (fn []
            (info "Stopping HTTP server")
            (stop-fn! :timeout (:timeout options))))))))

(defn make-system []
  (ys/make-system #{(ys/named make-Δ-config :config)
                    (ys/named make-Δ-handler :handler)
                    (ys/named make-Δ-http-server :http-server)}))

(defn -main []
  (info "Starting nREPL server on port 8088")
  (nrepl-server/start-server :port 8088 :handler cider-nrepl-handler)
  (y/set-system-fn! #'make-system)
  (info "Starting system")
  (y/start!))
