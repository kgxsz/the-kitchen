(defproject the-playground "0.1.0-SNAPSHOT"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [com.taoensso/timbre "4.1.1"]
                 [com.stuartsierra/component "0.2.3"]
                 [ring/ring-core "1.4.0"]
                 [http-kit "2.1.19"]
                 [bidi "1.20.3"]]

  :main the-playground.core)
