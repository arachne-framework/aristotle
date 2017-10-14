(ns arachne.aristotle
  "Primary API"
  (:require [arachne.aristotle.rdf :as rdf])
  (:import [org.apache.jena.rdf.model AnonId ModelFactory Statement Resource]
           [clojure.lang Keyword Symbol]
           [java.net URL URI]
           [org.apache.jena.datatypes.xsd XSDDatatype]
           [java.util GregorianCalendar]))


(defn rdf
  "Interprets the given Clojure data structure as RDF data and adds it to the given model."
  ([data]
   (rdf data (ModelFactory/createDefaultModel)))
  ([data model]
   (.add model (rdf/statements data model))))

(comment

  (reg/prefix :foaf "http://xmlns.com/foaf/0.1/")
  (reg/prefix :cfg "http://arachne-framework.org/config/")

  (def model (rdf [["http://example.com/person/luke":foaf/name "Luke VanderHart"]
                   ["http://example.com/person/luke":foaf/age 42.2M]
                   ]))

  (def model (rdf [{:rdf/about "http://example.com/person/luke"
                    :foaf/name "Luke"
                    :foaf/age 32
                    :foaf/numbers #{32 49 66}
                    :foaf/spouse
                    ^{::rdf/reified-subjects {:cfg/src-file "/foo/bar.clj"
                                              :cfg/line 42}
                      ::rdf/reified-objects [["http://example.com/person/luke"
                                              :cfg/claims]]}
                    {:rdf/about "http://hmv/me"
                     :foaf/name "Hannah"
                     :foaf/age 33}
                    :foaf/friends [{:rdf/about "http://alex/me"
                                    :foaf/name "Alex"}
                                   {:rdf/about "http://jaret/me"
                                    :foaf/name "Jaret"
                                    :foaf/friends {:rdf/about "http://example.com/person/luke"}}]
                    }]))

  (do
    (println "\n\n")
    (.write model System/out "N-TRIPLES")
    ;(.write model System/out "TURTLE")
    (println "\n\n"))

  )


;;;;;;;;;;;;  Data format

[
 [:_/subject ::pred "object"]
 [:_/subject2 ::pred2 42]

 ^{::reified-subjects {}}
 {:rdf/about "http://example.com/person/JohnSmith"
  ::fname "John"
  ::lname "Smith"

  }

 ]
