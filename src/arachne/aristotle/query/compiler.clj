(ns arachne.aristotle.query.compiler
  (:require [arachne.aristotle.registry :as reg]
            [arachne.aristotle.graph :as graph]
            [arachne.aristotle.query.spec :as qs]
            [clojure.spec.alpha :as s]
            [clojure.core.match :as m]
            [clojure.walk :as w])
  (:import [org.apache.jena.graph NodeFactory Triple Node_Variable]
           [org.apache.jena.sparql.expr Expr NodeValue ExprVar ExprList E_GreaterThan]
           [org.apache.jena.sparql.core BasicPattern Var]
           [org.apache.jena.sparql.algebra.op OpBGP OpProject OpFilter OpDistinct]
           [org.apache.commons.lang3.reflect ConstructorUtils]
           [org.apache.jena.sparql.algebra OpAsQuery Algebra]))

(defn- replace-node
  "If the given data structure is a node, replace it with a Jena Node object,
   otherwise return it unchanged."
  [n]
  (m/match n
    [:variable s] (Var/alloc (graph/node s))
    [:literal l] (graph/node l)
    [:iri [_ iri]] (graph/node iri)
    :else n))

(defmulti compile-op
  "Compile an operation from a Clojure data structure to a Jena Op instance"
  :op)

(defmulti compile-fn-expr
  "Compile a function expression from a Clojure data structure to a Jena Expr instance"
  :operator
  :default ::default)

(defn compile-expr
  "Compile a Clojure data structure to a Jena Expr instance"
  [[type val]]
  (case type
    :node-expr (let [node (replace-node val)]
                 (if (instance? Node_Variable node)
                   (ExprVar. node)
                   (NodeValue/makeNode node)))
    :fn-expr (compile-fn-expr val)))

(defmethod compile-fn-expr ::default
  [{operator :operator args :args}]
  (let [args (map compile-expr args)
        clazz (qs/exprs operator)]
    (if clazz
      (ConstructorUtils/invokeConstructor clazz (into-array Object args))
      (throw (ex-info "bad" {}))
      )))

(defmethod compile-op :bgp
  [{triples :triples}]
  (->> triples
    (w/postwalk replace-node)
    (mapcat (fn [[type data]]
              (case type
                :map (graph/triples data)
                :single-triple [(graph/triple data)]
                :triples (map graph/triple data))))
    (BasicPattern/wrap)
    (OpBGP.)))

(defmethod compile-op :filter
  [{exprs :exprs child :child}]
  (OpFilter/filterBy
    (ExprList. (map compile-expr exprs))
    (compile-op child)))

(defmethod compile-op :project
  [{bindings :bindings child :child}]
  (OpProject. (compile-op child)
    (map #(Var/alloc (graph/node %)) bindings)))

(defmethod compile-op :distinct
  [{child :child}]
  (OpDistinct. (compile-op child)))

;;; Operations to implement
;OpConditional
;OpDatasetNames
;OpDiff
;OpDisjunction
;OpDistinctReduced
;OpExt
;OpExtend
;OpExtendAssign
;OpFilter
;OpGraph
;OpGroup
;OpJoin
;OpLabel
;OpLeftJoin
;OpList
;OpMinus
;OpModified
;OpNull
;OpOrder
;OpPath
;OpProcedure
;OpPropFunc
;OpQuad
;OpQuadBlock
;OpQuadPattern
;OpReduced
;OpSequence
;OpService
;OpSplice
;OpTable
;OpTopN
;OpTriple
;OpUnion

;; Exprs to implement
E_Add,
E_BNode,
E_Bound,
E_Call,
E_Cast,
E_Coalesce,
E_Conditional,
E_Datatype,
E_DateTimeDay,
E_DateTimeHours,
E_DateTimeMinutes,
E_DateTimeMonth, E_DateTimeSeconds, E_DateTimeTimezone, E_DateTimeTZ, E_DateTimeYear, E_Divide, E_Equals, E_Exists, E_Function, E_FunctionDynamic, E_GreaterThan, E_GreaterThanOrEqual, E_IRI, E_IsBlank, E_IsIRI, E_IsLiteral, E_IsNumeric, E_IsURI, E_Lang, E_LangMatches, E_LessThan, E_LessThanOrEqual, E_LogicalAnd, E_LogicalNot, E_LogicalOr, E_MD5, E_Multiply, E_NotEquals, E_NotExists, E_NotOneOf, E_Now, E_NumAbs, E_NumCeiling, E_NumFloor, E_NumRound, E_OneOf, E_OneOfBase, E_Random, E_Regex, E_SameTerm, E_SHA1, E_SHA224, E_SHA256, E_SHA384, E_SHA512, E_Str, E_StrAfter, E_StrBefore, E_StrConcat, E_StrContains, E_StrDatatype, E_StrEncodeForURI, E_StrEndsWith, E_StrLang, E_StrLength, E_StrLowerCase, E_StrReplace, E_StrStartsWith, E_StrSubstring, E_StrUpperCase, E_StrUUID, E_Subtract, E_UnaryMinus, E_UnaryPlus, E_URI, E_UUID, E_Version, ExprAggregator, ExprDigest, ExprFunction, ExprFunction0, ExprFunction1, ExprFunction2, ExprFunction3, ExprFunctionN, ExprFunctionOp, ExprNode, ExprNone, ExprSystem, ExprVar, NodeValue, NodeValueBoolean, NodeValueDecimal, NodeValueDouble, NodeValueDT, NodeValueDuration, NodeValueFloat, NodeValueInteger, NodeValueLang, NodeValueNode, NodeValueSortKey, NodeValueString
