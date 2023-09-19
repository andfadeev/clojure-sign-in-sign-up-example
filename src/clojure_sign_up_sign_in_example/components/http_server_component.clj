(ns clojure-sign-up-sign-in-example.components.http-server-component
  (:require [com.stuartsierra.component :as component]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.params :as params]
            [ring.middleware.keyword-params :as keyword-params]
            [compojure.core :refer [GET POST routes]]
            [ring.util.response :as response]
            [buddy.hashers :as bh]
            [hiccup.page :as hp]
            [hiccup2.core :as h]
            [jdbc-ring-session.core :as jdbc-ring-session]
            [clojure-sign-up-sign-in-example.views :as views]
            [ring.middleware.session :as ring-session])
  (:import (org.eclipse.jetty.server Server)))

(defn page-title
  [title]
  (str title " | Example Application"))

(defn- layout
  [body {:keys [title]}]
  [:head
   [:meta {:charset "UTF-8"}]
   [:title title]
   (hp/include-js
     "https://cdn.tailwindcss.com?plugins=forms"
     "https://unpkg.com/htmx.org@1.9.4")
   [:body {:hx-boost "true"} body]])

(defn ok
  ([body headers]
   {:status 200
    :headers (merge {"Content-Type" "text/html"}
                    headers)
    :body (-> body
              (h/html)
              (str))})
  ([body]
   (ok body {})))


(def app-routes
  (routes
    (GET "/" request
      (if (seq (:session request))
        (response/redirect "/dashboard")
        (-> (views/home-page)
            (layout {:title (page-title "Home")})
            (ok))))

    (GET "/sign-in" request
      (let [{:keys [session]} request]
        (if (seq session)
          (response/redirect "/dashboard")
          (-> (views/sign-in-page)
              (layout {:title (page-title "Sign In")})
              (ok)))))

    (POST "/sign-in" request
      (let [{:keys [datasource]} (:dependencies request)
            {:keys [email
                    password]} (:params request)
            account (jdbc/execute-one!
                      (datasource)
                      (-> {:select :*
                           :from [:accounts]
                           :where [:= :email email]}
                          (sql/format))
                      {:builder-fn rs/as-unqualified-kebab-maps})]

        (if (and account
                 (:valid (bh/verify password (:password account))))
          {:status 200
           :headers {"HX-Redirect" "/dashboard"}
           :session (select-keys (into {} account) [:email :created-at])}
          (-> (views/sign-in-form {:error "Something is wrong"})
              (ok)))))

    (GET "/sign-up" request
      (if (seq (:session request))
        (response/redirect "/dashboard")
        (-> (views/sign-up-page)
            (layout {:title (page-title "Sign Up")})
            (ok))))

    (POST "/sign-up" request
      (let [{:keys [datasource]} (:dependencies request)
            {:keys [email
                    password
                    password-confirmation]} (:params request)]
        ;; Just an example of form validation (checking that passwords are typed correctly)
        ;; Could be extended to check if user by email is already exists and other form validations
        (if (= password password-confirmation)
          (let [account
                (jdbc/execute-one!
                  (datasource)
                  (-> {:insert-into [:accounts]
                       :columns [:email :password]
                       :values [[email (bh/derive password)]]
                       :returning :*}
                      (sql/format))
                  {:builder-fn rs/as-unqualified-kebab-maps})]
            {:status 200
             :headers {"HX-Redirect" "/dashboard"}
             :session (select-keys (into {} account) [:email :created-at])})
          (-> (views/sign-up-form {:error "Passwords are not matching"})
              (ok)))))

    (GET "/dashboard" request
      (let [{:keys [session dependencies]} request
            {:keys [datasource]} dependencies]
        (if (seq session)
          (let [account (jdbc/execute-one!
                          (datasource)
                          (-> {:select :*
                               :from [:accounts]
                               :where [:= :email (:email session)]}
                              (sql/format))
                          {:builder-fn rs/as-unqualified-kebab-maps})]
            (-> (views/dashboard-page account)
                (layout {:title (page-title "Dashboard")})
                (ok)))
          (response/redirect "/sign-in"))))

    (POST "/logout" _
      {:status 200
       :headers {"HX-Redirect" "/"}
       :session nil})))

(defn wrap-dependencies
  [handler dependencies]
  (fn [request]
    (handler (assoc request :dependencies dependencies))))

(defrecord HttpServerComponent
  [config
   datasource]
  component/Lifecycle

  (start [component]
    (println "Starting HttpServerComponent")
    (let [server (jetty/run-jetty
                   (-> app-routes
                       (keyword-params/wrap-keyword-params)
                       (params/wrap-params)
                       (ring-session/wrap-session
                         {:store (jdbc-ring-session/jdbc-store
                                   (datasource)
                                   {:table :session_store})})
                       (wrap-dependencies component))
                   {:port (-> config :server :port)
                    :join? false})]
      (assoc component :server server)))

  (stop [component]
    (println "Stopping HttpServerComponent")
    (when-let [^Server server (:server component)]
      (.stop server))
    (assoc component :server nil)))

(defn new-http-server-component
  [config]
  (map->HttpServerComponent {:config config}))