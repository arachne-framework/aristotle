(ns arachne.aristotle.query
  (:require [arachne.aristotle.registry :as reg]
            [arachne.aristotle.query.compiler :as qc]
            [arachne.aristotle.graph :as graph]
            [clojure.spec.alpha :as s])
  (:import [org.apache.jena.query QueryFactory QueryExecutionFactory]
           [org.apache.jena.sparql.algebra AlgebraGenerator]))

(s/def ::fn-expr (s/cat :operator symbol? :args (s/* ::expr)))

(s/def ::expr (s/or :fn-expr ::fn-expr
                    :node-expr ::graph/node))

(s/def ::filter (s/coll-of ::expr :min-count 1))
(s/def ::project (s/coll-of ::graph/variable :min-count 1))
(s/def ::pattern ::graph/triples)

(s/def ::query (s/keys :req-un [::project ::pattern]
                       :opt-un [::filter]))

(defn- compile-query
  "Convert the given query from a conformed Clojure data structure to an ARQ
   Query object, using the specified sequence of compiler passes."
  [data passes]
  (reduce (fn [data pass]
            (pass data))
    data passes))

(s/fdef query
  :args (s/cat :query ::query))

(defn query
  "Build an ARQ Query object from a Clojure data structure"
  [query]
  (s/assert* ::query query)
  (compile-query (s/conform ::query query)
    [qc/replace-nodes
     qc/replace-exprs
     qc/pattern
     qc/add-filter
     qc/project]))

(comment

  (require '[arachne.aristotle.graph :as g])

  (reg/prefix :foaf "http://xmlns.com/foaf/0.1/")
  (reg/prefix :cfg "http://arachne-framework.org/config/")

  (def q2 '{:project [?luke]
            :pattern {:rdf/about ?luke
                      :foaf/name "Luke"
                      :foaf/friend {:foaf/name "Joe"}}
            :filter [(< 1 ?age)
                     (= ?luke :foaf/dob)]
            :values {?names #{"Joe" "Jimmy"}}

            })

  (def q '{:project [?person]
           :pattern [[?person :foaf/name "Luke"]
                     [?person :foaf/age 32]]
           :filter [(< 1 ?age)
                    (not= ?luke :foaf/dob)]
           ;:values {?names #{"Joe" "Jimmy"}}


           })

  (s/conform ::query q)

  (query q)

  (s/explain ::triple '[?person :foaf/name ?name])

  (s/conform ::node :foaf/name)

  )

(comment

  (def querystr "SELECT ?x ?y
                 WHERE { ?x <http://xmlns.com/foaf/0.1/name> ?y }")
  (def querystr " SELECT ?s { ?s <http://example.com/val> ?val .
                  FILTER ( ?val < 20 ) }")
  (def querystr "PREFIX prop: <http://resedia.org/ontology/>
                 PREFIX res: <http://resedia.org/resource/>
                 PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

                 SELECT DISTINCT ?language ?label
                 WHERE {?country prop:language ?language .
                        ?language rdfs:label ?label .
                 VALUES ?country { res:Spain res:France res:Italy }
                 FILTER langMatches(lang(?label), \"en\")}")


  (def querystr "CONSTRUCT ?name
                 WHERE { ?stmt <http://www.w3.org/1999/02/22-rdf-syntax-ns#predicate> <http://xmlns.com/foaf/0.1/name> .
                         ?stmt <http://www.w3.org/1999/02/22-rdf-syntax-ns#object> ?name .
                         }")

  (def querystr "PREFIX  rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \nPREFIX  foaf:   <http://xmlns.com/foaf/0.1/> \n\nSELECT ?person\nWHERE \n{\n    ?person rdf:type  foaf:Person .\n    FILTER NOT EXISTS { ?person foaf:name ?name }\n}  ")


  (def query (QueryFactory/create querystr))

  (-> (AlgebraGenerator.)
    (.compile query)
    ;(.getSubOp)
    ;(.getExprs)
    ;(.getList)
    ;first
    ;(.getArgs)
    )


  (.getResultVars query)

  (def resultmodel (.execConstruct (QueryExecutionFactory/create query model)))


  (with-open [execution (QueryExecutionFactory/create query model)]
    (doseq [soln (iterator-seq (.execSelect execution))]
      (println soln)
      )
    )



  )
