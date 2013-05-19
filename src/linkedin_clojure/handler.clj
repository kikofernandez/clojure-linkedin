(ns linkedin-clojure.handler
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [hiccup.page :as hpage]
            [hiccup.form :as form]
            [cheshire.core :as parse]
            [ring.util.response :as response]
            [clj-http.client :as client]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; CONSTANTS
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def ENV "http://localhost:3000")
(def API_KEY "WRITE_YOUR_API_KEY_HERE")
(def SECRET_KEY "WRITE_YOUR_SECRET_KEY_HERE")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; IN MEMORY
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def lIn-user (atom {:first_name "" :last_name ""}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; LOGIC
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- login-view [error]
  (hpage/html5
   [:div
    (if (= error "access_denied")
      [:h1 "We feel rejected... sniff sniff"])
    (form/form-to [:post (str "https://www.linkedin.com/uas/oauth2/authorization?"
                              "response_type=code"
                              "&client_id=" API_KEY
                              "&scope=r_network%20r_fullprofile%20r_emailaddress"
                              "&state=DCEEFWF45453sdffef424111234"
                              "&redirect_uri=" ENV)]
                  (form/submit-button "Sign-In LinkedIn"))]))

(defn- welcome-page
  [user]
  (str "Hello there buddy, your name is " (user :first_name) " " (user :last_name)) )

(defn- request-access-token
  "Request the access token and return it."
  [authorization_token]
  (-> (client/post (str "https://www.linkedin.com/uas/oauth2/accessToken?"
                        "grant_type=authorization_code"
                        "&code=" authorization_token
                        "&redirect_uri=" ENV
                        "&client_id=" API_KEY
                        "&client_secret=" SECRET_KEY))
      :body
      (parse/parse-string)
      (get "access_token")))

(defn- simple-request
  "This is just a simple request to the LinkedIn API,
  where we are going to get our profile data."
  [access_token]
  (let [{:strs [firstName lastName headline siteStandardProfileRequest]}
        (-> (client/get (str "https://api.linkedin.com/v1/people/~"
                             "?oauth2_access_token=" access_token
                             "&format=json"))
            :body
            (parse/parse-string))]
    (swap! lIn-user #(assoc % :first_name %2 :last_name %3) firstName lastName)
    (response/redirect "/welcome")))

(defn- login
  "Login view, if we get back some query params from
  LinkedIn, then we continue request the access token, and publishing
  my profile data, otherwise show the LinkedIn Sign In button"
  [params]
  (if-let [authorization_token (params "code")]
    (let [access_token (request-access-token authorization_token)]
      (simple-request access_token))
    (let [error (params "error")]
      (login-view error))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; ROUTES
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defroutes app-routes
  (GET "/" {params :query-params} (login params))
  (GET "/welcome" [] (welcome-page @lIn-user))
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (handler/site app-routes))
