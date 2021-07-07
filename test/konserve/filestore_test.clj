(ns konserve.filestore-test
  (:refer-clojure :exclude [get get-in update update-in assoc assoc-in dissoc exists? keys])
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [<!! go chan put! close!] :as async]
            [konserve.core :refer :all]
            [konserve.storage-layout :refer [-get-raw]]
            [konserve.compliance-test :refer [compliance-test]]
            [konserve.filestore :refer [new-fs-store delete-store]]))

(deftest filestore-test
  (testing "Test the file store functionality."
    (let [folder "/tmp/konserve-fs-test"
          _      (spit "/tmp/foo" (range 1 10))
          _      (delete-store folder)
          store  (<!! (new-fs-store folder))]
      (testing "compliance test"
        (compliance-test store))
      (testing "Binary"
        (testing "ByteArray"
          (let [res-ch (chan)]
            (is (= true (<!! (bassoc store :byte-array (byte-array (range 10))))))
            (is (= true (<!! (bget store :byte-array
                                   (fn [{:keys [input-stream]}]
                                     (go
                                       (put! res-ch (mapv byte (slurp input-stream)))))))))
            (is (=  (mapv byte (byte-array (range 10))) (<!! res-ch)))
            (close! res-ch)))
        (testing "CharArray"
          (let [res-ch (chan)]
            (is (= true (<!! (bassoc store :char-array (char-array "foo")))))
            (is (= true (<!! (bget store :char-array
                                   (fn [{:keys [input-stream]}]
                                     (go
                                       (put! res-ch (slurp input-stream))))))))
            (is (=  "foo" (<!! res-ch)))))
        (testing "File Inputstream"
          (let [res-ch (chan)]
            (spit "/tmp/foo" (range 1 10))
            (is (= true (<!! (bassoc store :file-input-stream (java.io.FileInputStream. "/tmp/foo")))))
            (is (= true (<!! (bget store :file-input-stream
                                   (fn [{:keys [input-stream]}]
                                     (go
                                       (put! res-ch (slurp input-stream))))))))
            (is (=  (str (range 1 10)) (<!! res-ch)))))
        (testing "Byte Array Inputstream"
          (let [res-ch (chan)]
            (is (= true (<!! (bassoc store :input-stream (java.io.ByteArrayInputStream. (byte-array (range 10)))))))
            (is (= true (<!! (bget store :input-stream
                                   (fn [{:keys [input-stream]}]
                                     (go
                                       (put! res-ch (map byte (slurp input-stream)))))))))
            (is (=  (map byte (byte-array (range 10))) (<!! res-ch)))
            (close! res-ch)))
        (testing "String"
          (let [res-ch (chan)]
            (is (= true (<!! (bassoc store :string "foo bar"))))
            (is (= true (<!! (bget store :string
                                   (fn [{:keys [input-stream]}]
                                     (go
                                       (put! res-ch (slurp input-stream))))))))
            (is (=  "foo bar" (<!! res-ch)))))
        (testing "Reader"
          (let [res-ch (chan)]
            (is (= true (<!! (bassoc store :reader (java.io.StringReader. "foo bar")))))
            (is (= true (<!! (bget store :reader
                                   (fn [{:keys [input-stream]}]
                                     (go
                                       (put! res-ch (slurp input-stream))))))))
            (is (=  "foo bar" (<!! res-ch))))))
      (delete-store folder)
      (let [store (<!! (new-fs-store folder))]
        (is (= (<!! (keys store))
               #{}))))))

(deftest filestore-layout1-test
  (testing "Test Layout 1."
    (let [folder "/tmp/konserve-fs-layout1-test"
          _      (delete-store folder)
          store  (<!! (new-fs-store folder))]
      (<!! (assoc-in store [:foo] 42))
      (let [header (take 8 (map byte (<!! (-get-raw store :foo))))
            [layout-id serializer-id compressor-id encryptor-id] header
            [_ _ _ _ _ _ _ metadata-size] header]
        (is (= [layout-id serializer-id compressor-id encryptor-id]
               [1 1 1 0]))
        (is (= metadata-size 78))))))
