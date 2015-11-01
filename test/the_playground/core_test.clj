(ns the-playground.core-test
  (:require [the-playground.core :refer :all]
            [cheshire.core :refer [parse-string generate-string]]
            [clojure.test :refer :all]
            [org.httpkit.client :as http]
            [yoyo.core :as yc]
            [yoyo.system :as ys]))

(defn make-Δ-test-config
  []
  (yc/->component
   {:http-server-port 8084}))

(defn make-Δ-test-system
  []
  (ys/make-system #{(ys/named make-Δ-test-config :config)
                    (ys/named make-Δ-metrics :metrics)
                    (ys/named make-Δ-db :db)
                    (ys/named make-Δ-http-server :http-server)}))

(deftest end-to-end-test
  (yc/with-component (make-Δ-test-system)
    (fn [test-system]
      (let [users-url (str "http://localhost:" (get-in test-system [:config :http-server-port]) "/api/users")]

        (testing "A user can be created"
          (let [number-of-users-before (-> @(http/get users-url) :body (parse-string true) :users count)
                create-user-response @(http/post users-url
                                        {:headers {"Content-Type" "application/json"
                                                   "Accept" "application/json"}
                                         :body (generate-string {:name "Peter"})})
                number-of-users-after (-> @(http/get users-url) :body (parse-string true) :users count)]

            (is (= 201 (:status create-user-response)))
            (is (= "Peter" (-> create-user-response :body (parse-string true) :user :name)))
            (is (= (inc number-of-users-before) number-of-users-after))))))))
