(ns the-playground.core
  (:require [yoyo :as y]
            [yoyo.core :as yc]
            [yoyo.system :as ys]
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

(defn not-found-handler
  [req]
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

(defn start-http-server!
  [{:keys [handler opts] :as http-server}]
  (let [stop-fn! (http-server/run-server handler opts)]
    (info "Starting HTTP server on port" (:port opts))
    (yc/->component http-server
                    (fn []
                      (info "Stopping HTTP server")
                      (stop-fn! :timeout 500)))))

(defn make-Δ-http-server
  []
  (ys/named
    (fn [] (ys/->dep (start-http-server! {:handler (make-handler routes handler-fns)
                                         :opts    {:port 8080 :join? false}})))
    :http-server))

(defn make-system []
  (ys/make-system #{(make-Δ-http-server)}))

(defn -main []
  (info "Starting nREPL server on port 8088")
  (nrepl-server/start-server :port 8088 :handler cider-nrepl-handler)
  (y/set-system-fn! #'make-system)
  (info "Starting system")
  (y/start!))
