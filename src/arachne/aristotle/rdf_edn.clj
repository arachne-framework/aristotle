(ns arachne.aristotle.rdf-edn
  "Reader/writer for RDF/EDN"
  (:require [clojure.edn :as edn]
            [arachne.aristotle.graph :as g])
  (:import [org.apache.jena.riot LangBuilder RDFParserRegistry ReaderRIOTFactory ReaderRIOT]
           [org.apache.jena.riot.system ParserProfile StreamRDF]
           [org.apache.jena.atlas.web ContentType]
           [org.apache.jena.sparql.util Context]
           [java.io InputStream Reader InputStreamReader]))

(def lang (-> (LangBuilder/create)
              (.langName "RDF/EDN")
              (.contentType "application/edn")
              (.addAltContentTypes (into-array String ["application/edn"]))
              (.addFileExtensions (into-array String ["edn"]))
              (.build)))

(defn- read-edn
  "Read EDN from an input stream or Reader into the given StreamRDF output object"
  [input ^StreamRDF output]
  (let [data (edn/read-string {:readers *data-readers*} (slurp input))
        triples (g/triples data)]
    (.start output)
    (doseq [t triples]
      (.triple output t))
    (.finish output)))

(defn riot-reader
  "Construct a new RIOT reader for EDN"
  []
  (reify ReaderRIOT
    (^void read [this
                 ^InputStream is
                 ^String base
                 ^ContentType ct
                 ^StreamRDF output
                 ^Context context]
     (read-edn is output))
     (^void read [this
                  ^Reader rdr
                  ^String base
                  ^ContentType ct
                  ^StreamRDF output
                  ^Context context]
      (read-edn rdr output))))

(def factory (reify ReaderRIOTFactory
               (create [_ lang profile]
                 (riot-reader))))

(RDFParserRegistry/registerLangTriples lang factory)
