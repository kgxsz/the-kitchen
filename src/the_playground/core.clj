(ns the-playground.core
  (:gen-class)
  (:require [the-playground.handlers.api-handlers :as api]
            [the-playground.handlers.aux-handlers :as aux]
            [the-playground.middleware :as m]
            [the-playground.util :as u]
            [bidi.ring :as br]
            [cats.core :as c]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [nomad :as n]
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.json :refer [wrap-json-body]]
            [yoyo :as y]
            [yoyo.core :as yc]
            [metrics.core :refer [new-registry]]
            [metrics.meters :refer [meter]]
            [metrics.timers :refer [timer]]
            [yoyo.system :as ys]))

(defn make-route-mapping
  []
  ["/" {"api" {"/users" {:get :users
                         :post :create-user}
               "/articles" {:get :articles}}
        "api-docs" {:get :api-docs}
        "metrics" {:get :metrics}
        true :not-found}])

(defn make-api-handler-mapping
  []
  {:users (api/make-users-handler)
   :create-user (api/make-create-user-handler)
   :articles (api/make-articles-handler)})

(defn make-aux-handler-mapping
  []
  (c/mlet [metrics (ys/ask :metrics)]
    (ys/->dep
     {:api-docs (aux/make-api-docs-handler (make-api-handler-mapping) (make-route-mapping))
      :metrics (aux/make-metrics-handler metrics)
      :not-found (aux/make-not-found-handler)})))

(defn make-handler
  []
  (c/mlet [metrics (ys/ask :metrics)
           aux-handler-mapping (make-aux-handler-mapping)]
    (ys/->dep
     (let [handler-mapping (merge (make-api-handler-mapping)
                                  aux-handler-mapping)]
        (-> (br/make-handler (make-route-mapping) handler-mapping)
            (wrap-json-body {:keywords? true})
            (m/wrap-json-response)
            (wrap-cors :access-control-allow-origin [#"http://petstore.swagger.io"]
                       :access-control-allow-methods [:get :put :post :delete])
            (m/wrap-exception-catching)
            (m/wrap-logging)
            (m/wrap-instrument metrics (make-route-mapping)))))))

(defn make-Δ-config
  []
  (yc/->component
   (n/read-config
    (io/resource "config.edn"))))

(defn make-Δ-metrics
  []
  (let [registry (new-registry)]
    (yc/->component
     {:users {:request-processing-time (timer registry "users-request-processing-time")
              :request-rate (meter registry "users-request-rate")}
      :create-user {:request-processing-time (timer registry "create-user-request-processing-time")
                    :request-rate (meter registry "create-user-request-rate")}
      :articles {:request-processing-time (timer registry "articles-request-processing-time")
                 :request-rate (meter registry "articles-request-rate")}})))

(defn make-Δ-db
  []
  (let [db (atom {})]
    (log/info "Starting db")
    (yc/->component
     db
     (fn [] (log/info "Stopping db:" @db)))))

(defn make-Δ-http-server
  []
  (c/mlet [http-server-port (ys/ask :config :http-server-port)
           handler (make-handler)]
    (ys/->dep
     (let [stop-fn! (run-server handler {:port http-server-port :join? false})]
        (log/info "Starting HTTP server on port" http-server-port)
        (yc/->component
          nil
          (fn []
            (log/info "Stopping HTTP server")
            (stop-fn! :timeout 500)))))))

(defn make-Δ-system
  []
  (ys/make-system #{(ys/named make-Δ-config :config)
                    (ys/named make-Δ-metrics :metrics)
                    (ys/named make-Δ-db :db)
                    (ys/named make-Δ-http-server :http-server)}))

(defn -main
  []
  (log/info "Starting system")
  (y/set-system-fn! #'make-Δ-system)
  (y/start!))
