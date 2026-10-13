(ns instant.storage.beta
  (:require [instant.flags :as flags]
            [instant.config :as config]
            [instant.util.exception :as ex]))

(defn assert-storage-configured! []
  (when-not (config/storage-enabled?)
    (ex/throw-validation-err!
     :storage nil
     [{:message "File storage is disabled. Configure S3 credentials or set INSTANT_STORAGE_ENABLED=true to use an AWS profile or IAM role."}])))

(defn assert-storage-enabled! [app-id]
  (assert-storage-configured!)
  (if (= :prod (config/get-env))
    (ex/assert-permitted! :storage-enabled? app-id (not (flags/storage-disabled? app-id)))
    true))
