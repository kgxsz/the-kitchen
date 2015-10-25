(ns the-playground.core
  (:gen-class)
  (:require [the-playground.handlers.api-handlers :as api]
            [the-playground.handlers.aux-handlers :as aux]
            [the-playground.middleware :as m]
            [bidi.ring :refer [make-handler]]
            [cats.core :as c]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [nomad :as n]
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.cors :refer [wrap-cors]]
            [yoyo :as y]
            [yoyo.core :as yc]
            [yoyo.system :as ys]))

(defn make-Δ-config
  []
  (ys/->dep
   (yc/->component
    (n/read-config
     (io/resource "config.edn")))))

(defn make-Δ-route-mapping
  []
  (ys/->dep
   (yc/->component
    ["/" {"api" {"/users" {:get :users
                           :post :create-user}
                 "/articles" {:get :articles}}
          "api-docs" {:get :api-docs}
          true :not-found}])))

(defn make-Δ-api-handler-mapping
  []
  (ys/->dep
   (yc/->component
    {:users (api/make-users-handler)
     :create-user (api/make-create-user-handler)
     :articles (api/make-articles-handler)})))

(defn make-Δ-aux-handler-mapping
  []
  (c/mlet [api-handler-mapping (ys/ask :api-handler-mapping)
           route-mapping (ys/ask :route-mapping)]
    (ys/->dep
     (yc/->component
      {:api-docs (aux/make-api-docs-handler api-handler-mapping route-mapping)
       :not-found (aux/make-not-found-handler)}))))

(defn make-Δ-handler
  []
  (c/mlet [route-mapping (ys/ask :route-mapping)
           api-handler-mapping (ys/ask :api-handler-mapping)
           aux-handler-mapping (ys/ask :aux-handler-mapping)]
    (ys/->dep
     (yc/->component
      (-> (make-handler route-mapping (merge api-handler-mapping
                                             aux-handler-mapping))
          (m/wrap-json-response)
          (wrap-cors :access-control-allow-origin [#"http://petstore.swagger.io"]
                     :access-control-allow-methods [:get :put :post :delete])
          (m/wrap-exception-catching)
          (m/wrap-logging))))))

(defn make-Δ-http-server
  []
  (c/mlet [http-server-port (ys/ask :config :http-server-port)
           handler (ys/ask :handler)]
    (ys/->dep
     (let [stop-fn! (run-server handler {:port http-server-port :join? false})]
        (log/info "Starting HTTP server on port" http-server-port)
        (yc/->component
          nil
          (fn []
            (log/info "Stopping HTTP server")
            (stop-fn! :timeout 500)))))))

(defn make-system
  []
  (ys/make-system #{(ys/named make-Δ-config :config)
                    (ys/named make-Δ-route-mapping :route-mapping)
                    (ys/named make-Δ-api-handler-mapping :api-handler-mapping)
                    (ys/named make-Δ-aux-handler-mapping :aux-handler-mapping)
                    (ys/named make-Δ-handler :handler)
                    (ys/named make-Δ-http-server :http-server)}))

(defn -main
  []
  (log/info "Starting system")
  (y/set-system-fn! #'make-system)
  (y/start!))
