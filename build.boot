(set-env!
 :source-paths #{"src" "test"}
 :resource-paths #{"resources"}

 :dependencies '[[org.clojure/clojure "1.7.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [log4j/log4j "1.2.16"
                  :exclusions [javax.mail/mail
                               javax.jms/jms
                               com.sun.jdmk/jmxtools
                               com.sun.jmx/jmxri]]
                 [jarohen/yoyo "0.0.6-beta2"]
                 [jarohen/nomad "0.8.0-beta3"
                  :exclusions [org.clojure/clojure
                               org.clojure/tools.nrepl]]
                 [http-kit "2.1.19"]
                 [bidi "1.20.3"]
                 [metosin/ring-swagger "0.21.0"]
                 [prismatic/schema "1.0.1"]
                 [ring/ring-core "1.4.0"]
                 [cheshire "5.5.0"]
                 [slingshot "0.12.2"]
                 [ring-cors "0.1.7"]
                 [adzerk/boot-test "1.0.4" :scope "test"]])

(require '[the-playground.core]
         '[adzerk.boot-test :refer [test]])

(deftask build
  "Build the uberjar."
  []
  (set-env! :source-paths #{"src"})
  (comp
   (aot :namespace '#{the-playground.core})
   (pom :project 'the-playground :version "0.1.0")
   (uber)
   (jar :main 'the-playground.core)))

(deftask auto-test
  "Run tests on any file changes."
  []
  (comp (watch) (speak) (test)))


(deftask dev
  "Start a development environment."
  []
  (comp
   (repl :server true :port 8088)
   (with-pre-wrap fileset
     (with-bindings {#'*data-readers* *data-readers*}
       (boot.core/load-data-readers!)
       (the-playground.core/-main)
       (def dirs (get-env :directories))
       (apply clojure.tools.namespace.repl/set-refresh-dirs dirs))
     fileset)
   (wait)))
