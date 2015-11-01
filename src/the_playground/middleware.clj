(ns the-playground.middleware
  (:require [the-playground.schema :as s]
            [the-playground.util :refer [format-request-method]]
            [bidi.bidi :as b]
            [cheshire.core :refer [generate-string]]
            [clojure.tools.logging :as log]
            [metrics.meters :refer [mark!]]
            [metrics.timers :refer [time!]]
            [metrics.counters :refer [inc! dec!]]
            [schema.core :as sc]
            [slingshot.slingshot :refer [try+]]))

(defn wrap-instrument
  [handler metrics route-mapping]
  (fn [{:keys [uri request-method] :as request}]
    (let [handler-key (:handler (b/match-route route-mapping uri :request-method request-method))
          handler-metrics (get-in metrics [:api-handlers handler-key])]
      (if handler-metrics
        (time! (:request-processing-time handler-metrics)
          (mark! (:request-rate handler-metrics))
          (inc! (:open-requests handler-metrics))
          (let [{:keys [status] :as response} (handler request)]
            (dec! (:open-requests handler-metrics))
            (cond
              (>= status 500) (mark! (:5xx-response-rate handler-metrics))
              (>= status 400) (mark! (:4xx-response-rate handler-metrics))
              :else (mark! (:2xx-response-rate handler-metrics)))
            response))
        (handler request)))))

(defn wrap-validate
  [handler {:keys [request-schema response-schemata]}]
  (fn [{:keys [body request-method uri] :as request}]
    (try+
     (when request-schema (sc/validate request-schema body))
     (let [{:keys [status body] :as response} (handler request)]
       (sc/validate (get response-schemata status) body)
       response)
     (catch [:type :schema.core/error :schema request-schema] {:keys [error]}
       (log/debug "Validation failed for incoming" (format-request-method request-method)  "request to" uri "-" error)
       {:status 400
        :body {:error error}}))))

(defn wrap-json-response
  [handler]
  (fn [request]
    (-> (handler request)
        (update :body generate-string)
        (update :headers assoc "Content-Type" "application/json"))))

(defn wrap-docs
  [handler docs]
  (vary-meta handler assoc :docs docs))

(defn wrap-logging
  [handler]
  (fn [{:keys [uri request-method] :as request}]
    (log/debug "Incoming" (format-request-method request-method) "request to" uri)
    (let [{:keys [status] :as response} (handler request)]
      (log/debug "Outgoing" status "response for" (format-request-method request-method) "request to" uri)
      response)))

(defn wrap-exception-catching
  [handler]
  (fn [request]
    (try+
     (handler request)
     (catch Object e
       (log/error  "Unhandled exception -" e)
       {:status 500}))))
