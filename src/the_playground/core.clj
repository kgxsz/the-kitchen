(ns the-playground.core
  (:require [org.httpkit.server :refer [run-server]]
            [bidi.ring :refer (make-handler)]))

(defn api-handler [req]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    "Welcome to the API!"})

(defn not-found-handler [req]
  {:status  404
   :headers {"Content-Type" "text/html"}
   :body    "Not found."})

(def routes
 ["/" [["api" :api]
       [true  :not-found]]])

(def handler-fns
  {:api       api-handler
   :not-found not-found-handler})

(defn -main [& args]
  (run-server (make-handler routes handler-fns) {:port 8080}))
