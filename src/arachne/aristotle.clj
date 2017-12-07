(ns arachne.aristotle
  "Primary API"
  (:require [arachne.aristotle.graph :as g]
            [arachne.aristotle.query :as q]
            [arachne.aristotle.registry :as reg]))

;; registry, expressing RDF as EDN
;; sparql via datomic-like query

;; reasoners, querying reasoners via sparql
