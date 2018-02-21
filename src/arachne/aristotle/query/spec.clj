(ns arachne.aristotle.query.spec
  (:require [clojure.spec.alpha :as s]
            [arachne.aristotle.graph :as g]))

(defmacro defd
  "Spec def, with a docstring.

   Docstring is currently ignored."
  [name docstr & body]
  `(s/def ~name ~@body))

(defd ::operation
  "A SPARQL algebra operation, which may be one of a variety of
  types."
  (s/or :bgp ::bgp
        :table ::table
        :distinct ::distinct
        :project ::project
        :filter ::filter
        :conditional ::conditional
        :dataset-names ::dataset-names
        :diff ::diff
        :disjunction ::disjunction
        :extend ::extend
        :graph ::graph
        :group ::group
        :join ::join
        :label ::label
        :left-join ::left-join
        :list ::list
        :minus ::minus
        :null ::null
        :order ::order
        :quad ::quad
        :quad-block ::quad-block
        :quad-pattern ::quad-pattern
        :reduced ::reduced
        :sequence ::sequence
        :slice ::slice
        :top-n ::top-n
        :union ::union))

;; Data Structures

(defd ::bindings
  "Var bindings used by :table op and as input bindings for queries."
  (s/coll-of
   (s/or :var->value (s/tuple ::g/variable (complement coll?))
         :var->values (s/tuple ::g/variable
                               (s/coll-of (complement coll?)))
         :vars->values (s/tuple (s/coll-of ::g/variable :kind vector?)
                                (s/coll-of (s/coll-of (complement coll?) :kind vector?)
                                           :kind vector?)))
   :into #{}))

(defd ::var-set
  "A specific set of logic variables."
  (s/coll-of ::g/variable :min-count 0))

(defd ::var-expr-list
  "A list of alternating var/expr bindings (similar to Clojure's
  `let`)"
  (s/cat :pairs (s/+ (s/cat :var ::g/variable :expr ::expr))))

(defd ::var-aggr-list
  "A list of alternating var/aggregate bindings (similar to Clojure's
  `let`)"
  (s/cat :pairs (s/+ (s/cat :var ::g/variable :aggr ::agg-expr))))


(defd ::sort-conditions
  "A list of alternating expresion/direction pairs."
  (s/cat :pairs (s/+ (s/cat :expr ::expr :direction #{:asc :desc}))))

(defd ::quad
  "Quad represented as a 4-tuple"
  (s/tuple ::g/node ::g/node ::g/node ::g/node))

;; Operations

(defd ::bgp
  "A basic graph pattern. Multiple triples that will be matched
  against the data store."
  (s/cat :op #{:bgp} :triples (s/+ ::g/triples)))

(defd ::table
  "Introduces a tabled set of possible bindings. Corresponds to VALUES
  clause in SPARQL. Each binding map entry corresponds to a separate
  underlying TableOp, combined using a sequence."
  (s/cat :op #{:table} :map ::bindings-map))

(defd ::distinct
  "Removes duplicate solutions from the solution set. Corresponds to
  SPARQL's DISTINCT keyword."
  (s/cat :op #{:distinct} :child ::operation))

(defd ::project
  "Retains only some of the variables in the solution set. Corresponds
  to SPARQL's SELECT clause."
  (s/cat :op #{:project} :vars ::var-set :child ::operation))

(defd ::filter
  "Filters results based on an expression. Corresponds to SPARQL's
  FILTER."
  (s/cat :op #{:filter} :exprs (s/+ ::expr) :child ::operation))

(defd ::conditional
  "Takes two child operations; results from the first child will be
  returned even if vars from the second are unbound. Corresponds to
  SPARQL's OPTIONAL."
  (s/cat :op #{:conditional} :base ::operation :optional ::operation))

(defd ::dataset-names
  "Not sure what this form does TBH. ARQ doesn't document it."
  (s/cat :op #{:dataset-names} :node ::g/node))

(defd ::diff
  "Return solutions that are present in one child or the other, but
  not both."
  (s/cat :op #{:diff} :a ::operation :b ::operation))

(defd ::disjunction
  "Logical disjunction between multiple operations."
  (s/cat :op #{:disjunction} :children (s/+ ::operation)))

(defd ::extend
  "Bind one or more variables to expression results, within a body
  operation."
  (s/cat :op #{:extend} :vars (s/spec ::var-expr-list) :body ::operation))

(defd ::graph
  "Define a graph using a name and an operation."
  (s/cat :op #{:graph} :label ::g/node :body ::operation))

(defd ::group
  "Group results by a set of variables and expressions, optionally
  calling an aggregator function on the results. Corresponds to
  SPARQL's GROUP BY."
  (s/cat :op #{:group}
         :vars ::var-set
         :aggregators (s/spec ::var-aggr-list)
         :body ::operation))

(defd ::join
  "Join the result sets of two operations. Corresponds to a nested
  pattern in SPARQL."
  (s/cat :op #{:join} :left ::operation :right ::operation))

(defd ::label
  "Do-nothing operation to annotate the operation tree with arbitrary
  objects. Unlikely to be useful in Aristotle."
  (s/cat :op #{:label} :label any? :child ::operation))

(defd ::left-join
  "Outer join or logical union of two sub-operators, subject to the
  provided filter expressions. Equivalent to an OPTIONAL plus a filter
  expression in SPARQL."
  (s/cat :op #{:left-join} :left ::operation :right ::operation
         :exprs (s/+ ::expr-list)))

(defd ::list
  "View of a result set as a list. Usually redundant in Aristotle."
  (s/cat :op #{:list} :child ::operation))

(defd ::minus
  "Return solutions in the first operation, with matching solutions in
  the second operation removed. Corresponds to SPARQL's MINUS."
  (s/cat :op #{:minus} :left ::operation :right ::operation))

(defd ::null
  "Operation representing the empty result set."
  (s/cat :op #{:null}))

(defd ::order
  "Yield a sorted view of the result set, given some sort
  conditions. Corresponds to SPARQL's ORDER BY."
  (s/cat :op #{:order} :sort-conditions ::sort-conditions :child ::operation))

(defd ::quad
  "A single RDF quad"
  (s/cat :op #{:quad} :quad ::quad))

(defd ::quad-block
  "A quad pattern formed from multiple 4-tuples"
  (s/cat :op #{:quad-block} :quads (s/+ ::quad)))

(defd ::quad-pattern
  "A logical quad pattern formed by supplying a graph identifier and
  one or more triple forms (parsed using Aristotle's standard triple
  format.)"
  (s/cat :op #{:quad-pattern}
         :graph-id ::g/node
         :triples (s/+ ::g/triples)))

(defd ::reduced
  "Similar to :distinct in that it removes duplicate entries, more
  performant because it only removes _consecutive_ duplicate
  entries (meanting the result set may still contain duplicates."
  (s/cat :op #{:reduced} :child ::operation))

(defd ::sequence
  "A join-like operation where the result set from one operation can
  be fed directly into the next form, without any concern for scoping
  issues."
  (s/cat :op #{:sequence} :first ::operation :second ::operation))

(defd ::slice
  "Return a subset of the result set using a start and end
  index. Corresponds to SPARQL's LIMIT and OFFSET."
  (s/cat :op #{:slice} :child ::operation :start int? :length int?))

(defd ::top-n
  "Limit to the first N results of a result set. More efficient
  than :order combined with :slice because it does not need to realize
  the entire result set at once."
  (s/cat :op #{:top-n}
         :count int?
         :sort-conditions ::sort-conditions
         :child ::operation))

(defd ::union
  "Logical union of the result set from two operations. Corresponds to SPARQL's UNION"
  (s/cat :op #{:union} :left ::operation :right ::operation))

;; Expressions

(defd ::composite-expr
  "Expression form whose arguments are other expressions."
  (s/cat :name symbol? :args (s/+ ::expr)))

(defd ::exists-expr
  "Expression which takes an operation as its argument. Returns true
  if the operation has a non-empty result set."
  (s/cat :e #{'exists} :op ::operation))

(defd ::not-exists-expr
  "Expression which takes an operation as its argument. Returns true
  if the operation has an empty result set."
  (s/cat :e #{'not-exists} :op ::operation))

(defd ::custom-expr
  "User-defined expression, with an IRI in 'function position'."
  (s/cat :fn ::g/iri :args (s/+ ::expr)))

(defd ::expr
  "An expression that resolves to a value."
  (s/or :node ::g/node
        :exists ::exists-expr
        :not-exists ::not-exists-expr
        :composite-expr ::composite-expr
        :custom-expr ::custom-expr))

(defd ::count-agg-expr
  "Count expression. Concrete implementation depends on whether it has
  no args, a single variable arg, or a nested `distinct expression,
  which may or may not have a var (for a total of 4 possibilities.)"
  (s/or :simple-count (s/cat :expr #{'count})
        :count-var (s/cat :expr #{'count} :var ::g/variable)
        :count-distinct  (s/cat :expr #{'count}
                                :distinct (s/spec (s/cat :expr #{'distinct})))
        :count-distinct-var
        (s/cat :expr #{'count}
               :distinct (s/spec (s/cat :expr #{'distinct} :var ::g/variable)))))

(defd ::agg-expr
  "An aggregate expression."
  (s/or :count ::count-agg-expr
        :sum (s/cat :type #{'sum} :arg ::expr)
        :avg (s/cat :type #{'avg} :arg ::expr)
        :min (s/cat :type #{'min} :arg ::expr)
        :max (s/cat :type #{'max} :arg ::expr)
        :sample (s/cat :type #{'sample} :arg ::expr)
        :group-concat (s/cat :type #{'group-concat}
                             :expr ::expr :separator string?)))


