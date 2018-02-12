# Aristotle

An RDF/OWL library for Clojure, providing a data-oriented wrapper for
Apache Jena.

Key features:

- Read/write RDF graphs using idiomatic Clojure data structures.
- SPARQL queries expressed using Clojure data structures.
- Pluggable inferencing and reasoners.
- Pluggable validators.

##  Rationale

RDF is a powerful framework for working with highly-annotated data in very abstract ways. Although it isn't perfect, it is highly researched, well defined and understood, and the industry standard for "rich" semi-structured, open-ended information modeling.

Most of the existing Clojure tools for RDF are focused mostly on creating and manipulating RDF graphs in pure Clojure at a low level. I desired a more comprehensive library with the specific objective of bridging existing idioms for working with Clojure data to RDF models.

Apache Jena is a very capable, well-designed library for working with RDF and the RDF ecosystem. It uses the Apache software license, which unlike many other RDF tools is compatible with Clojure's EPL. However, Jena's core APIs can only be described as agressively object-oriented. Since RDF is at its core highly data-oriented, and Clojure is also data-oriented, using an object-oriented or imperative API seems especially cumbersome. Aristotle attempts to preserve "good parts" of Jena, while replacing the cumbersome APIs with clean data-driven interfaces.

Aristotle does not provide direct access to other RDF frameworks (such as RDF4j, JSONLD, Commons RDF, OWL API, etc.) However, Jena itself is highly pluggable, so if you need to interact with one of these other systems it is highly probably that a Jena adapter already exists or can be easily created.

## Index

