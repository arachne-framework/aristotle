(ns arachne.aristotle.query.compiler
  (:require [arachne.aristotle.registry :as reg]
            [arachne.aristotle.graph :as graph]
            [clojure.spec.alpha :as s]
            [clojure.core.match :as m]
            [clojure.walk :as w])
  (:import [org.apache.jena.graph NodeFactory Triple Node_Variable]
           [org.apache.jena.sparql.expr Expr NodeValue ExprVar ExprList]
           [org.apache.jena.sparql.core BasicPattern Var]
           [org.apache.jena.sparql.algebra.op OpBGP OpProject OpFilter]))


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
  [:variable s] (graph/node s)
  [:literal l] (graph/node l)
  [:iri [_ iri]] (graph/node iri))

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
    (let [ctors (.getConstructors clazz)
          ctor (first (filter #(= (count args) (.getParameterCount %)) ctors))]
      (when-not ctor
        (throw (ex-info (format "No %s argument constructor found for %s, for operation '%s'"
                          (count args) clazz op) {:op op :args args :class clazz})))
      (.newInstance ctor (into-array Expr args)))
    (throw (ex-info (format "Expression operator %s is not implemented" op)
             {:op op :args args}))))

(defreplace replace-exprs
  "Replace expressions with Expr objects"
  [:node-expr node] (if (instance? Node_Variable node)
                      (ExprVar. node)
                      (NodeValue/makeNode node))
  [:fn-expr {:operator op
             :args args}] (construct-expr op args))

(defn pattern
  "Compiles the pattern to an OpBGP object and sets it as the primary operation"
  [q]
  (assoc q :op (->> (-> q :pattern second)
                 (graph/triples)
                 (BasicPattern/wrap)
                 (OpBGP.))))

(defn project
  "Wraps the primary operation in a OpProject operation"
  [q]
  (let [vars (->> q :project (map graph/node) (map #(Var/alloc %)))]
    (update q :op (fn [op]
                    (OpProject. op vars)))))

(defn add-filter
  "Takes the top level :op and wraps it in a Filter, if present"
  [q]
  (if-let [filter-exprs (:filter q)]
    (update q :op (fn [op]
                    (OpFilter/filterBy (ExprList. filter-exprs) op)))
    q))