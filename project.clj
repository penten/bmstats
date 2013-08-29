(defproject bmstats "0.1.0-SNAPSHOT"
  :description "PyBitmessage statistic gatherer"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/java.jdbc "0.3.0-alpha4"]
                 [org.xerial/sqlite-jdbc "3.7.2"]
                 [jfree/jfreechart "1.0.13"]]
  :main bmstats.core)
