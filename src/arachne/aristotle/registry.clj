(ns arachne.aristotle.registry
  "Tools for mapping between IRIs and keywords"
  (:refer-clojure :exclude [find alias])
  (:import [org.apache.jena.rdf.model.impl Util]))

(defonce ^:dynamic *registry* (atom {:ns->prefix {}
                                     :prefix->ns {}
                                     :kw->iri {}
                                     :iri->kw {}}))

(defn register
  "Register a mapping between a keyword and a specific IRI"
  [kw iri]
  (swap! *registry* #(-> %
                       (assoc-in [:kw->iri kw] iri)
                       (assoc-in [:iri->kw iri] kw))))

(defn prefix
  "Register a namespace as an RDF IRI prefix."
  [namespace prefix]
  (swap! *registry* #(-> %
                       (assoc-in [:ns->prefix (name namespace)] prefix)
                       (assoc-in [:prefix->ns prefix] (name namespace)))))

(defn iri
  "Given a keyword, build a corresponding IRI, throwing an exception if this is not possible."
  [kw]
  (or (get-in @*registry* [:kw->iri kw])
      (when-let [prefix (get-in @*registry* [:ns->prefix (namespace kw)])]
        (str prefix (name kw)))
      (throw (ex-info (format "Could not determine IRI for %s, no matching prefix or keyword is registered."
                        kw)
               {:keyword kw}))))

(defn kw
  "Return a keyword representing the given IRI. Returns nil if no matching
   keyword or namespace could be found in the registry."
  [iri]
  (or (get-in @*registry* [:iri->kw iri])
      (let [idx (Util/splitNamespaceXML iri)
            name (subs iri idx)
            prefix (subs iri 0 idx)
            ns (get-in @*registry* [:prefix->ns prefix])]
        (when ns (keyword ns name)))
    nil))


(prefix :rdf "http://www.w3.org/1999/02/22-rdf-syntax-ns#")