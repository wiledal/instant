(ns instant.postal-test
  (:require [clojure.test :refer [deftest is testing]]
            [clj-http.client :as http]
            [instant.config :as config]
            [instant.email-router :as router]
            [instant.postal :as postal]
            [instant.util.json :refer [->json <-json]]))

(deftest postal-message-test
  (with-redefs [config/email-reply-to (constantly "support@hugos.computer")]
    (is (= {:from "Instant <verify@hugos.computer>"
            :to ["hugo@example.com"] :cc ["cc@example.com"]
            :bcc ["bcc@example.com"] :subject "Sign in"
            :html_body "<p>123456</p>" :plain_body "123456"
            :reply_to "support@hugos.computer"}
           (postal/message-body
            {:from {:name "Instant" :email "verify@hugos.computer"}
             :to {:email "hugo@example.com"} :cc ["cc@example.com"]
             :bcc [{:email "bcc@example.com"}] :subject "Sign in"
             :html "<p>123456</p>" :text "123456"}))))
  (with-redefs [config/email-reply-to (constantly nil)]
    (is (= {:from "from@example.com" :to ["to@example.com"] :subject "Hello"}
           (postal/message-body {:from "from@example.com"
                                 :to "to@example.com" :subject "Hello"})))))

(deftest postal-delivery-configuration-test
  (with-redefs [config/email-provider (constantly :postal)
                config/postal-send-enabled? (constantly true)
                config/postmark-send-enabled? (constantly false)]
    (is (true? (config/email-send-enabled?))))
  (with-redefs [config/email-provider (constantly :postal)
                config/postal-send-enabled? (constantly false)
                config/postmark-send-enabled? (constantly true)]
    (is (false? (config/email-send-enabled?)))))

(deftest postal-send-test
  (with-redefs [config/postal-send-enabled? (constantly true)
                config/postal-api-url (constantly "https://postal.on.hugos.computer/")
                config/postal-api-key (constantly "test-secret")
                config/email-reply-to (constantly nil)]
    (testing "explicit Postal provider authenticates and sends the expected payload"
      (with-redefs [config/email-provider (constantly :postal)
                    http/post (fn [url opts]
                                (is (= "https://postal.on.hugos.computer/api/v1/send/message" url))
                                (is (= "test-secret" (get-in opts [:headers "X-Server-API-Key"])))
                                (is (= :none (:redirect-strategy opts)))
                                (is (= ["to@example.com"] (:to (<-json (:body opts) true))))
                                {:status 200 :body (->json {:status "success" :data {:message_id "sent"}})})]
        (is (= {:message_id "sent"}
               (router/send-structured! {:to "to@example.com" :from "from@example.com"})))))
    (testing "HTTP failures, API failures, malformed replies and transport errors fail safely"
      (doseq [response [{:status 200 :body (->json {:status "error"})}
                        {:status 200 :body (->json {:status "parameter-error"})}
                        {:status 503 :body (->json {:status "success"})}
                        {:status 200 :body "not-json"}]]
        (with-redefs [http/post (constantly response)]
          (is (thrown? clojure.lang.ExceptionInfo (postal/send! {})))))
      (with-redefs [http/post (fn [& _] (throw (ex-info "test-secret" {:headers {"X-Server-API-Key" "test-secret"}})))]
        (try
          (postal/send! {})
          (is false "Expected email send failure")
          (catch clojure.lang.ExceptionInfo e
            (is (nil? (.getCause e)))
            (is (not (re-find #"test-secret" (str e (ex-data e))))))))))
  (testing "missing configuration fails before any HTTP request"
    (with-redefs [config/postal-send-enabled? (constantly false)
                  http/post (fn [& _] (is false "Unexpected HTTP request"))]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Postal is not configured"
                            (postal/send! {}))))))
