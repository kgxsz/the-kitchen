(ns the-playground.middleware
  (:require [the-playground.schema :as s]
            [the-playground.util :refer [format-request-method]]
            [bidi.bidi :as b]
            [cheshire.core :refer [generate-string]]
            [clojure.set :as set]
            [clojure.tools.logging :as log]
            [metrics.counters :refer [inc! dec!]]
            [metrics.meters :refer [mark!]]
            [metrics.timers :refer [time!]]
            [schema.core :as sc]
            [slingshot.slingshot :refer [try+]]))


(defn wrap-instrument-rates
  [handler metrics]

  (fn [{:keys [handler-key] :as request}]
    (if-let [handler-metrics (get-in metrics [:api-handlers handler-key])]

      (do (mark! (:request-rate handler-metrics))
          (let [{:keys [status] :as response} (handler request)]
            (cond
              (>= status 500) (mark! (:5xx-response-rate handler-metrics))
              (>= status 400) (mark! (:4xx-response-rate handler-metrics))
              :else (mark! (:2xx-response-rate handler-metrics)))
            response))

      (handler request))))


(defn wrap-instrument-open-requests
  [handler metrics]

  (fn [{:keys [handler-key] :as request}]
    (if-let [handler-metrics (get-in metrics [:api-handlers handler-key])]

      (do (inc! (:open-requests handler-metrics))
          (let [response (handler request)]
            (dec! (:open-requests handler-metrics))
            response))

      (handler request))))


(defn wrap-instrument-timer
  [handler metrics]
  (fn [{:keys [handler-key] :as request}]
    (if-let [handler-metrics (get-in metrics [:api-handlers handler-key])]
      (time! (:request-processing-time handler-metrics) (handler request))
      (handler request))))


(defn wrap-handler-key
  "Determine the intended handler and associate its key to the request map."
  [handler route-mapping]

  (fn [{:keys [uri request-method] :as request}]
    (let [handler-key (:handler (b/match-route route-mapping uri :request-method request-method))]
      (handler (assoc request :handler-key handler-key)))))


(defn wrap-validate
  "Ensures the the request and response satisfy their schema.
   Return a 400 response for an unsatisfied request schema.
   Throws an exception up for an unsatisfied response schema.
   Don't use this at the top level, only as local handler middleware."
  [handler {:keys [request-schema response-schemata]}]

  (fn [{:keys [body request-method uri] :as request}]
    (try+

     (when request-schema (sc/validate request-schema body))

     (let [{:keys [status body] :as response} (handler request)]
       (sc/validate (get response-schemata status) body)
       response)

     (catch [:type :schema.core/error :schema request-schema] {:keys [error]}
       (log/warn "Validation failed for incoming" (format-request-method request-method)  "request to" uri "-" error)
       {:status 400
        :body {:error error}}))))


(defn wrap-json-response
  "Ensures that the outgoing response is written to JSON, and the approproate headers are set."
  [handler]

  (fn [request]
    (-> (handler request)
        (update :body generate-string)
        (update :headers assoc "Content-Type" "application/json"))))


(defn wrap-docs
  "Associates the docs to the handler's metadata. Don't use this
   at the top level, only as local handler middleware."
  [handler docs]
  (vary-meta handler assoc :docs docs))


(defn wrap-logging
  "Logs the request and response with associated data."
  [handler]

  (fn [{:keys [uri request-method handler-key] :as request}]
    (log/debug "Incoming" (format-request-method request-method) "request to" uri "handled by" handler-key)
    (let [{:keys [status] :as response} (handler request)]
      (log/debug "Outgoing" status "response for" (format-request-method request-method) "request to" uri "handled by" handler-key)
      response)))


(defn wrap-exception-catching
  "Returns a 500 when an exception goes unhandled."
  [handler]

  (fn [request]
    (try+
     (handler request)
     (catch Exception e
       (log/error  "Unhandled exception -" e)
       {:status 500}))))
