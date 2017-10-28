(ns arachne.aristotle.registry
  "Tools for mapping between IRIs and keywords"
  (:refer-clojure :exclude [find alias]))

(defonce ^:dynamic *registry* (atom {:prefixes {}
                                     :mappings {}}))

(defn prefix
  "Register a namespace as an RDF prefix."
  [namespace prefix]
  (swap! *registry* assoc-in [:prefixes (name namespace)] prefix))

(defn register
  "Register a mapping between a keyword and a specific IRI"
  [kw iri]
  (swap! *registry* assoc-in [:mappings kw] iri))

(defn iri
  "Given a keyword, build a corresponding IRI, throwing an exception if this is not possible."
  [kw]
  (or (get-in @*registry* [:mappings kw])
      (when-let [prefix (get-in @*registry* [:prefixes (namespace kw)])]
        (str prefix (name kw)))
      (throw (ex-info (format "Could not determine IRI for %s, no matching prefix or keyword is registered."
                        kw)
               {:keyword kw}))))

(prefix :rdf "http://www.w3.org/1999/02/22-rdf-syntax-ns#")