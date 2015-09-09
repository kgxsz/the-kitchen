(ns the-playground.core
  (:require [org.httpkit.server :refer [run-server]]
            [bidi.ring :refer (make-handler)]))

(defn root-handler [req]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    "Root!"})

(defn hello-handler [req]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    "Hello!"})

(def routes
 ["/" {"" :root
       "hello" :hello}])

(def handler-fns
  {:root root-handler
   :hello hello-handler})

(defn -main [& args]
  (run-server (make-handler routes handler-fns) {:port 8080}))
