(ns the-playground.util
  (:require [clojure.string :refer [upper-case]]))

(defn format-request-method
  [request-method]
  (-> request-method name upper-case))