- [Data Model](#data-model)
  - [Literals](#literals)
  - [Data Structures](#data-structures)
- [API](#api)

## Data Model

To express RDF data as Clojure, Aristotle provides two protocols. `arachne.aristotle.graph/AsNode` converts Clojure literals to RDF Nodes of the appropriate type, while `arachne.aristotle.graph/AsTriples` converts Clojure data structures to sets of RDF triples.

### Literals

Clojure primitive values map to Jena Node objects of the appropriate type.

|Clojure Type|RDF Node|
|------------|--------|
|long|XSD Long|
|double|XSD Double|
|boolean|XSD Boolean|
|java.math.BigDecimal|XSD Decimal|
|java.util.Date|XSD DateTime|
|java.util.Calendar|XSD DateTime|
|string enclosed by angle brackets<br>(e.g, `"<http://foo.com/#bar>"`)| IRI
|other strings| XSD String|
|keyword|IRI (see explanation of IRI/keyword registry below)|
|java.net.URL|IRI|
java.net.URI|IRI|
|symbols starting with `?`| variable node (for patterns or queries)|
|the symbol `_`|unique blank node|
|other symbols| blank node with the symbol's `toString` as its label

#### IRI/Keyword Registry

Since IRIs are usually long strings, and tend to be used repeatedly, using the full string expression can be cumbersome. Furthermore, Clojure tends to prefer keywords to strings, especially for property/attribute names and enumerated or constant values.

Therefore, Aristotle provides a mechanism to associate a namespace with an IRI prefix. Keywords with a registered namespace will be converted to a corresponding IRI.

Use the `arachne.aristotle.registry/prefix` function to declare a prefix. For example,

```
(reg/prefix 'foaf "http://xmlns.com/foaf/0.1/")
```

Then, keywords with a `:foaf` namespace will be interpreted as IRI nodes. For example, with the above declaration `:foaf/name` will be interpreted as `<http://xmlns.com/foaf/0.1/name>`.

The following common namespace prefixes are defined by default:

|Namespace |IRI Prefix|
|----|-------|
|rdf|`<http://www.w3.org/1999/02/22-rdf-syntax-ns#>`|
|rdfs|`<http://www.w3.org/2000/01/rdf-schema#>`|
|xsd|`<http://www.w3.org/2001/XMLSchema#>`|
|owl|`<http://www.w3.org/2002/07/owl#>`|
|owl2|`<http://www.w3.org/2006/12/owl2#>`|

The registry is stored in the dynamic Var `arachne.aristotle.registry/*registry*`, which can be also overridden on a thread-local basis using the `arachne.aristotle.registry/with` macro, which takes a map of namespaces (as keywords) and IRI prefixes. For example:

```clojure
(reg/with {'foaf "http://xmlns.com/foaf/0.1/"
           'dc "http://purl.org/dc/elements/1.1/"}
  ;; Code using keywords with :foaf and :dc namespaces
  )
```

### Data Structures

You can use the `arachne.aristotle.graph/triples` function to convert any compatible Clojure data structure to a collection of RDF Triples (usually in practice it isn't necessary to call `triples` explicitly, as the higher-level APIs do it for you.)

#### Single Triple

A 3-element vector can be used to represent a single RDF Triple. For example: 

```clojure
(ns arachne.aristotle.example
  (:require [arachne.aristotle.registry :as reg]
            [arachne.aristotle.graph :as g]))

(reg/prefix 'arachne.aristotle.example "http://arachne-framework.org/example#")

(g/triples [::luke :foaf/firstName "Luke"])
```

The call to `g/triples` returns a collection containing a single Jena Triple with a subject of `<http://arachne-framework.org/example#luke>`, a predicate of `<http://xmlns.com/foaf/0.1/firstName>` an the string literal `"Luke"` as the object.

#### Collections of Triples

A collection of multiple triples works the same way.

For example, 

```clojure
(g/triples '[[luke :foaf/firstName "Luke"]
             [luke :foaf/knows nola]
             [nola :foaf/firstName "Nola"]]) 
```

Note the use of symbols; in this case, the nodes for both Luke and Nola are represented as blank nodes (without explicit IRIs.)

#### Maps

Maps may be used to represent multiple statements about a single subject, with each key indicating an RDF property. The subject of the map is indicated using the special `:rdf/about` key, which is *not* interpreted as a property, but rather as identifying the subject of the map. If no `:rdf/about` key is present,  a blank node will be used as the subject.

For example:

```clojure
(g/triples {:rdf/about ::luke
            :foaf/firstName "Luke"
            :foaf/lastName "VanderHart"})
```

This is equivalent to two triples:

```
<http://arachne-framework.org/example#luke> <http://xmlns.com/foaf/0.1/firstName> "Luke"
<http://arachne-framework.org/example#luke> <http://xmlns.com/foaf/0.1/lastName> "VanderHart"
```

##### Multiple Values

If the value for a key is a single literal, it is interpreted as a single triple. If the value is a collection, it is intererpreted as multiple values for the same property. For example:

```clojure
(g/triples {:rdf/about ::luke
            :foaf/made [::arachne ::aristotle ::quiescent]})
```                        

Expands to: 

    <http://arachne-framework.org/example#luke> <http://xmlns.com/foaf/0.1/made> <http://arachne-framework.org/example#arachne>
    <http://arachne-framework.org/example#luke> <http://xmlns.com/foaf/0.1/made> <http://arachne-framework.org/example#quiescent>
    <http://arachne-framework.org/example#luke> <http://xmlns.com/foaf/0.1/made> <http://arachne-framework.org/example#aristotle>
    
##### Nested Maps

In addition to literals, the values of keys may be additional maps (or collections of maps). The subject of the nested map will be both the object of the property under which it is specified, and the subject if statements in its own map.

```clojure
(g/triples {:rdf/about ::luke
            :foaf/knows [{:rdf/about ::nola
                          :foaf/name "Nola"
                          :foaf/knows ::luke}}
                         {:rdf/about ::Jim
                          :foaf/name "Jim"}}])
``` 

Expressed in expanded triples, this is:

    <http://arachne-framework.org/example#luke> <http://xmlns.com/foaf/0.1/knows> <http://arachne-framework.org/example#nola>
    <http://arachne-framework.org/example#nola> <http://xmlns.com/foaf/0.1/name> "Nola"
    <http://arachne-framework.org/example#nola> <http://xmlns.com/foaf/0.1/knows> <http://arachne-framework.org/example#luke>
    <http://arachne-framework.org/example#luke> <http://xmlns.com/foaf/0.1/knows> <http://arachne-framework.org/example#jim>
    <http://arachne-framework.org/example#jim> <http://xmlns.com/foaf/0.1/name> "Jim"

## API

Aristotle's primary API is exposed in its top-level namespace, `arachne.aristotle`, which defines functions to create and interact with _models_. 

A model is a collection of RDF data, together with (optionally) logic and/or inferencing engines. Models may be stored in memory or be a facade to an external RDF database (although all the model constructors shipped with Aristotle are for in-memory models.)

Models are instances of `org.apache.jena.rdf.model.Model`, and are stateful mutable objects (mutability is too deeply ingrained in Jena to provide an immutable facade.)

Jena Models are not thread-safe by default. However, all of Aristotle's built-in functions perform the necessary locking to support safe concurrent access. Direct uses of the Model via interop will need to follow the [Jena Concurrency Guidelines](https://jena.apache.org/documentation/notes/concurrency-howto.html), and can leverage the `arachne.aristotle.lock/read` and `arachne.aristotle.lock/write` macros.

### Creating a Model

To create a new model, invoke the `arachne.aristotle/model` multimethod. The first argument to `model` is a keyword specifying the type of model to construct, additional arguments vary depending on the type of model.

Model constructors provided by Aristotle include:

|type|model|
|----|-----|
|:simple| Basic in-memory triple store with no inferencing capability |
|:jena-mini| In-memory triple store that performs OWL 1 inferencing using Jena's "Mini" inferencer (a subset of OWL Full with restrictions on some of the less useful forward entailments.)
|:jena-rules| In-memory triple store supporting custom rules, using Jena's [hybrid backward/forward rules engine](https://jena.apache.org/documentation/inference/#rules). Takes a collection of `org.apache.jena.reasoner.rulesys.Rule` objects as an additional argument (the prebuilt collection of rules for Jena Mini is provided at `arachne.aristotle.inference/mini-rules`) |

Clients may wish to provide additional implementations of the `model` multimethod to support additional model or inference types; the only requirement is that the method return an instance of `org.apache.jena.rdf.model.Model`. For example, for your project, you may wish to use the more powerful Pellet reasoner, which has Jena integration but is not shipped with Aristotle due to license restrictions.

Example:

```
(require '[arachne.aristotle :as aa])
(def m (aa/model :jena-mini))
```

### Adding data to a model

In order to do anything useful with a model, you must add additional facts using the `arachne.aristotle/add` function. `add` takes a model and some data to add. The data will be interpreted 












