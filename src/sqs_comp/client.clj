(ns sqs-comp.client
  (:require [cemerick.bandalore :as sqs]
            [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [crap.control :refer [retry]]
            [crap.exceptions :refer [try+]])
  (:import [com.amazonaws.services.sqs.model QueueDoesNotExistException]
           [com.amazonaws AmazonServiceException]
           [java.net SocketException SocketTimeoutException]
           [javax.net.ssl SSLPeerUnverifiedException]
           [org.apache.http.conn HttpHostConnectException]))


(defn every-suffixed [haystack needle]
  (some true?
    (map #(.endsWith %1 needle) haystack)))

(defn list-queues [system]
  (let [client (:client system)]
    (sqs/list-queues (:client client))))

(defn has-queue? [system queue-name]
  (let [client (:client (:queue system))]
    (some true? (map #(.endsWith % queue-name)
                  (list-queues system)))))

(defprotocol SQSReader
  (create-queues [sqs-reader]
    "Create queues in configuration with the client.")

  (wait-for-queues [sqs-reader queues]
    "Waits until all the queues associated with this
SQSReader are created")

  (receive-events [sqs-reader]
    "Receive some events from the SQS queue")

  (send-message [sqs-reader event]
    "Send a message to SQS.")

  (delete-message [sqs-reader message]
    "Delete a message from the SQS queue")

  (delete-queue [sqs-reader queue]
    "Delete a queue from the SQS queue")

  (delete-all-queues [sqs-reader]
    "Delete a queue from the SQS queue"))

(defrecord SQSClient [config client incoming-queue]

  component/Lifecycle
  (start [sqs-client]
    (log/info "Starting new SQSClient with configuration:" config)
    (let [conf (:conf config)
          client (if (:using-iam conf)
                   (sqs/create-client)
                   (sqs/create-client
                     (:aws-id conf)
                     (:aws-secret conf)))
          sqs-client1 (assoc sqs-client :client client)
          incoming-queue (.create-queues sqs-client1)
          sqs-client2 (assoc sqs-client1
                        :incoming-queue incoming-queue)]
      (.wait-for-queues sqs-client2 [incoming-queue])
      sqs-client2))

  (stop [_]
    (log/info "Stopping SQSClient"))

  SQSReader
  (create-queues [sqs-reader]
    (sqs/create-queue
      client
      (:incoming-events-queue-name (:conf config))))

  (wait-for-queues [sqs-reader queues]
    (retry {:retries 50 :wait-period 1000}
      (let [known-queues (sqs/list-queues client)]
        (log/info "Queues missing, waiting until they exist" queues)
        (every? true?
          (map #(every-suffixed known-queues %) queues)))))

  (receive-events [sqs-reader]
    (try+
      (let [msgs (sqs/receive client incoming-queue)]
        (when (seq msgs)
          msgs))
      (catch (HttpHostConnectException SSLPeerUnverifiedException
               SocketException SocketTimeoutException
               AmazonServiceException) e
        (log/error "Exception receiving events:" (str e)))))

  (send-message [sqs-reader event]
    (sqs/send client incoming-queue event))

  (delete-queue [sqs-reader queue]
    (try
      (sqs/delete-queue client queue)
      (catch QueueDoesNotExistException e
        nil)))

  (delete-all-queues [sqs-reader]
    (mapv #(.delete-queue sqs-reader %)
      (sqs/list-queues client)))

  (delete-message [sqs-reader message]
    (log/debug "Deleting event:" (:id message))
    (sqs/delete client message)))

(defn make-sqs-client
  "Creates a new SQSClient with default parameters"
  []
  (->SQSClient nil nil nil))
