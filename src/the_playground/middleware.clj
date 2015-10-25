(ns the-playground.middleware
  (:require [cheshire.core :refer :all]
            [clojure.string :refer [upper-case]]
            [clojure.tools.logging :as log]
            [slingshot.slingshot :refer [try+]]))

(defn wrap-json-response
  [handler]
  (fn [req]
    (-> (handler req)
        (update :body generate-string)
        (update :headers assoc "Content-Type" "application/json"))))

(defn wrap-docs
  [handler docs]
  (vary-meta handler assoc :docs docs))

(defn wrap-logging
  [handler]
  (fn [{:keys [uri request-method] :as req}]
    (log/debug "Incoming" (-> request-method name upper-case) "request to" uri)
    (let [{:keys [status] :as res} (handler req)]
      (log/debug "Outgoing" status "response for" (-> request-method name upper-case) "request to" uri)
      res)))

(defn wrap-exception-catching
  [handler]
  (fn [req]
    (try+
     (handler req)
     (catch Object e
       (log/error  "Unhandled exception:" e)
       {:status 500}))))

