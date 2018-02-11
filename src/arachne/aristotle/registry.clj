(ns arachne.aristotle.registry
  "Tools for mapping between IRIs and keywords"
  (:refer-clojure :exclude [find alias])
  (:import [org.apache.jena.rdf.model.impl Util]))

(defonce ^:dynamic *registry* {:ns->prefix {}
                               :prefix->ns {}
                               :kw->iri {}
                               :iri->kw {}})

(defn add-prefix
  [registry allow-overrides namespace prefix]
  (-> registry
      (update-in [:ns->prefix (name namespace)]
                 (fn [existing]
                   (if (and (not allow-overrides) existing (not= existing prefix))
                     (throw (ex-info (format "Could not register namespace %s to URI prefix %s, already registered to %s" namespace prefix existing)
                                                  {:namespace namespace
                                                   :prefix prefix
                                                   :existing existing}))
                     prefix)))
      (assoc-in [:prefix->ns prefix] (name namespace))))

(defn add-alias
  [registry kw iri]
  (-> registry
      (assoc-in [:kw->iri kw] iri)
      (assoc-in [:iri->kw iri] kw)))

(defn prefix
  "Register a namespace as an RDF IRI prefix."
  [namespace prefix]
  (alter-var-root #'*registry* add-prefix false namespace prefix))

(defn alias
  "Register a mapping between a keyword and a specific IRI"
  [kw iri]
  (alter-var-root #'*registry* add-alias kw iri))

(defn iri
  "Given a keyword, build a corresponding IRI, throwing an exception if this is not possible."
  [kw]
  (or (get-in *registry* [:kw->iri kw])
      (when-let [prefix (get-in *registry* [:ns->prefix (namespace kw)])]
        (str prefix (name kw)))
      (throw (ex-info (format "Could not determine IRI for %s, no matching prefix or keyword is registered."
                        kw)
               {:keyword kw}))))

(defn kw
  "Return a keyword representing the given IRI. Returns nil if no matching
   keyword or namespace could be found in the registry."
  [iri]
  (or (get-in *registry* [:iri->kw iri])
      (let [idx (Util/splitNamespaceXML iri)
            name (subs iri idx)
            prefix (subs iri 0 idx)
            ns (get-in *registry* [:prefix->ns prefix])]
        (when ns (keyword ns name)))
      nil))

(defmacro with
  "Execute the supplied function with the specified prefix map in the thread-local registry"
  [prefix-map & body]
  `(binding [*registry* (reduce (fn [r# [ns# prefix#]]
                                  (add-prefix r# true ns# prefix#))
                                *registry*
                                ~prefix-map)]
     ~@body))

(prefix 'rdf "http://www.w3.org/1999/02/22-rdf-syntax-ns#")
(prefix 'rdfs "http://www.w3.org/2000/01/rdf-schema#")
(prefix 'xsd "http://www.w3.org/2001/XMLSchema#")
(prefix 'owl "http://www.w3.org/2002/07/owl#")
(prefix 'owl2 "http://www.w3.org/2006/12/owl2#")
