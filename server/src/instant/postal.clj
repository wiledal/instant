(ns instant.postal
  (:require [clj-http.client :as http]
            [clojure.string :as string]
            [instant.config :as config]
            [instant.util.exception :as ex]
            [instant.util.json :refer [->json <-json]]))

(defn format-address [address]
  (if (map? address)
    (if-let [display-name (:name address)]
      (str display-name " <" (:email address) ">")
      (:email address))
    address))

(defn recipients [addresses]
  (mapv #(if (map? %) (:email %) %)
        (cond
          (nil? addresses) []
          (sequential? addresses) addresses
          :else [addresses])))

(defn message-body [{:keys [from to cc bcc subject html text reply-to]}]
  (cond-> {:from (format-address from)
           :to (recipients to)
           :subject subject}
    html (assoc :html_body html)
    text (assoc :plain_body text)
    cc (assoc :cc (recipients cc))
    bcc (assoc :bcc (recipients bcc))
    (or reply-to (config/email-reply-to))
    (assoc :reply_to (format-address (or reply-to (config/email-reply-to))))))

(defn send! [req]
  (when-not (config/postal-send-enabled?)
    (ex/throw-email-send-failed!
     "Postal is not configured. Set POSTAL_API_URL and POSTAL_API_KEY."))
  ;; Postal can report failure in a 200 response; both statuses must succeed.
  ;; Never attach the HTTP exception, which can contain the API key or login code.
  (let [result
        (try
          (let [response (http/post
                          (str (string/replace (config/postal-api-url) #"/+$" "")
                               "/api/v1/send/message")
                          {:headers {"X-Server-API-Key" (config/postal-api-key)
                                     "Content-Type" "application/json"}
                           :redirect-strategy :none
                           :throw-exceptions false
                           :conn-timeout 10000
                           :socket-timeout 10000
                           :connection-request-timeout 10000
                           :body (->json (message-body req))})
                body (<-json (:body response) true)]
            (when (and (<= 200 (:status response) 299)
                       (= "success" (:status body)))
              body))
          (catch Exception _ nil))]
    (if result
      (:data result)
      (ex/throw-email-send-failed! "We weren't able to send the email through Postal."))))
