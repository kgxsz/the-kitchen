(ns the-playground.core
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.server :as http-server]
            [bidi.ring :refer (make-handler)]
            [clojure.tools.nrepl.server :as nrepl-server]
            [cider.nrepl :refer (cider-nrepl-handler)]
            [taoensso.timbre :refer [info]]))


(defn api-handler [req]
  (info "Request to /api")
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    "Welcome to the API!"})


(defn not-found-handler [req]
  (info "Request to unknown route")
  {:status  404
   :headers {"Content-Type" "text/html"}
   :body    "Not found."})


(def routes
 ["/" [["api" :api]
       [true  :not-found]]])


(def handler-fns
  {:api       api-handler
   :not-found not-found-handler})


(defrecord HTTPServer [port server]
  component/Lifecycle

  (start [component]
    (let [handler (make-handler routes handler-fns)
          http-server (http-server/run-server handler {:port port :join? false})]
      (info "Started http server on port" port)
      (assoc component :http-server http-server)))

  (stop [component]
    (when-let [http-server (:http-server component)]
      (http-server :timeout 500)
      (info "Stopped http server")
      (dissoc component :http-server))))


(defn make-http-server
  [port]
  (map->HTTPServer {:port port}))


(defn make-system
  [{:keys [port] :as config}]
  (component/system-map
    :http-server (make-http-server port)))


(def system nil)


(defn init []
  (alter-var-root #'system (constantly (make-system {:port 8080}))))


(defn start-system
  []
  (alter-var-root #'system component/start))


(defn stop-system []
  (alter-var-root #'system
                  (fn [s] (when s (component/stop s)))))


(defn -main [& args]
  (let [nrepl-port 8088
        http-port  8080]
    (nrepl-server/start-server :port nrepl-port :handler cider-nrepl-handler)
    (info "Started nREPL server on port" nrepl-port)))
