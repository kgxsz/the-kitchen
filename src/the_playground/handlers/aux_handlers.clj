(ns the-playground.handlers.aux-handlers
  (:require [bidi.bidi :as b]
            [metrics.meters :refer [rates]]
            [ring.swagger.swagger2 :as rs]
            [clojure.tools.logging :as log]
            [schema.core :as sc]))

(defn make-api-docs-handler
  [route-mapping api-docs-mapping]
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
                        (for [[handler-key docs] api-docs-mapping
                              request-method [:get :post :put :delete :head :options]
                              :let [path (b/path-for route-mapping handler-key)]
                              :when (= handler-key (:handler (b/match-route route-mapping path :request-method request-method)))]
                          {path {request-method docs}}))}))}))

(defn make-not-found-handler
  []
  (fn [_] {:status 404}))

(defn make-metrics-handler
  [metrics]
  (fn [_]
    {:status 200
     :body (into {} (for [[k v] metrics] [k (rates v)]))}))
