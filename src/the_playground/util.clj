(ns the-playground.util
  (:require [clojure.string :refer [upper-case]]))


(defn format-request-method
  [request-method]
  (-> request-method name upper-case))

(defmacro middleware->
  "Works like the cond-> macro, but ensures that the handler
   belongs to the correct group before threading it though."
  [handler groups & clauses]
  (assert (even? (count clauses)))
  (let [g (gensym)
        pstep (fn [[group-set step]]
                `(cond
                   (= ~group-set :all) (-> ~g ~step)
                   (clojure.set/subset? ~group-set ~groups) (-> ~g ~step)
                   :else ~g))]
    `(let [~g ~handler
           ~@(interleave (repeat g) (map pstep (partition 2 clauses)))]
       ~g)))
