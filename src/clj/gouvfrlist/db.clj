(ns gouvfrlist.db
  (:require [datahike.api :as d]
            [gouvfrlist.config :as config]))

(defn db-uri [] (:db-uri config/opts))

;; Define the global database schema
(def db-schema {})

;; Create the database
(defn create-database-with-schema [schema]
  (d/create-database (db-uri) db-schema :schema-on-read true))

;; ;; Delete the database
;; (defn delete-database []
;;   (d/delete-database (db-uri)))

(try (d/connect (db-uri))
     (catch Exception e
       (println "Initializing datahike database")
       (d/create-database (db-uri) db-schema :schema-on-read true)))

;; Make the connection to the database
(def db-conn (d/connect (db-uri)))

;; Add or update an entity with entity attributes as a hashmap
(defn add-or-update-entity [attrs]
  ;; Add entity
  (if-let [id (:db/id attrs)]
    @(d/transact db-conn [(conj {:db/id id} attrs)])
    @(d/transact db-conn [(conj {:db/id (d/tempid -1)} attrs)])))

(defn get-website [url]
  (let [db  (d/db db-conn)
        uid (ffirst
             (d/q `[:find ?e :where [?e :url ~url]]
                  @db-conn))]
    (if uid (d/pull db '[*] uid))))

(defn get-domains []
  (ffirst (d/q '[:find (pull ?e [*])
                 :where [?e :domains]]
               @db-conn)))

(defn get-all-websites []
  (map first (d/q '[:find (pull ?e [*])
                    :where [?e :title]]
                  @db-conn)))
