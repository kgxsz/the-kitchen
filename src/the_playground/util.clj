(ns the-playground.util
  (:require [clojure.string :refer [upper-case]]))


(defn get-value
  [user name]
  (->> user (some #(when (= (:name %) name) %)) :value))


(defn get-user-id
  [user]
  (get-value user "user-id"))


(defn get-name
  [user]
  (get-value user "name"))


(defn format-request-method
  [request-method]
  (-> request-method name upper-case))


(defn list-handler-keys
  "Extracts every handler key described in the route mapping."
  [route-mapping]
  (letfn [(get-keys [m] (for [[k v] m] (if (map? v) (get-keys v) v)))]
    (flatten (get-keys (second route-mapping)))))


(defmacro when-group->
  "Works like the cond-> macro, but ensures that the subject
   is threaded only when the clause's groups are a subset of
   the subject's groups, or if the :all keyword is used."
  [subject subject-groups & clauses]
  (assert (even? (count clauses)))
  (let [g (gensym)
        pstep (fn [[clause-groups clause-form]]
                `(cond
                   (= ~clause-groups :all) (-> ~g ~clause-form)
                   (clojure.set/subset? ~clause-groups ~subject-groups) (-> ~g ~clause-form)
                   :else ~g))]
    `(let [~g ~subject
           ~@(interleave (repeat g) (map pstep (partition 2 clauses)))]
       ~g)))
