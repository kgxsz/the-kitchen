(ns the-playground.core-test
  (:require [the-playground.core :refer :all]
            [the-playground.util :as u]
            [cheshire.core :refer [parse-string generate-string]]
            [clojure.test :refer :all]
            [org.httpkit.client :as http]
            [yoyo.core :as yc]
            [bidi.bidi :as b]
            [yoyo.system :as ys]))


(defn extract-collection
  [body]
  (-> body (parse-string true) :collection))


(defn extract-items
  [body]
  (-> body extract-collection :items))


(defn fill-template
  [body m]
  (->> (parse-string body true)
       :collection :template :data
       (map (fn [d] (assoc d :value ((:name d) m))))
       (assoc-in {} [:template :data])
       (generate-string)))


(defn make-Δ-test-config
  []
  (yc/->component
   {:http-server-port 8084}))


(defn make-Δ-test-db
  []
  (let [db (atom {:users []})]
    (yc/->component db)))


(defn make-Δ-test-system
  []
  (ys/make-system #{(ys/named make-Δ-test-config :config)
                    (ys/named make-Δ-metrics :metrics)
                    (ys/named make-Δ-test-db :db)
                    (ys/named make-Δ-http-server :http-server)}))


(deftest end-to-end-test
  (yc/with-component (make-Δ-test-system)
    (fn [{:keys [config]}]

      (let [make-url (fn [path] (str "http://localhost:" (:http-server-port config) path))
            users-url (make-url (b/path-for route-mapping :users))
            post-headers {"Content-Type" "application/vnd.collection+json", "Accept" "application/vnd.collection+json"}
            get-headers {"Accept" "application/vnd.collection+json"}]


        (testing "the collection of users can be explored"
          (let [{:keys [status body]} @(http/get users-url {:headers get-headers})]

            (is (= 200 status) "the request gets an ok response")
            (is (= 0 (-> body extract-items count)) "there are no users in the collection")))


        (testing "a user can be created"
          (let [{:keys [body]} @(http/get users-url {:headers get-headers})
                {:keys [status body]} @(http/post users-url {:headers post-headers :body (fill-template body {"name" "Peter"})})]

              (is (= 201 status) "the request gets a created response")
              (is (= "Peter" (-> body extract-items first :data u/get-name)) "the response contains the created user")))


        (testing "a user cannot be created more than once"
          (let [{:keys [body]} @(http/get users-url {:headers get-headers})
                {:keys [status body]} @(http/post users-url {:headers post-headers :body (fill-template body {:name "Peter"})})]

              (is (= 409 status) "The user creation gets a conflict response")
              (is (= "user-already-exists" (-> body extract-collection :error :title)) "the response contains the error")))


        (testing "the collection of users now contains a single user"
          (let [{:keys [status body]} @(http/get users-url {:headers get-headers})]

            (is (= 200 status) "the request gets an ok response")
            (is (= 1 (-> body extract-items count)) "there is a single users in the collection")))


        (testing "an individual user can be explored"
          (let [{:keys [body]} @(http/get users-url {:headers get-headers})
                user-url (make-url (-> body extract-items first :href))
                {:keys [status body]} @(http/get user-url {:headers get-headers})]

            (is (= 200 status) "the request gets an ok response")
            (is (= "Peter" (-> body extract-items first :data u/get-name)) "the user's name is Peter")))


        (testing "A user's details can be updated")

        (testing "A user can be deleted")))))
