(ns dev
  (:require [com.stuartsierra.component.repl :as component-repl]
            [clojure-sign-up-sign-in-example.core :as core]))

(component-repl/set-init
  (fn [_]
    (core/create-system
      {:server {:port 3000}
       :db-spec {:jdbcUrl "jdbc:postgresql://localhost:5432/db"
                 :username "local"
                 :password "local"}})))