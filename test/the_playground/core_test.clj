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
    (fn [{:keys [config]}]
      (let [users-url (str "http://localhost:" (:http-server-port config) "/api/users")
            headers {"Content-Type" "application/json", "Accept" "application/json"}]

        (testing "A user can be created"
          (let [number-of-users-before (-> @(http/get users-url) :body (parse-string true) :users count)
                create-user-response @(http/post users-url {:headers headers
                                                            :body (generate-string {:name "Peter"})})
                users-response-body (-> @(http/get users-url) :body (parse-string true))
                number-of-users-after (-> users-response-body :users count)]

            (is (= 201 (:status create-user-response)) "The user creation gets a created response")
            (is (= "Peter" (-> create-user-response :body (parse-string true) :user :name)) "The user creation response includes the new user's name")
            (is (not-empty (filter #(= (:name %) "Peter") (:users users-response-body))) "The new user exists ins the system")
            (is (= (inc number-of-users-before) number-of-users-after) "The number of users in the system has increased by one")))

        (testing "The same user cannot be created more than once"
          (let [number-of-users-before (-> @(http/get users-url) :body (parse-string true) :users count)
                create-user-response @(http/post users-url {:headers headers
                                                            :body (generate-string {:name "Peter"})})
                number-of-users-after (-> @(http/get users-url) :body (parse-string true) :users count)]

            (is (= 409 (:status create-user-response)) "The user creation gets a conflict response")
            (is (= number-of-users-before number-of-users-after) "The number of users in the system has not changed")))))))
