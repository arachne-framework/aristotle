(ns arachne.aristotle.inference
  "Tools for adding additional inference rules to a graph.

  See https://jena.apache.org/documentation/inference/"
  (:require [clojure.spec.alpha :as s]
            [arachne.aristotle.graph :as g]
            [arachne.aristotle.registry :as reg])
  (:import [org.apache.jena.graph Triple Node_Blank Node_Variable]
           [org.apache.jena.reasoner.rulesys Rule]
           [org.apache.jena.reasoner TriplePattern]
           [org.apache.jena.reasoner.rulesys
            RuleReasoner FBRuleReasoner OWLFBRuleReasoner Node_RuleVariable]
           [org.apache.jena.reasoner InfGraph ReasonerRegistry]
           [java.util List]))

(def ^:private ^:dynamic *assignments*)

(defn- find-or-assign
  "Find an existing var in the same rule, or construct a new one."
  [^Node_Variable n]
  (let [name (str "?" (.getName n))
        [_ assignments] (swap! *assignments*
                               (fn [[idx assignments :as val]]
                                 (if (get assignments name)
                                   val
                                   [(inc idx)
                                    (assoc assignments name
                                           (Node_RuleVariable. name idx))])))]
    (get assignments name)))

(defn- sub
  "Substitute a general RDF node for the type that should be used in a
  TriplePattern as part of a rule."
  [node]
  (cond
    (instance? Node_Blank node) (Node_RuleVariable/WILD)
    (instance? Node_Variable node) (find-or-assign node)
    :else node))

(defn- pattern
  [triples]
  (for [^Triple t (g/triples triples)]
    (TriplePattern. (sub (.getSubject t))
                    (sub (.getPredicate t))
                    (sub (.getObject t)))))

(defn- coll-of? [class coll]
  (and (seqable? coll)
       (every? #(instance? class %) (seq coll))))

(defn- extract
  "Return a map of variable assignments used in the given object."
  [val]
  (cond
    (instance? Node_RuleVariable val) (if (= Node_RuleVariable/WILD val)
                                        {}
                                        {(.getName ^Node_RuleVariable val) val})
    (instance? TriplePattern val) (merge (extract (.getSubject ^TriplePattern val))
                                         (extract (.getPredicate ^TriplePattern val))
                                         (extract (.getObject ^TriplePattern val)))
    (instance? Rule val) (apply merge (map extract (concat (.getHead ^Rule val) (.getBody ^Rule val))))
    :else {}))

(defn add
  "Given a graph, return a new graph with a reasoner including the given
  rules. This may be expensive, given that it rebuilds the reasoner
  for the entire graph."
  [^InfGraph g rules]
  (let [reasoner ^FBRuleReasoner (.getReasoner g)]
    (.addRules reasoner ^List rules)
    (.bind reasoner (.getRawGraph g))))

(defn rule
  "Create an implication rule. Takes the following keyword args:

  :name - name of the rule
  :body - The premesis, or left-hand-side of a rule. Specified as a
          data pattern using the `arachne.aristotle.graph/AsTriples`
          protocol.
  :head - the consequent, or right-hand-side of a rule. May be a data
          pattern or an instance of Rule.
  :dir - :forward if the rule is a forward-chaining rule, or :back for
         a backward-chaining rule. Defaults to :back."
  [& {:keys [^String name body head dir]}]
  (binding [*assignments* (let [vars (extract head)]
                            (atom [(count vars) vars]))]
    (let [^List head (if (instance? Rule head)
                 [head]
                 (pattern head))
          ^List body (pattern body)]
      (doto (Rule. name head body)
        (.setBackward (not (= :forward dir)))
        (.setNumVars (count (second @*assignments*)))))))

(def owl-rules
  "The maximal set of OWL rules supported by Jena"
  (.getRules ^RuleReasoner (ReasonerRegistry/getOWLReasoner)))

(def mini-rules
  "The OWL rules supported by Jena's mini Reasoner"
  (.getRules ^RuleReasoner (ReasonerRegistry/getOWLMiniReasoner)))

(def table-all
  "Rule that calls the built in TableAll directive. This usually
   desirable, to prevent infinite circular backwards inferences."
  (Rule/parseRule "-> tableAll()."))
