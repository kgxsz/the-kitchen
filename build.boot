(set-env!
 :source-paths #{"src"}
 :resource-paths #{"resources"}
 :dependencies '[[org.clojure/clojure "1.7.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [log4j/log4j "1.2.16"
                  :exclusions [javax.mail/mail javax.jms/jms com.sun.jdmk/jmxtools com.sun.jmx/jmxri]]
                 [jarohen/yoyo "0.0.6-beta2"]
                 [jarohen/nomad "0.8.0-beta3"
                  :exclusions [org.clojure/clojure org.clojure/tools.nrepl]]
                 [http-kit "2.1.19"]
                 [bidi "1.20.3"]])

(require '[the-playground.core])

(deftask build
  "Build the uberjar"
  []
  (comp
   (aot :namespace '#{the-playground.core})
   (pom :project 'the-playground :version "0.1.0")
   (uber)
   (jar :main 'the-playground.core)))

(deftask run
  []
  "Kick off the main function"
  (with-pre-wrap fileset
    (with-bindings {#'*data-readers* *data-readers*}
      (boot.core/load-data-readers!)
      (the-playground.core/-main)
      (def dirs (get-env :directories))
      (apply clojure.tools.namespace.repl/set-refresh-dirs dirs))
    fileset))

(deftask develop
  []
  "Setup a development environmet"
  (comp
   (repl :server true :port 8088)
   (run)
   (wait)))
