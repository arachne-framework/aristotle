(ns arachne.aristotle.validation
  "Utils for returning inference validation errors in a consistent way"
  (:require [arachne.aristotle :as a]
            [arachne.aristotle.graph :as g]
            [arachne.aristotle.registry :as reg]
            [arachne.aristotle.query :as q])
  (:import [org.apache.jena.rdf.model InfModel]
           [org.apache.jena.reasoner ValidityReport ValidityReport$Report])
  )


(defn built-in
  "Validate using Jena's built in inference validation."
  [^InfModel m]
  (let [r (.validate m)]
    (if (.isValid r)
      []
      (map (fn [^ValidityReport$Report r]
             {::error? (boolean (.isError r))
              ::type :inference
              ::jena-type (.getType r)
              ::description (.getDescription r)})
           (iterator-seq (.getReports r))))))

(defn functional-properties
  [m]
  (let [errors (q/query '[:filter (< 1 ?count)
                          [:group [?prop ?obj] [?count (count ?val)]
                           [:bgp
                            [?prop :owl/class :owl/FunctionalProperty]
                            [?obj ?prop ?val]]]] m)]
    (map (fn [{prop '?prop obj '?obj}]
           {::error? true
            ::type :functional-property-violation
            ::description (format "Functional property violation on property %s, for subject %s"
                                  prop obj)
            ::info {:property prop
                    :subject obj}})
         errors)))

(def ^:dynamic *validators* #{built-in functional-properties})

(defn validate
  "Validate the given model, returning a sequence of validation errors or warnings"
  [m]
  (mapcat #(% m) *validators*))
