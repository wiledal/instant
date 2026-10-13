(ns instant.storage.beta-test
  (:require [clojure.test :refer [deftest is testing]]
            [instant.config :as config]
            [instant.db.app-backup-jobs :as backup-jobs]
            [instant.flags :as flags]
            [instant.model.history :as history]
            [instant.storage.beta :as storage-beta]
            [instant.storage.coordinator :as coordinator]
            [instant.storage.s3 :as s3]
            [instant.storage.sweeper :as sweeper]
            [instant.util.exception :as ex]))

(deftest disabled-storage-test
  (with-redefs [config/storage-enabled? (constantly false)]
    (testing "storage operations fail before accessing S3 or the database"
      (doseq [operation [#(storage-beta/assert-storage-enabled! (random-uuid))
                         #(coordinator/upload-file! {} nil)
                         #(coordinator/create-upload-url! {})
                         #(coordinator/consume-upload-url! {} nil)
                         #(coordinator/create-download-url {})
                         #(backup-jobs/enqueue! {})
                         #(s3/s3-client)
                         #(s3/s3-async-client)
                         #(s3/s3-async-export-client)
                         #(s3/s3-transfer-manager)
                         #(s3/presign-creds)]]
        (try
          (operation)
          (is false "Expected storage-disabled error")
          (catch clojure.lang.ExceptionInfo e
            (is (= ::ex/validation-failed (::ex/type (ex-data e))))
            (is (re-find #"File storage is disabled" (.getMessage e)))))))
    (testing "the sweeper does not schedule work"
      (with-redefs [sweeper/schedule (atom nil)]
        (sweeper/start)
        (is (nil? @sweeper/schedule))))
    (testing "existing file metadata can be queried without signing URLs"
      (is (nil? (s3/create-signed-download-url! (random-uuid) "existing-file"))))
    (testing "WAL history stays in Postgres"
      (is (false? (history/store-to-s3?))))))

(deftest enabled-storage-test
  (with-redefs [config/storage-enabled? (constantly true)]
    (binding [config/*env* :dev]
      (is (true? (storage-beta/assert-storage-enabled! (random-uuid)))))
    (binding [config/*env* :prod]
      (with-redefs [flags/storage-disabled? (constantly true)]
        (is (thrown? clojure.lang.ExceptionInfo
                     (storage-beta/assert-storage-enabled! (random-uuid)))))
      (with-redefs [flags/storage-disabled? (constantly false)]
        (is (true? (storage-beta/assert-storage-enabled! (random-uuid))))))))
