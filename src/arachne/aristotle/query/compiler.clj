(ns arachne.aristotle.query.compiler
  (:require [arachne.aristotle.registry :as reg]
            [arachne.aristotle.graph :as graph]
            [clojure.spec.alpha :as s]
            [clojure.core.match :as m]
            [clojure.walk :as w])
  (:import [org.apache.jena.graph NodeFactory Triple Node_Variable]
           [org.apache.jena.sparql.expr Expr NodeValue ExprVar ExprList]
           [org.apache.jena.sparql.core BasicPattern Var]
           [org.apache.jena.sparql.algebra.op OpBGP OpProject OpFilter OpDistinct]
           [org.apache.commons.lang3.reflect ConstructorUtils]
           [org.apache.jena.sparql.algebra OpAsQuery Algebra]))


(defmacro defreplace
  "Define a function that walks its input and replaces nodes matching the
   given core.match patterns"
  [name docstr & patterns]
  `(defn ~name ~docstr [data#]
     (w/postwalk (fn [node#]
                  (m/match node#
                    ~@patterns
                    :else node#))
       data#)))

(defreplace replace-nodes
  "Replace Nodes with node values"
  [:variable s] (Var/alloc (graph/node s))
  [:literal l] (graph/node l)
  [:iri [_ iri]] (graph/node iri))

(defreplace replace-triples
  "Replace triple-able objects with Triples"
  [:triples [:map m]] (graph/triples m)
  [:triples [:single-triple t]] [(graph/triple t)]
  [:triples [:triples ts]] (map graph/triple ts))

(def op-classes
  {'= org.apache.jena.sparql.expr.E_Equals
   '> org.apache.jena.sparql.expr.E_GreaterThan
   '< org.apache.jena.sparql.expr.E_LessThan
   '>= org.apache.jena.sparql.expr.E_GreaterThanOrEqual
   '<= org.apache.jena.sparql.expr.E_LessThanOrEqual
   'not org.apache.jena.sparql.expr.E_LogicalNot
   'or org.apache.jena.sparql.expr.E_LogicalOr
   'and org.apache.jena.sparql.expr.E_LogicalAnd
   'not= org.apache.jena.sparql.expr.E_NotEquals

   })

(defn- construct-expr
  "Construct an Expression by reflectively constructing a class instance,
   according to the mapping defined in op-classes."
  [op args]
  (if-let [clazz (op-classes op)]
    (ConstructorUtils/invokeConstructor clazz (into-array Object args))
    (throw (ex-info (format "Expression operator %s is not implemented" op)
             {:op op :args args}))))

(defreplace replace-exprs
  "Replace expressions with Expr objects"
  [:node-expr node] (if (instance? Node_Variable node)
                      (ExprVar. node)
                      (NodeValue/makeNode node))
  [:filter [:fn-expr {:operator op
                      :args args}]] (construct-expr op args))

(defn bgp
  "Compiles the pattern to an OpBGP object and sets it as the primary operation"
  [q]
  (assoc q :op (->> q
                 :triples
                 (apply concat)
                 (BasicPattern/wrap)
                 (OpBGP.))))

(defn project
  "Wraps the primary operation in a OpProject operation"
  [q]
  (let [vars (or (:select-distinct q) (:select q))
        vars (->> vars (map graph/node) (map #(Var/alloc %)))]
    (update q :op (fn [op]
                    (let [op (OpProject. op vars)]
                      (if (:select-distinct q)
                        (OpDistinct. op)
                        op))))))

(defn split-where
  "Takes the top level :where key and splits it into two top-level keys, :triples and :filter"
  [q]
  (-> q
    (dissoc :where)
    (merge (group-by first (-> q :where)))))

(defn add-filter
  "Wraps the top-level op in an OpFilter"
  [q]
  (let [filter-exprs (:filter q)]
    (if (empty? filter-exprs)
      q
      (update q :op (fn [op]
                      (OpFilter/filterBy (ExprList. filter-exprs) op))))))

(defn optimize
  "Automatically optimize the query using Jena's built-in optimizer"
  [q]
  (update q :op #(Algebra/optimize %)))
