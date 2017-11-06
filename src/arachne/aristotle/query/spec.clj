(ns arachne.aristotle.query.spec
  (:require [clojure.spec.alpha :as s]
            [arachne.aristotle.graph :as graph]))

(defmulti op
  "Return the spec for an operation form"
  first)

(defmulti expr
  "Return the spec for an expression form"
  first)

(defmacro defop
  [name & regex-body]
  `(defmethod op ~name
     [_#]
     (s/cat :op keyword? ~@regex-body)))

(defmacro defexpr
  [name & regex-body]
  `(defmethod expr ~name
     [_#]
     (s/cat :operator symbol? ~@regex-body)))


(s/def ::expr (s/or :fn-expr (s/and list? (s/multi-spec expr identity))
                    :node-expr ::graph/node))

(s/def ::op (s/and vector? (s/multi-spec op identity)))


(defop :distinct :child ::op)
(defop :bgp :triples (s/+ ::graph/triples))
(defop :filter :exprs (s/+ ::expr) :child ::op)
(defop :project :bindings (s/coll-of ::graph/variable) :child ::op)



;; Simple exprs to implement
(def exprs
  {'= org.apache.jena.sparql.expr.E_Equals
   '> org.apache.jena.sparql.expr.E_GreaterThan
   '< org.apache.jena.sparql.expr.E_LessThan
   '>= org.apache.jena.sparql.expr.E_GreaterThanOrEqual
   '<= org.apache.jena.sparql.expr.E_LessThanOrEqual
   'not org.apache.jena.sparql.expr.E_LogicalNot
   'or org.apache.jena.sparql.expr.E_LogicalOr
   'and org.apache.jena.sparql.expr.E_LogicalAnd
   'not= org.apache.jena.sparql.expr.E_NotEquals})

(defmacro defexprs
  []
  `(doseq [op# (keys exprs)]
    (defexpr op# :args (s/+ ::expr))))

(defexprs)