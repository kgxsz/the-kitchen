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
            [metrics.counters :refer [counter]]
            [metrics.gauges :refer [gauge-fn]]
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
  (c/mlet [db (ys/ask :db)]
    (ys/->dep
     {:users (api/make-users-handler db)
      :create-user (api/make-create-user-handler db)
      :articles (api/make-articles-handler db)})))

(defn make-aux-handler-mapping
  []
  (c/mlet [metrics (ys/ask :metrics)
           api-handler-mapping (make-api-handler-mapping)]
    (ys/->dep
     {:api-docs (aux/make-api-docs-handler api-handler-mapping (make-route-mapping))
      :metrics (aux/make-metrics-handler api-handler-mapping metrics)
      :not-found (aux/make-not-found-handler)})))

(defn make-handler
  []
  (c/mlet [metrics (ys/ask :metrics)
           api-handler-mapping (make-api-handler-mapping)
           aux-handler-mapping (make-aux-handler-mapping)]
    (ys/->dep
     (let [handler-mapping (merge api-handler-mapping
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
  (c/mlet [db (ys/ask :db)
           api-handler-mapping (make-api-handler-mapping)]
    (ys/->dep
     (let [registry (new-registry)]
       (yc/->component
        {:db-gauges {:number-of-users (gauge-fn "number-of-users" #(count (:users @db)))
                     :number-of-articles (gauge-fn "number-of-articles" #(count (:articles @db)))}
         :api-handlers (into {}
                         (for [handler-key (keys api-handler-mapping)
                               :let [handler-name (name handler-key)]]
                           [handler-key {:request-processing-time (timer registry (str handler-name "-request-processing-time"))
                                         :request-rate (meter registry (str handler-name "-request-rate"))
                                         :2xx-response-rate (meter registry (str handler-name "-2xx-response-rate"))
                                         :4xx-response-rate (meter registry (str handler-name "-4xx-response-rate"))
                                         :5xx-response-rate (meter registry (str handler-name "-5xx-response-rate"))
                                         :open-requests (counter registry (str handler-name "-open-requests"))}]))})))))

(defn make-Δ-db
  []
  (let [db (atom {:users [{:id 154 :name "Jane"}
                          {:id 137 :name "Henry"}]
                  :articles [{:id 176 :title "Things I like" :text "I like cheese and bread."}
                             {:id 146 :title "Superconductivity" :text "It's really hard to understand."}]})]
    (log/info "Initialising db")
    (yc/->component db (fn [] (log/info "Terminating db state with:" @db)))))

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
