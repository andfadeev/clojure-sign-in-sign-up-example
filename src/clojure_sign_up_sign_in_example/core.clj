(ns clojure-sign-up-sign-in-example.core
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [clojure-sign-up-sign-in-example.components.http-server-component
             :as http-server-component]
            [next.jdbc.connection :as connection])
  (:import (com.zaxxer.hikari HikariDataSource)
           (org.flywaydb.core Flyway)))

(defn datasource-component
  [config]
  (connection/component
    HikariDataSource
    (assoc (:db-spec config)
           :init-fn (fn [datasource]
                      (log/info "Running database init")
                      (.migrate
                        (.. (Flyway/configure)
                            (dataSource datasource)
                            ; https://www.red-gate.com/blog/database-devops/flyway-naming-patterns-matter
                            (locations (into-array String ["classpath:database/migrations"]))
                            (table "schema_version")
                            (load)))))))

(defn create-system
  [config]
  (component/system-map

    :datasource (datasource-component config)

    :http-server-component
    (component/using
      (http-server-component/new-http-server-component config)
      [:datasource])))

(defn -main
  []
  (let [system (-> {}
                   (create-system)
                   (component/start-system))]
    (.addShutdownHook
      (Runtime/getRuntime)
      (new Thread #(component/stop-system system)))))
