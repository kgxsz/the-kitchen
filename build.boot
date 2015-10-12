(set-env!
 :source-paths #{"src"}
 :dependencies '[[org.clojure/clojure "1.7.0"]
                 #_[com.taoensso/timbre "4.1.1"]
                 #_[org.clojure/tools.namespace "0.2.11"]
                 #_[jarohen/yoyo "0.0.6-beta2"]
                 #_[jarohen/nomad "0.8.0-beta3" :exclusions [org.clojure/clojure]]
                 #_[ring/ring-core "1.4.0"]
                 [http-kit "2.1.19"]
                 [bidi "1.20.3"]])

(require '[the-playground.core])

#_(deftask run []
  (with-pre-wrap fileset

    (with-bindings {#'*data-readers* *data-readers*}
      (boot.core/load-data-readers!) ;; loads them into the #'clojure.core/*data-readers* var
      (the-playground.core/-main)
      (def dirs (get-env :directories))
      (apply clojure.tools.namespace.repl/set-refresh-dirs dirs))

    fileset))

(deftask build
  "Build the uberjar"
  []
  (comp
   (aot :namespace '#{the-playground.core})
   (pom :project 'the-playground :version "0.1.0")
   (uber)
   (jar :main 'the-playground.core)))

(deftask run []
  (with-pre-wrap fileset
    (the-playground.core/-main)
    fileset))

(deftask develop
  []
  (comp
   (repl :server true :port 8088)
   (run)
   (wait)))

;; 1) boot run
;; 2) boot repl -c -p 8088
;; 3) (boot.core/load-data-readers!)
;; 4) (load-file "src/the_playground/core.clj")
;; 5) (the-playground.core/-main)
;; 6) (def dirs (get-env :directories))
;; 7) (apply clojure.tools.namespace.repl/set-refresh-dirs dirs)
