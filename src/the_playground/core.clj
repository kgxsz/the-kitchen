(ns the-playground.core
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.server :as http-server]
            [bidi.ring :refer (make-handler)]
            [clojure.tools.nrepl.server :as nrepl-server]
            [cider.nrepl :refer (cider-nrepl-handler)]
            [clojure.tools.namespace.repl :refer (refresh)]
            [taoensso.timbre :refer [info]]))


(defn api-handler [req]
  (info "Request to /api")
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    "Welcome to the API!"})

(defn not-found-handler [req]
  (info "Request to unknown route")
  {:status  404
   :headers {"Content-Type" "text/html"}
   :body    "Not found."})

(def routes
 ["/" [["api" :api]
       [true  :not-found]]])

(def handler-fns
  {:api       api-handler
   :not-found not-found-handler})

(def system (atom nil))

(defrecord HTTPServer [port stop-fn]
  component/Lifecycle

  (start [component]
    (if stop-fn
      (do
        (info "HTTP server is already running on" port)
        component)
      (let [handler (make-handler routes handler-fns)
            opts {:port port :join? false}]
        (info "Starting HTTP server on port" port)
        (assoc component :stop-fn (http-server/run-server handler opts)))))

  (stop [component]
    (if stop-fn
      (do
        (info "Stopping HTTP server")
        (stop-fn :timeout 500)
        (assoc component :stop-fn nil))
      (do
        (info "HTTP server is not running")
        component))))


(defn make-http-server
  [port]
  (map->HTTPServer {:port port}))

(defn make-system
  []
  (let [http-port 8080]
    (component/system-map
     :http-server (make-http-server http-port))))

(defn start-system
  []
  (reset! system (make-system))
  (swap! system component/start))

(defn stop-system
  []
  (swap! system component/stop))

(defn restart-system
  []
  (stop-system)
  (refresh :after 'the-playground.core/start-system))

(defn -main
  []
  (info "Starting nREPL server on port 8088")
  (nrepl-server/start-server :port 8088 :handler cider-nrepl-handler)
  (info "Starting system")
  (start-system))
