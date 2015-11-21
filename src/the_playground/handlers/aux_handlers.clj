(ns the-playground.handlers.aux-handlers
  (:require [the-playground.util :as u]
            [bidi.bidi :as b]
            [metrics.counters :as c]
            [metrics.gauges :as g]
            [metrics.meters :refer [rates]]
            [metrics.timers :refer [percentiles]]
            [ring.swagger.swagger2 :as rs]
            [schema.core :as sc]))


(defn make-api-docs-handler
  [doc-mapping group-mapping route-mapping]
  (fn [_]
    {:status 200
     :body (sc/with-fn-validation
             (rs/swagger-json
               {:info {:version "1.0.0"
                       :title "The Playground"
                       :description "A place to explore"}
                :tags [{:name "User"
                        :description "user related endpoints"}
                       {:name "Article"
                        :description "article related endpoints"}]
                :paths (apply
                         merge-with
                         merge
                         (for [handler-key (u/list-handler-keys route-mapping)
                               request-method [:get :post :put :delete :head :options]
                               :when (contains? (handler-key group-mapping) :api)
                               :let [path (b/path-for route-mapping handler-key)]
                               :when (= handler-key (:handler (b/match-route route-mapping path :request-method request-method)))]
                           {path {request-method (handler-key doc-mapping)}}))}))}))


(defn make-metrics-handler
  [metrics group-mapping route-mapping]
  (fn [_]
    {:status 200
     :body {:db-gauges {:number-of-users (g/value (get-in metrics [:db-gauges :number-of-users]))
                        :number-of-articles (g/value (get-in metrics [:db-gauges :number-of-articles]))}
            :handlers (into {}
                        (for [handler-key (u/list-handler-keys route-mapping)]
                          [handler-key (u/when-group-> {} (handler-key group-mapping)
                                         :all (assoc :request-processing-time (percentiles (get-in metrics [:handlers handler-key :request-processing-time])))
                                         :all (assoc :request-rate (rates (get-in metrics [:handlers handler-key :request-rate])))
                                         #{:api} (assoc :2xx-response-rate (rates (get-in metrics [:handlers handler-key :2xx-response-rate])))
                                         #{:api} (assoc :3xx-response-rate (rates (get-in metrics [:handlers handler-key :3xx-response-rate])))
                                         #{:api} (assoc :4xx-response-rate (rates (get-in metrics [:handlers handler-key :4xx-response-rate])))
                                         #{:api} (assoc :5xx-response-rate (rates (get-in metrics [:handlers handler-key :5xx-response-rate])))
                                         #{:api} (assoc :open-requests (c/value (get-in metrics [:handlers handler-key :open-requests]))))]))}}))


(defn make-not-found-handler
  []
  (fn [_] {:status 404}))
