(ns the-playground.core
  (:gen-class)
  (:require [bidi.ring :refer [make-handler]]
            [bidi.bidi :as b]
            [cats.core :as c]
            [clojure.tools.logging :as log]
            [clojure.string :refer [replace-first]]
            [clojure.java.io :as io]
            [nomad :as n]
            [org.httpkit.server :refer [run-server]]
            [yoyo.core :as yc]
            [yoyo :as y]
            [yoyo.system :as ys]
            [schema.core :as s]
            [ring.util.response :refer (url-response)]
            [cheshire.core :refer :all]
            [slingshot.slingshot :refer [try+]]
            [ring.swagger.swagger2 :as rs]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(s/defschema User {:id s/Int
                   :name s/Str})

(s/defschema Article {:id s/Int
                      :title s/Str
                      :text s/Str})

(s/defschema UsersResponse {:users [User]})

(s/defschema CreateUserResponse {:user User})

(s/defschema ArticlesResponse {:articles [Article]})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn wrap-each-handler
  [handler-mapping middleware]
  "Wraps each of the supplied middleware around each handler defined within the
   the handler mapping. This is useful for wrapping many handlers at once,
   without affecting every handler in the application."
  (into {}
    (for [[handler-key handler] handler-mapping]
      [handler-key ((apply comp middleware) handler)])))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


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
  (fn [{:keys [uri] :as req}]
    (log/debug "Processing request to" uri)
    (let [{:keys [status] :as res} (handler req)]
      (log/debug "Dispatching response with status" status "for request to" uri)
      res)))

(defn wrap-exception-catching
  [handler]
  (fn [req]
    (try+
     (handler req)
     (catch Object e
       (log/error  "Unhandled exception:" e)
       {:status 500}))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn make-users-handler
  []
  (-> (fn [req]
        {:status 200
         :body (s/validate UsersResponse
                 {:users [{:id 123, :name "Bob"}
                          {:id 321, :name "Jane"}]})})

      (wrap-docs {:summary "Gets a list of users"
                  :description "Lists all the users"
                  :tags ["Users"]
                  :responses {200 {:schema UsersResponse
                                   :description "The list of users"}}})))

(defn make-create-user-handler
  []
  (-> (fn [req]
        {:status 201
         :body (s/validate CreateUserResponse
                 {:user {:id 456 :name "Alice"}})})

      (wrap-docs {:summary "Creates a user"
                  :description "Creates a user"
                  :tags ["Users"]
                  :responses {201 {:schema CreateUserResponse
                                   :description "The created user"}}})))

(defn make-articles-handler
  []
  (-> (fn [req]
        {:status 200
         :body (s/validate ArticlesResponse
                 {:articles [{:id 176, :title "Things I like", :text "I like cheese and bread."}
                             {:id 346, :title "Superconductivity", :text "It's really hard to understand."}]})})

      (wrap-docs {:summary "Gets a list of articles"
                  :description "Lists all the articles"
                  :tags ["Articles"]
                  :responses {200 {:schema ArticlesResponse
                                   :description "The list of articles"}}})))

(defn make-swagger-ui-handler
  []
  (fn [{:keys [uri]}]
    (if-let [resource (->> (if (= uri "/swagger-ui") "/index.html" "")
                           (str (replace-first uri #"/" ""))
                           (io/resource))]
      (url-response resource)
      {:status 404})))

(defn generate-api-docs
  [api-handler-mapping route-mapping]
  (s/with-fn-validation
    (rs/swagger-json
     {:info {:version "1.0.0"
             :title "The Playground"
             :description "A place to explore"}
      :tags [{:name "User"
              :description "User stuff"}]
      :paths (apply
              merge-with
              merge
              (for [[handler-key _] api-handler-mapping
                    request-method [:get :post :put :delete :head :options]
                    :let [path (b/path-for route-mapping handler-key)]
                    :when (= handler-key (:handler (b/match-route route-mapping path :request-method request-method)))]
                {path {request-method (:docs (meta (handler-key api-handler-mapping)))}}))})))

(defn make-api-docs-handler
  [api-handler-mapping route-mapping]
  (fn [req]
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (generate-string (generate-api-docs api-handler-mapping route-mapping))}))

(defn make-not-found-handler
  []
  (fn [req] {:status 404}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn make-Δ-route-mapping
  []
  (ys/->dep
   (yc/->component
    ["/" {"api" {"/users" {:get :users
                           :post :create-user}
                 "/articles" {:get :articles}}
          "swagger-ui" {:get {true :swagger-ui}}
          "api-docs" {:get :api-docs}
          true :not-found}])))

(defn make-Δ-api-handler-mapping
  []
  (ys/->dep
   (yc/->component
    {:users (make-users-handler)
     :create-user (make-create-user-handler)
     :articles (make-articles-handler)})))

(defn make-Δ-aux-handler-mapping
  []
  (c/mlet [api-handler-mapping (ys/ask :api-handler-mapping)
           route-mapping (ys/ask :route-mapping)]
    (ys/->dep
     (yc/->component
      {:swagger-ui (make-swagger-ui-handler)
       :api-docs (make-api-docs-handler api-handler-mapping route-mapping)
       :not-found (make-not-found-handler)}))))

(defn make-Δ-handler
  []
  (c/mlet [route-mapping (ys/ask :route-mapping)
           api-handler-mapping (ys/ask :api-handler-mapping)
           aux-handler-mapping (ys/ask :aux-handler-mapping)]
    (ys/->dep
     (yc/->component
      (let [wrapped-api-handler-mapping (wrap-each-handler api-handler-mapping [wrap-json-response])]
        (-> (make-handler route-mapping (merge wrapped-api-handler-mapping
                                               aux-handler-mapping))
            (wrap-logging)
            (wrap-exception-catching)))))))

(defn make-Δ-config
  []
  (ys/->dep
   (yc/->component
    (n/read-config
     (io/resource "config.edn")))))

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
