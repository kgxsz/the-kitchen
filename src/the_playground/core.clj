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


(def route-mapping
  ["/" {"api" {"/users" {:get :users
                         :post :create-user}
               "/articles" {:get :articles}}
        "api-docs" {:get :api-docs}
        "metrics" {:get :metrics}
        true :not-found}])


(def group-mapping
  {:users #{:api}
   :create-user #{:api}
   :articles #{:api}
   :api-docs #{:aux}
   :metrics #{:aux}
   :not-found #{:aux}})


(def doc-mapping
  {:users api/users-doc
   :create-user api/create-user-doc
   :articles api/articles-doc})


(defn make-handler-mapping
  []
  (c/mlet [db (ys/ask :db)
           metrics (ys/ask :metrics)]
    (ys/->dep
      {:users (api/make-users-handler db)
       :create-user (api/make-create-user-handler db)
       :articles (api/make-articles-handler db)
       :api-docs (aux/make-api-docs-handler doc-mapping route-mapping)
       :metrics (aux/make-metrics-handler metrics)
       :not-found (aux/make-not-found-handler)})))


(defn make-wrap-middleware
  "Makes a function to wrap middleware around individual handlers.
   Different handlers are wrapped by different middleware depending
   on the group they belong to. The :all keyword means that the
   particular middleware will be applied to all handlers."
  []
  (c/mlet [metrics (ys/ask :metrics)]
    (ys/->dep
     (fn [handler handler-key]
       (u/middleware-> handler (handler-key group-mapping)
            #{:api} (m/wrap-validate (handler-key doc-mapping))
            :all (wrap-json-body {:keywords? true})
            :all (m/wrap-json-response)
            :all (wrap-cors :access-control-allow-origin [#"http://petstore.swagger.io"]
                            :access-control-allow-methods [:get :put :post :delete])
            :all (m/wrap-exception-catching)
            :all (m/wrap-logging)
            #{:api} (m/wrap-instrument-response-rates metrics)
            :all (m/wrap-instrument-request-rates metrics)
            #{:api} (m/wrap-instrument-open-requests metrics)
            :all (m/wrap-instrument-timer metrics)
            :all (m/wrap-handler-key route-mapping))))))


(defn make-handler
  []
  (c/mlet [handler-mapping (make-handler-mapping)
           wrap-middleware (make-wrap-middleware)]
    (ys/->dep
     (br/make-handler
       route-mapping
       (into {}
         (for [[handler-key handler] handler-mapping]
           [handler-key (wrap-middleware handler handler-key)]))))))


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
                     (concat
                       (for [handler-key [:api-docs :metrics :not-found]
                             :let [handler-name (name handler-key)]]
                         [handler-key {:request-processing-time (timer registry (str handler-name "-request-processing-time"))
                                       :request-rate (meter registry (str handler-name "-request-rate"))}])

                       (for [handler-key [:users :create-user :articles]
                             :let [handler-name (name handler-key)]]
                         [handler-key {:request-processing-time (timer registry (str handler-name "-request-processing-time"))
                                       :request-rate (meter registry (str handler-name "-request-rate"))
                                       :2xx-response-rate (meter registry (str handler-name "-2xx-response-rate"))
                                       :3xx-response-rate (meter registry (str handler-name "-3xx-response-rate"))
                                       :4xx-response-rate (meter registry (str handler-name "-4xx-response-rate"))
                                       :5xx-response-rate (meter registry (str handler-name "-5xx-response-rate"))
                                       :open-requests (counter registry (str handler-name "-open-requests"))}])))})))))


(defn make-Δ-db
  []
  (let [db (atom {:users [{:id 154 :name "Jane"}
                          {:id 137 :name "Henry"}]
                  :articles [{:id 176 :title "Things I like" :text "I like cheese and bread."}
                             {:id 146 :title "Superconductivity" :text "It's really hard to understand."}]})]
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
