(ns the-playground.core
  (:require [org.httpkit.server :as http-server]
            [bidi.ring :refer (make-handler)]
            [clojure.tools.nrepl.server :as nrepl-server]
            [cider.nrepl :refer (cider-nrepl-handler)]))

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
  {:api      api-handler
   :not-found not-found-handler})

(defn -main [& args]
  (nrepl-server/start-server :port 8088 :handler cider-nrepl-handler)
  (http-server/run-server (make-handler routes handler-fns) {:port 8080}))
