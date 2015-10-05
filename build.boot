(set-env!
 :source-paths #{"src"}
 :dependencies '[[org.clojure/clojure "1.7.0"]
                 [com.taoensso/timbre "4.1.1"]
                 [org.clojure/tools.namespace "0.2.11"]
                 [jarohen/yoyo "0.0.6-beta2"]
                 [jarohen/nomad "0.8.0-beta3" :exclusions [org.clojure/clojure]]
                 [ring/ring-core "1.4.0"]
                 [http-kit "2.1.19"]
                 [bidi "1.20.3"]])

(deftask build
  "Build the uberjar"
  []
  (comp
   (aot :namespace '#{the-playground.core})
   (pom :project 'the-playground :version "0.1.0")
   (uber)
   (jar :main 'the-playground.core)))

(deftask develop []
  (comp
   (repl :server true :port 8088)
   (wait)))

;; 1) boot run
;; 2) boot repl -c -p 8088
;; 3) (boot.core/load-data-readers!)
;; 4) (load-file "src/the_playground/core.clj")
;; 5) (the-playground.core/-main)
;; 6) (def dirs (get-env :directories))
;; 7) (apply clojure.tools.namespace.repl/set-refresh-dirs dirs)
