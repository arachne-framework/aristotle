(ns arachne.aristotle.query.compiler
  (:require [arachne.aristotle.registry :as reg]
            [arachne.aristotle.graph :as graph]
            [clojure.spec.alpha :as s]
            [arachne.aristotle.graph :as g])
  (:import [org.apache.jena.graph Node NodeFactory Triple Node_Variable Node_Blank]
           [org.apache.jena.sparql.expr Expr NodeValue ExprVar ExprList E_GreaterThan E_Equals E_LessThan E_GreaterThanOrEqual E_LogicalNot E_LogicalAnd E_LogicalOr E_NotEquals E_LessThanOrEqual E_BNode E_Bound E_Conditional E_Datatype E_DateTimeDay E_DateTimeHours E_DateTimeMinutes E_DateTimeMonth E_DateTimeSeconds E_DateTimeTimezone E_DateTimeYear E_Divide E_Exists E_IRI E_IsIRI E_IsBlank E_IsLiteral E_IsNumeric E_IsURI E_Add E_Lang E_LangMatches E_MD5 E_Multiply E_Subtract E_Now E_NumAbs E_NumCeiling E_NumFloor E_NumRound E_Random E_Regex E_SameTerm E_Str E_SHA1 E_SHA224 E_SHA256 E_SHA384 E_SHA512 E_StrAfter E_StrBefore E_StrConcat E_StrContains E_StrDatatype E_StrLength E_StrEndsWith E_StrStartsWith E_StrLang E_StrSubstring E_StrUpperCase E_StrUUID E_StrLowerCase E_UnaryPlus E_UnaryMinus E_URI E_Version E_UUID E_StrEncodeForURI E_StrReplace E_Coalesce E_OneOf E_NotOneOf E_Function E_NotExists ExprAggregator]
           [org.apache.jena.sparql.core BasicPattern Var VarExprList QuadPattern Quad]
           [org.apache.commons.lang3.reflect ConstructorUtils]
           [org.apache.jena.sparql.algebra OpAsQuery Algebra Table Op]
           [org.apache.jena.sparql.algebra.table Table1 TableN]
           [org.apache.jena.sparql.algebra.op OpDistinct OpProject OpFilter OpBGP OpConditional OpDatasetNames OpDiff OpDisjunction OpDistinctReduced OpExtend OpGraph OpGroup OpJoin OpLabel OpLeftJoin OpList OpMinus OpNull OpOrder OpQuad OpQuadBlock OpQuadPattern OpReduced OpSequence OpSlice OpTopN OpUnion OpTable]
           [org.apache.jena.sparql.expr.aggregate AggCount$AccCount AggSum AggAvg AggMin AggMax AggGroupConcat$AccGroupConcat AggSample$AccSample AggGroupConcat AggCount AggCountVar AggCountDistinct AggCountVarDistinct AggSample]
           [org.apache.jena.query SortCondition]
           [org.apache.jena.sparql.engine.binding BindingBuilder]
           [org.apache.jena.sparql.core Var]
           [java.util List]))

(defn- replace-vars
  "Given a collection of Triples, mutate the triples to replace Node_variable
   objects with Var objects."
  [triples]
  (let [bnodes (atom {})
        update-bnodes (fn [bnodes id]
                        (if (bnodes id)
                          bnodes
                          (assoc bnodes id (Var/alloc ^String (str "?" (count bnodes))))))
        replace (fn [node]
                  (cond
                    (instance? Node_Variable node) (Var/alloc ^Node_Variable node)
                    (instance? Node_Blank node)
                    (let [id     (.getBlankNodeLabel ^Node_Blank node)
                          bnodes (swap! bnodes update-bnodes id)]
                      (bnodes id))
                    :else node))]
    (for [^Triple triple triples]
      (Triple/create
        (replace (.getSubject triple))
        (replace (.getPredicate triple))
        (replace (.getObject triple))))))

(defn- triples
  "Convert the given Clojure data structure to a set of Jena triples"
  [data]
  (s/assert* ::graph/triples data)
  (replace-vars (graph/triples data)))

