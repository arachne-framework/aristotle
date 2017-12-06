(ns arachne.aristotle.query
  (:require [arachne.aristotle.registry :as reg]
            [arachne.aristotle.query.compiler :as qc]
            [arachne.aristotle.graph :as graph]
            [clojure.spec.alpha :as s])
  (:import [org.apache.jena.query QueryFactory QueryExecutionFactory]
           [org.apache.jena.sparql.algebra AlgebraGenerator Algebra OpAsQuery]
           [org.apache.jena.sparql.algebra.op OpProject Op1]))

(defn- find-vars
  "Unwrap the given operation until we find an OpProject, then return the list of vars."
  [op]
  (cond
    (instance? OpProject op) (.getVars op)
    (instance? Op1 op) (recur (.getSubOp op))
    :else (throw (ex-info (format "Could not extract vars from operation of type %s"
                            (.getClass op)) {:op op}))))

(defn run
  "Given an input dataset (which may be a Graph or a Model) and an Operation,
   evaluate the query and return the results as a realized Clojure data structure."
  [data op]
  (let [vars (find-vars op)]
    (->> (Algebra/exec op data)
      (iterator-seq)
      (map (fn [binding]
               (mapv #(graph/data (.get binding %)) vars)))
      (doall))))

(defn build
  "Build a Jena Operation object from the given query, represented as a
   Clojure data structure"
  [query]
  (let [op (qc/op query)
        op (Algebra/optimize op)]
    op))

(defn sparql
  "Return a SPARQL query string for the given Jena Operation (as returned from `build`).
  Useful mostly for debugging."
  [op]
  (OpAsQuery/asQuery op))

(defn query
  "Build and execute a query on the given dataset."
  [query dataset]
  (run dataset (build query)))

(comment

  (require '[arachne.aristotle.graph :as g])

  (reg/prefix :foaf "http://xmlns.com/foaf/0.1/")
  (reg/prefix :cfg "http://arachne-framework.org/config/")

  (def q '[:distinct
           [:project [?name ?age]
            [:filter (<= 21 ?age)
             [:bgp [youngling :foaf/age ?age]
              {:rdf/about youngling
               :foaf/knows {:foaf/name ?name}}]]]])

  (def q '[:filter (<= ?a ?b)
           [:filter (< ?a 42)

            [:bgp [[?a :foaf/name ?b]]

             ]]
           ])

  (def q '[:project [?a]
           [:bgp [[?a :foaf/name ?b]]
              [?a :foaf/test ?b]
              {:rdf/about ?a
               :foaf/knows "<http://example.com/person/joe>"
               :foaf/age 32}]])

  (def q '[:project [?a]
           [:quad-block
            [?x ?a :foaf/name ?name]
            [?x ?a :foaf/age ?age]
            ]])

  (def q '[:project [?name-count]
           [:bgp {:rdf/about ?p
                  :rdf/name ?name}]])

  ;(s/conform ::qs/op q)

  (sparql (build q))

  (.getVars (build q))

  (def x (build q))

  (def v (.getVars x))

  )

(comment

  (def querystr "SELECT DISTINCT ?x ?y
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


  (def querystr "SELECT DISTINCT ?x ?y
                 WHERE { ?x <http://xmlns.com/foaf/0.1/name> ?y
                         FILTER NOT EXISTS {?x <http://xmlns.com/foaf/0.1/age> ?age}
                         }")

  (def querystr "SELECT ?x (<java:foo.inc>(?y) AS ?iy)
                 WHERE {
                   ?x <http://xmlns.com/foaf/0.1/name> ?y .
                }

  ")

  (def querystr "SELECT ?x (str(?y) AS ?iy)
                 WHERE {
                   ?x <http://xmlns.com/foaf/0.1/name> ?y .
                }


  ")


  (def querystr "SELECT ?group1 ?group2
                 (COUNT(?var1) AS ?var2)
                  (GROUP_CONCAT (DISTINCT ?var3; SEPARATOR=\", \") AS ?var4)
                     WHERE
                     {
                       ?var3 <http://xmlns.com/foaf/0.1/name> ?y .
                     }
                  GROUP BY ?group1 ?group2")

  (def querystr "SELECT ?group1 ?group2 (COUNT(?var1) AS ?var2)
                 WHERE {
                     ?var1 <http://xmlns.com/foaf/0.1/name> ?var4 .
                 }
                 GROUP BY ?group1 ?group2
                 ")

  (def qq (QueryFactory/create querystr))


  (def op
    (-> (AlgebraGenerator.)
      (.compile qq)
      ;(.getSubOp)
      ;(.getExprs)
      ;(.getList)
      ;first
      ;(.getArgs)
      )
    )


  (.getResultVars query)

  (def resultmodel (.execConstruct (QueryExecutionFactory/create query model)))


  (with-open [execution (QueryExecutionFactory/create query model)]
    (doseq [soln (iterator-seq (.execSelect execution))]
      (println soln)
      )
    )



  )
