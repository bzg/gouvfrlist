(ns gouvfrlist.server
  (:require [ring.util.response :as response]
            [clojure.java.io :as io]
            [org.httpkit.server :as server]
            [ring.middleware.reload :refer [wrap-reload]]
            [compojure.core :refer [GET POST defroutes]]
            [compojure.route :refer [not-found resources]]
            [cheshire.core :as json]
            [etaoin.api :as e]
            [etaoin.dev :as edev]
            [hickory.core :as h]
            [gouvfrlist.db :as db]
            [gouvfrlist.config :as config]
            [tea-time.core :as tt]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.core :as appenders])
  (:gen-class))

(timbre/set-config!
 {:level     :debug
  :output-fn (partial timbre/default-output-fn {:stacktrace-fonts {}})
  :appenders
  {:println (timbre/println-appender {:stream :auto})
   :spit    (appenders/spit-appender {:fname (:log-file config/opts)})}})

(def chromium-opts
  {:path-driver   (:path-driver config/opts)
   :path-browser  (:path-browser config/opts)
   :load-strategy :none
   :headless      true
   :dev
   {:perf
    {:level      :all
     :network?   true
     :page?      true
     :interval   1000
     :categories [:devtools]}}})

(defn total-content-length [req]
  (reduce
   + (map read-string
          (remove nil?
                  (map #(or (get-in % [:response :headers :Content-Length])
                            (get-in % [:response :headers :content-length]))
                       req)))))

(defn website-html-infos [s]
  (let [h             (filter #(not (string? %)) (h/as-hiccup (h/parse s)))
        s             (tree-seq sequential? #(filter vector? %) h)
        count-tags    (count (rest s))
        get-vals      (fn [k] (filter #(= (first %) k) s))
        get-meta-vals (fn [ks prop]
                        (:content
                         (last (first (filter #(= ks (prop (last %)))
                                              (get-vals :meta))))))
        title         (last (first (get-vals :title)))
        description   (get-meta-vals "description" :name)
        keywords      (get-meta-vals "keywords" :name)]
    {:title       (or title "")      
     :tags        count-tags
     :description (or description "")
     :keywords    (or keywords "")
     :og:image    (or (get-meta-vals "og:image" :property) "")}))

(defn website-logs-infos [logs]
  (let [requests (edev/logs->requests logs)
        logs0    (filter #(= (:method %) "Network.responseReceived")
                         (map #(:message %) logs))
        logs1    (first (filter ;; FIXME: relying on the first response?
                         #(get-in % [:params :response :url])
                         logs0))]
    {:requests-number (count requests)
     :is-secure?      (if (= (get-in
                              logs1 [:params :response :securityState]) "secure")
                        true false)
     ;; :ip-address      (or (get-in logs1 [:params :response :remoteIPAddress]) "")
     :content-length  (total-content-length requests)}))

(defn website-infos [url]
  (let [s (atom nil)
        l (atom nil)
        i (str (clojure.string/replace
                (last (re-find #"(?i)(https?://)(.+[^/])" url))
                #"/" "-") ".jpg")]
    (if (db/get-website url)
      (timbre/info (str "Skipping " url))
      (try
        (let [c (e/chrome chromium-opts)]
          (timbre/info (str "Start for " url))
          (e/with-wait (:wait config/opts)
            (e/go c url)
            (e/screenshot c (str (:path-screenshots config/opts) i))
            (reset! s (e/get-source c))
            (reset! l (edev/get-performance-logs c)))
          (db/add-or-update-entity
           (merge
            {:url              url
             :capture-filename i
             :using-ga?        (if (re-find #"UA-[0-9]+-[0-9]+" @s)
                                 true
                                 false)}
            (website-html-infos @s)
            (website-logs-infos @l)))
          (timbre/info (str "Done for " url)))
        (catch Exception e
          (timbre/error
           (str "Can't fetch data for " url ": "
                (:cause (Throwable->map e)))))))))

(def valid-domains
  (with-open [rdr (io/reader "tested.gouv.fr.txt")]
    (reduce conj [] (line-seq rdr))))

(defn build-websites-database []
  (doseq [d valid-domains]
    (website-infos d)))

(def rebuild-database
  (tt/every! (:rebuild-interval config/opts)
             0 build-websites-database))

(defn default-page []
  (assoc
   (response/response
    (io/input-stream
     (io/resource
      "public/index.html")))
   :headers {"Content-Type" "text/html; charset=utf-8"}))

(defroutes routes
  (GET "/" [] (default-page))
  (GET "/all" []
       (assoc
        (response/response
         (json/generate-string (db/get-all-filtered-websites)))
        :headers {"Content-Type" "application/json; charset=utf-8"}))
  (resources "/")
  (not-found "Not Found"))

(def app (-> #'routes wrap-reload))

(defn -main [& args]
  ;; (tt/start!)
  (let [port (read-string (or (System/getenv "GOUVFRLIST_PORT")
                              (:port config/opts) "3000"))]
    (def server (server/run-server app {:port port}))))

;; (-main)
