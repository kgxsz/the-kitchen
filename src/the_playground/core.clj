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
            [metrics.core :refer [new-registry]]
            [metrics.counters :refer [counter]]
            [metrics.gauges :refer [gauge-fn]]
            [metrics.meters :refer [meter]]
            [metrics.timers :refer [timer]]
            [nomad :as n]
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.json :refer [wrap-json-body]]
            [yoyo :as y]
            [yoyo.core :as yc]
            [yoyo.system :as ys]))


(def route-mapping
  ["/" {"api" {"/users" {"" {:get :users
                             :post :create-user}
                         ["/" :user-id] {:get :user}}}
        "api-docs" {:get :api-docs}
        "metrics" {:get :metrics}
        true :not-found}])


(def group-mapping
  {:users #{:api :users-collection}
   :create-user #{:api :users-collection}
   :user #{:api :users-collection}
   :api-docs #{:aux}
   :metrics #{:aux}
   :not-found #{:aux}})


(def doc-mapping
  {:users (api/make-users-doc route-mapping)
   :create-user (api/make-create-user-doc route-mapping)
   :user (api/make-user-doc route-mapping)})


(defn make-handler-mapping
  []
  (c/mlet [db (ys/ask :db)
           metrics (ys/ask :metrics)]
    (ys/->dep
      {:users (api/make-users-handler route-mapping db)
       :create-user (api/make-create-user-handler route-mapping db)
       :user (api/make-user-handler route-mapping db)
       :api-docs (aux/make-api-docs-handler route-mapping group-mapping doc-mapping)
       :metrics (aux/make-metrics-handler route-mapping group-mapping metrics)
       :not-found (aux/make-not-found-handler)})))


(defn make-handler
  "Makes the top level handler with middleware wrpped around each handler.
   The left side of the clause denotes the groups that the handler
   has to belong to in order for the middleware to wrap it."
  []
  (c/mlet [handler-mapping (make-handler-mapping)
           metrics (ys/ask :metrics)]
    (ys/->dep
     (br/make-handler
       route-mapping
       (into {}
         (for [handler-key (u/list-handler-keys route-mapping)]
           [handler-key (u/when-group-> (handler-key handler-mapping) (handler-key group-mapping)
                          #{:users-collection} (api/wrap-users-collection route-mapping)
                          #{:api} (m/wrap-validate (handler-key doc-mapping))
                          :all (wrap-json-body {:keywords? true})
                          #{:api} (m/wrap-collection-json-response)
                          #{:aux} (m/wrap-generic-json-response)
                          :all (m/wrap-exception-catching)
                          :all (m/wrap-logging)
                          #{:api} (m/wrap-instrument-response-rates metrics)
                          :all (m/wrap-instrument-request-rates metrics)
                          #{:api} (m/wrap-instrument-open-requests metrics)
                          :all (m/wrap-instrument-timer metrics)
                          :all (m/wrap-handler-key route-mapping)
                          :all (wrap-cors :access-control-allow-origin [#"http://petstore.swagger.io"]
                                          :access-control-allow-methods [:get :put :post :delete]))]))))))


(defn make-Δ-config
  []
  (let [config (n/read-config (io/resource "config.edn"))]
    (log/info "Reading config")
    (yc/->component config)))


(defn make-Δ-metrics
  []
  (c/mlet [db (ys/ask :db)]
    (ys/->dep
     (let [registry (new-registry)]
       (log/info "Initialising metrics")
       (yc/->component
        {:db-gauges {:number-of-users (gauge-fn "number-of-users" #(count (:users @db)))
                     :number-of-articles (gauge-fn "number-of-articles" #(count (:articles @db)))}
         :handlers (into {}
                     (for [handler-key (u/list-handler-keys route-mapping)
                           :let [handler-name (name handler-key)]]
                       [handler-key (u/when-group-> {} (handler-key group-mapping)
                                      :all (assoc :request-processing-time (timer registry (str handler-name "-request-processing-time")))
                                      :all (assoc :request-rate (meter registry (str handler-name "-request-rate")))
                                      #{:api} (assoc :2xx-response-rate (meter registry (str handler-name "-2xx-response-rate")))
                                      #{:api} (assoc :3xx-response-rate (meter registry (str handler-name "-3xx-response-rate")))
                                      #{:api} (assoc :4xx-response-rate (meter registry (str handler-name "-4xx-response-rate")))
                                      #{:api} (assoc :5xx-response-rate (meter registry (str handler-name "-5xx-response-rate")))
                                      #{:api} (assoc :open-requests (counter registry (str handler-name "-open-requests"))))]))})))))


(defn make-Δ-db
  []
  (let [db (atom {:users [[{:name "user-id" :value "123"}
                           {:name "name" :value "Jenny"}]
                          [{:name "user-id" :value "456"}
                           {:name "name" :value "John"}]
                          [{:name "user-id" :value "789"}
                           {:name "name" :value "Rachel"}]]})]
    (log/info "Initialising db")
    (yc/->component db)))


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


(defn make-system
  []
  (log/info "Starting system")
  (ys/make-system #{(ys/named make-Δ-config :config)
                    (ys/named make-Δ-metrics :metrics)
                    (ys/named make-Δ-db :db)
                    (ys/named make-Δ-http-server :http-server)}))


(defn -main
  []
  (y/set-system-fn! #'make-system)
  (y/start!))