(defn var-seq
  "Convert a seq of variable names to a list of Var nodes"
  [s]
  (mapv #(Var/alloc (graph/node %)) s))

(declare op)
(declare expr)
(declare aggregator)

(defn- var-expr-list
  "Given a vector of var/expr bindings (reminiscient of Clojure's `let`), return a Jena VarExprList with vars and exprs."
  [bindings]
  (let [vel (VarExprList.)]
    (doseq [[v e] (partition 2 bindings)]
      (.add vel (Var/alloc (graph/node v)) (expr e)))
    vel))

(defn- var-aggr-list
  "Given a vector of var/aggregate bindings return a Jena VarExprList with
   vars and aggregates"
  [bindings]
  (vec (for [[v e] (partition 2 bindings)]
         (ExprAggregator. (Var/alloc (graph/node v)) (aggregator e)))))

(defn- sort-conditions
  "Given a seq of expressions and the keyword :asc or :desc, return a list of
   sort conditions."
  [conditions]
  (for [[e dir] (partition 2 conditions)]
    (SortCondition. ^Expr (expr e) (if (= :asc dir) 1 -1))))

(defn- quad-pattern
  "Parse the given Clojure data structure into a Jena QuadPattern object"
  ^QuadPattern [quads]
  (let [qp (QuadPattern.)]
    (doseq [[g s p o] quads]
      (let [quad (Quad. (g/node g) (g/triple s p o))]
        (.add qp quad)))
    qp))

(defn aggregator
  "Convert a Clojure data structure representing an aggregation expression to
   a Jena Aggregator object"
  [[op & [a1 a2 & _ :as args]]]
  (case op
    count (cond
            (symbol? a1)
            (AggCountVar. (expr a1))

            (and (seq? a1) (= 'distinct (first a1)) (and (= 1 (count a1))))
            (AggCountDistinct.)

            (and (seq? a1) (= 'distinct (first a1)) (and (= 2 (count a1))))
            (AggCountVarDistinct. (expr (second a1)))

            :else (AggCount.))
    sum (AggSum. (expr a1))
    avg (AggAvg. (expr a1))
    min (AggMin. (expr a1))
    max (AggMax. (expr a1))
    group-concat (AggGroupConcat. (expr a1) a2)
    sample (AggSample. a1)))

(defn- table-bindings
  "Add a bindings map entry to the given table"
  [^TableN t [k v]]
  (if (coll? v)
    (doseq [node v]
      (let [bb (BindingBuilder/create)]
        (if (coll? k)
          (mapv #(when %2 (.add bb (Var/alloc (graph/node %1)) (graph/node %2))) k node)
          (.add bb (Var/alloc (graph/node k)) (graph/node node)))
        (.addBinding t (.build bb))))
    (let [binding (BindingBuilder/create)]
      (.add binding (Var/alloc (graph/node k)) (graph/node v))
      (.addBinding t (.build binding)))))

(defn build-table
  "Given a bindings map, return an OpSequence including a nested table
  for each map."
  [bm]
  (->> bm
       (map #(let [t (TableN.)]
               (table-bindings t %)
               (OpTable/create t)))
       (reduce (fn [op t]
                 (OpSequence/create op t)))))

(defn op
  "Convert a Clojure data structure to an Arq Op"
  ^Op
  [[op-name & [a1 a2 & amore :as args]]]
  (case op-name
    :table (build-table a1)
    :distinct (OpDistinct/create (op a1))
    :project (OpProject. (op a2) (var-seq a1))
    :filter (OpFilter/filterBy (ExprList. ^List (map expr (butlast args))) (op (last args)))
    :bgp (OpBGP. (BasicPattern/wrap (mapcat triples args)))
    :conditional (OpConditional. (op a1) (op a2))
    :dataset-names (OpDatasetNames. (graph/node a1))
    :diff (OpDiff/create (op a1) (op a2))
    :disjunction (OpDisjunction/create (op a1) (op a2))
    :extend (OpExtend/create (op a2) (var-expr-list a1))
    :graph (OpGraph. (graph/node a1) (op a2))
    :group (OpGroup/create (op (first amore))
                           (reduce (fn [^VarExprList o v]
                                     (doto o
                                       (.add v)))
                                   (VarExprList.) (var-seq a1))
                           (var-aggr-list a2))
    :join (OpJoin/create (op a1) (op a2))
    :label (OpLabel/create a1 (op a2))
    :left-join (OpLeftJoin/create (op a1) (op a2) (ExprList. ^List (map expr amore)))
    :list (OpList. (op a1))
    :minus (OpMinus/create (op a1) (op a2))
    :null (OpNull/create)
    :order (OpOrder. (op a2) (sort-conditions a1))
    :quad (OpQuad. (.get (quad-pattern args) 0))
    :quad-block (OpQuadBlock. (quad-pattern args))
    :quad-pattern (OpQuadPattern. (graph/node a1)
                    (BasicPattern/wrap (mapcat triples (rest args))))
    :reduced (OpReduced/create (op a1))
    :sequence (OpSequence/create (op a1) (op a2))
    :slice (OpSlice. (op a1) (long a1) (long (first amore)))
    :top-n (OpTopN. (op (first amore)) (long a1) (sort-conditions a2))
    :union (OpUnion. (op a1) (op a2))
    :service (throw (ex-info "SPARQL federated queries not yet supported" {}))
    :path (throw (ex-info "SPARQL property paths not yet supported" {}))
    (throw (ex-info (str "Unknown operation " op-name) {:op-name op-name
                                                        :args args}))))


;;https://github.com/apache/jena/blob/master/jena-extras/jena-querybuilder/src/main/java/org/apache/jena/arq/querybuilder/ExprFactory.java

(def expr-class
  "Simple expressions that resolve to a class which takes Exprs in its
   constructor"
  {'*           E_Multiply
   '/           E_Divide
   '<           E_LessThan
   '<=          E_LessThanOrEqual
   '=           E_Equals
   '>           E_GreaterThan
   '>=          E_GreaterThanOrEqual
   'abs         E_NumAbs
   'and         E_LogicalAnd
   'bnode       E_BNode
   'bound       E_Bound
   'ceil        E_NumCeiling
   'concat      E_StrConcat
   'contains    E_StrContains
   'datatype    E_Datatype
   'day         E_DateTimeDay
   'encode      E_StrEncodeForURI
   'floor       E_NumFloor
   'hours       E_DateTimeHours
   'if          E_Conditional
   'iri         E_IRI
   'uri         E_URI
   'isBlank     E_IsBlank
   'isIRI       E_IsIRI
   'isURI       E_IsURI
   'isLiteral   E_IsLiteral
   'isNumeric   E_IsNumeric
   'lang        E_Lang
   'langMatches E_LangMatches
   'lcase       E_StrLowerCase
   'md5         E_MD5
   'minutes     E_DateTimeMinutes
   'month       E_DateTimeMonth
   'not         E_LogicalNot
   'not=        E_NotEquals
   'now         E_Now
   'or          E_LogicalOr
   'rand        E_Random
   'regex       E_Regex
   'replace     E_StrReplace
   'round       E_NumRound
   'sameTerm    E_SameTerm
   'seconds     E_DateTimeSeconds
   'sha1        E_SHA1
   'sha224      E_SHA224
   'sha256      E_SHA256
   'sha384      E_SHA384
   'sha512      E_SHA512
   'str         E_Str
   'strafter    E_StrAfter
   'strbefore   E_StrBefore
   'strdt       E_StrDatatype
   'strends     E_StrEndsWith
   'strlang     E_StrLang
   'strlen      E_StrLength
   'strstarts   E_StrStartsWith
   'struuid     E_StrUUID
   'substr      E_StrSubstring
   'timezone    E_DateTimeTimezone
   'tz          E_DateTimeTimezone
   'ucase       E_StrUpperCase
   'uuid        E_UUID
   'version     E_Version
   'year        E_DateTimeYear})


(defn composite-expr
  "Convert a Clojure data structure representing a expression to an Arq Expr"
  [[f & args]]
  (cond
    (= f 'exists) (E_Exists. (op (first args)))
    (= f 'not-exists) (E_NotExists. (op (first args)))
    :else
    (let [args (map expr args)
          clazz (get expr-class f)]
      (cond
        clazz (ConstructorUtils/invokeConstructor clazz (into-array Object args))
        (= f '+) (if (= 1 (count args))
                   (E_UnaryPlus. (first args))
                   (E_Add. (first args) (second args)))
        (= f '-) (if (= 1 (count args))
                   (E_UnaryMinus. (first args))
                   (E_Subtract. (first args) (second args)))
        (= f 'coalesce) (E_Coalesce. (ExprList. ^List args))
        (= f 'in) (E_OneOf. (first args) (ExprList. ^List (rest args)))
        (= f 'not-in) (E_NotOneOf. (first args) (ExprList. ^List (rest args)))

        (s/valid? ::graph/iri f) (E_Function. (.getURI (graph/node f)) (ExprList. ^List args))

        :else (throw (ex-info (str "Unknown expression type " f) {:expr f
                                                                  :args args}))))))


(defn expr
  "Convert a Clojure data structure to an Arq Expr"
  [expr]
  (if (instance? java.util.List expr)
    (composite-expr expr)
    (let [node (graph/node expr)]
      (if (instance? Node_Variable node)
        (ExprVar. (Var/alloc ^Node_Variable node))
        (NodeValue/makeNode node)))))

(comment
  (require 'arachne.aristotle.query.spec)
  (s/conform :arachne.aristotle.query.spec/expr '(< 105000 (:xsd/integer ?pop)))
  #_.)
