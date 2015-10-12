(ns the-playground.core
  (:gen-class)
  (:require #_[taoensso.timbre :refer [info]]
            [bidi.ring :refer [make-handler]]
            #_[cats.core :as c]
            #_[clojure.java.io :as io]
            #_[nomad :as n]
            [org.httpkit.server :refer [run-server]]
            #_[yoyo.core :as yc]
            #_[yoyo :as y]
            #_[yoyo.system :as ys]))

(defn make-api-handler
  []
  (fn [req]
    #_(info "Request to /api")
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (str "Welcome to the API!")}))

(defn make-not-found-handler
  []
  (fn [req]
    #_(info "Request to unknown route")
    {:status 404
     :headers {"Content-Type" "text/html"}
     :body "Not found."}))

(defn make-routes
  []
  ["/" [["api" :api]
        [true :not-found]]])

#_(defn make-Δ-config
  []
  (ys/->dep
   (yc/->component
    (nomad/read-config
     (io/file "config.edn")))))

#_(defn make-Δ-routes
  []
  (ys/->dep
   (yc/->component
     (make-routes))))

#_(defn make-Δ-handler-fns
  []
  (c/mlet [config (ys/ask :config)]
    (ys/->dep
      (yc/->component
        {:api (make-api-handler config)
         :not-found (make-not-found-handler)}))))

#_(defn make-Δ-http-server
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

#_(defn make-system
  []
  (ys/make-system #{(ys/named make-Δ-config :config)
                    (ys/named make-Δ-routes :routes)
                    (ys/named make-Δ-handler-fns :handler-fns)
                    (ys/named make-Δ-http-server :http-server)}))

#_(defn -main
  []
  (y/set-system-fn! #'make-system)
  (info "Starting system")
  (y/start!))

(defn -main
  []
  (let [stop-server (run-server (make-handler
                                 (make-routes)
                                 {:api (make-api-handler)
                                  :not-found (make-not-found-handler)})
                                {:port 8080 :join? false})]))
