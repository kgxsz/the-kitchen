(ns the-playground.handlers.aux-handlers
  (:require [bidi.bidi :as b]
            [metrics.meters :refer [rates]]
            [metrics.timers :refer [percentiles]]
            [metrics.counters :as c]
            [metrics.gauges :as g]
            [ring.swagger.swagger2 :as rs]
            [schema.core :as sc]))

(defn make-api-docs-handler
  [api-handler-mapping route-mapping]
  (fn [_]
    {:status 200
     :body (sc/with-fn-validation
             (rs/swagger-json
               {:info {:version "1.0.0"
                       :title "The Playground"
                       :description "A place to explore"}
                :tags [{:name "User"
                        :description "User stuff"}]
                :paths (apply
                        merge-with
                        merge
                        (for [[handler-key _] api-handler-mapping
                              request-method [:get :post :put :delete :head :options]
                              :let [path (b/path-for route-mapping handler-key)]
                              :when (= handler-key (:handler (b/match-route route-mapping path :request-method request-method)))]
                          {path {request-method (:docs (meta (handler-key api-handler-mapping)))}}))}))}))

(defn make-not-found-handler
  []
  (fn [_] {:status 404}))

(defn make-metrics-handler
  [api-handler-mapping metrics]
  (fn [_]
    {:status 200
     :body {:db-gauges {:number-of-users (g/value (get-in metrics [:db-gauges :number-of-users]))
                        :number-of-articles (g/value (get-in metrics [:db-gauges :number-of-articles]))}
            :api-handlers (into {}
                            (for [handler-key (keys api-handler-mapping)]
                              [handler-key {:request-processing-time (percentiles (get-in metrics [:api-handlers handler-key :request-processing-time]))
                                            :request-rate (rates (get-in metrics [:api-handlers handler-key :request-rate]))
                                            :2xx-response-rate (rates (get-in metrics [:api-handlers handler-key :2xx-response-rate]))
                                            :4xx-response-rate (rates (get-in metrics [:api-handlers handler-key :4xx-response-rate]))
                                            :5xx-response-rate (rates (get-in metrics [:api-handlers handler-key :5xx-response-rate]))
                                            :open-requests (c/value (get-in metrics [:api-handlers handler-key :open-requests]))}]))}}))
