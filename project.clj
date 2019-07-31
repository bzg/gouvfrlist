;; Copyright (c) 2019 Bastien Guerry <bastien.guerry@data.gouv.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSES/EPL-2.0.txt

(defproject gouvfrlist "0.3.0"

  :description "Frontend to display public sector source code repositories"
  :url "https://github.com/bzg/gouvfrlist"
  :license {:name "Eclipse Public License - v 2.0"
            :url  "http://www.eclipse.org/legal/epl-v20.html"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [etaoin "0.3.5"]
                 [io.replikativ/datahike "0.1.3"]
                 [compojure "1.6.1"]
                 [http-kit "2.3.0"]
                 [clj-http "3.10.0"]
                 [ring "1.7.1"]
                 [ring-middleware-format "0.7.4"]
                 [ring-cors "0.1.13"]
                 [cheshire "5.8.1"]
                 [hickory "0.7.1"]
                 [clojure-csv/clojure-csv "2.0.2"]
                 [com.taoensso/timbre "4.10.0"]
                 [tea-time "1.0.1"]]
  :jvm-opts ["-Xmx1g"]
  :source-paths ["src/clj" "src/cljs"]
  :main gouvfrlist.server
  :uberjar-name "gouvfrlist-standalone.jar"
  :auto-clean false
  :clean-targets ^{:protect false} ["target" "resources/public/js/dev/"
                                    "resources/public/js/gouvfrlist.js"]
  :aliases {"fig"       ["trampoline" "run" "-m" "figwheel.main"]
            "fig:build" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "fig:min"   ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "dev"]}
  :profiles {:uberjar {:aot :all}
             :dev     {:source-paths ["src/cljs"]
                       :dependencies [[cljs-ajax "0.8.0"]
                                      [cljs-bean "1.3.0"]
                                      [com.bhauman/figwheel-main "0.2.3"]
                                      [com.bhauman/rebel-readline-cljs "0.1.4"]
                                      [markdown-to-hiccup "0.6.2"]
                                      [org.clojure/clojurescript "1.10.520"]
                                      [org.clojure/core.async "0.4.500"]
                                      [re-frame "0.10.8"]
                                      [reagent "0.8.1"]
                                      [reagent-utils "0.3.3"]]}})
