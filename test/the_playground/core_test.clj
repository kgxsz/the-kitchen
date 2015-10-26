(ns the-playground.core-test
  (:require [the-playground.core :refer :all]
            [cheshire.core :refer [parse-string]]
            [clojure.test :refer :all]
            [yoyo.core :as yc]
            [org.httpkit.client :as http]
            [yoyo.system :as ys]))

(defn make-Δ-test-config
  []
  (yc/->component
   {:http-server-port 8084}))

(defn make-Δ-test-system
  []
  (ys/make-system #{(ys/named make-Δ-test-config :config)
                    (ys/named make-Δ-route-mapping :route-mapping)
                    (ys/named make-Δ-api-handler-mapping :api-handler-mapping)
                    (ys/named make-Δ-aux-handler-mapping :aux-handler-mapping)
                    (ys/named make-Δ-handler :handler)
                    (ys/named make-Δ-http-server :http-server)}))

(deftest end-to-end-test
  (yc/with-component (make-Δ-test-system)
    (fn [test-system]

      (testing "A The list of users can be explored"
        (let [users-res @(http/get "http://localhost:8084/api/users")]
          (is (= 2 (-> users-res :body (parse-string true) :users count)))))


      (testing "A user can be created"
        (let [users-res-initial @(http/get "http://localhost:8084/api/users")
              create-user-res @(http/post "http://localhost:8084/api/users")
              users-res-final @(http/get "http://localhost:8084/api/users")]

          (is (= 2 (-> users-res-initial :body (parse-string true) :users count)))
          (is (= 201 (:status create-user-res)))
          (is (= 2 (-> users-res-final :body (parse-string true) :users count)))))

      (testing "A The list of articles can be explored"
        (let [articless-res @(http/get "http://localhost:8084/api/articles")]
          (is (= 2 (-> articless-res :body (parse-string true) :articles count))))))))
