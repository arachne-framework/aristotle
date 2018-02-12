(ns arachne.aristotle.locks
  "Tools for performing thread-safe operations on a Model"
  (:import [org.apache.jena.shared Lock])
  (:refer-clojure :exclude [read write]))

(defn- lock-form
  [type lock body]
  `(let [^Lock lock# ~lock]
     (try
       (.enterCriticalSection lock# ~type)
       ~@body
       (finally
         (.leaveCriticalSection lock#)))))

(defmacro read
  "Execute the body with a read lock on the given lock."
  [lock & body]
  (lock-form Lock/READ lock body))

(defmacro write
  "Execute the body with a write lock on the given lock."
  [lock & body]
  (lock-form Lock/WRITE lock body))
