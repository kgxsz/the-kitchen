(ns the-playground.middleware
  (:require [the-playground.schema :as s]
            [the-playground.util :refer [format-request-method]]
            [cheshire.core :refer [generate-string]]
            [clojure.tools.logging :as log]
            [schema.core :as sc]
            [slingshot.slingshot :refer [try+]]))

(defn wrap-validate
  [handler {:keys [request-schema response-schemata]}]
  (fn [{:keys [body request-method uri] :as request}]

    (try+
     (when request-schema (sc/validate request-schema body))
     (catch [:type :schema.core/error] {:keys [error]}
       (log/debug "Invalid" (format-request-method request-method)  "request to" uri "-" error)
       {:status 400
        :body error}))

    (let [{:keys [status body] :as response} (handler request)]
      (sc/validate (get response-schemata status) body)
      response)))

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
