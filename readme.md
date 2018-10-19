# Aristotle

[![CircleCI](https://circleci.com/gh/arachne-framework/aristotle.svg?style=svg)](https://circleci.com/gh/arachne-framework/aristotle)

An RDF/OWL library for Clojure, providing a data-oriented wrapper for
Apache Jena.

Key features:

- Read/write RDF graphs using idiomatic Clojure data structures.
- SPARQL queries expressed using Clojure data structures.
- Pluggable inferencing and reasoners.
- Pluggable validators.

##  Rationale

RDF is a powerful framework for working with highly-annotated data in very abstract ways. Although it isn't perfect, it is highly researched, well defined and understood, and the industry standard for "rich" semi-structured, open-ended information modeling.

Most of the existing Clojure tools for RDF are focused mostly on creating and manipulating RDF graphs in pure Clojure at a low level. I desired a more comprehensive library with the specific objective of bridging existing idioms for working with Clojure data to RDF graphs.

Apache Jena is a very capable, well-designed library for working with RDF and the RDF ecosystem. It uses the Apache software license, which unlike many other RDF tools is compatible with Clojure's EPL. However, Jena's core APIs can only be described as agressively object-oriented. Since RDF is at its core highly data-oriented, and Clojure is also data-oriented, using an object-oriented or imperative API seems especially cumbersome. Aristotle attempts to preserve "good parts" of Jena, while replacing the cumbersome APIs with clean data-driven interfaces.

Aristotle does not provide direct access to other RDF frameworks (such as RDF4j, JSONLD, Commons RDF, OWL API, etc.) However, Jena itself is highly pluggable, so if you need to interact with one of these other systems it is highly probably that a Jena adapter already exists or can be easily created.

## Index

- [Data Model](#data-model)
  - [Literals](#literals)
  - [Data Structures](#data-structures)
- [API](#api)
- [Query](#query)
- [Validation](#validation)

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
|symbols starting with `_`| named blank node|
|other symbols| IRI of the form `<urn:clojure:namespace/name>`.

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

The registry is stored in the global dynamic Var `arachne.aristotle.registry/*registry*`, which can be also overridden on a thread-local basis using the `arachne.aristotle.registry/with` macro, which takes a map of namespaces (as keywords) and IRI prefixes. For example:

```clojure
(reg/with {'foaf "http://xmlns.com/foaf/0.1/"
           'dc "http://purl.org/dc/elements/1.1/"}
  ;; Code using keywords with :foaf and :dc namespaces
  )
```

You can also register a prefix in RDF/EDN data, using the `#rdf/prefix` tagged literal. The prefix will be added to the thread-local binding and is scoped to the same triple expansion. This allows you to define a prefix alongside the data that uses it, without installing it globally or managing it in your code. For example:

```clojure
[#rdf/prefix [:ex "http://example.com/"]
 {:rdf/about :ex/luke
  :foaf/name "Luke"}]
  ```

#### Wildcard Prefixes

Aristotle now allows you to register a RDF IRI prefix for a namespace *prefix*, rather than a fully specified namespace. To do so, use an asterisk in the symbol you provide to the `prefix` function:

```clojure
(reg/prefix 'arachne.* "http://arachne-framework.org/vocab/1.0/")
```

This means that keywords with a namespace that starts with an `arachne` namespace segment will use the supplied prefix. Any additional namespace segments will be appended to the prefix, separated by a forward slash (`/`).

Given the registration above, for example, the keyword `:arachne.http.request/body` would be interpreted as the IRI "<http://arachne-framework.org/vocab/1.0/http/request/body>"

If multiple wildcard prefixes overlap, the system will use whichever is more specific, and will prefer non-wildcard registrations to wildcard registrations in the case of ambiguity.

Using `#` or any other character as a prefix separator for wildcard prefixes, instead of `/`, is currently not supported.

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

Aristotle's primary API is exposed in its top-level namespace, `arachne.aristotle`, which defines functions to create and interact with _graphs_.

A graph is a collection of RDF data, together with (optionally) logic and/or inferencing engines. Graphs may be stored in memory or be a facade to an external RDF database (although all the graph constructors shipped with Aristotle are for in-memory graphs.)

Graphs are instances of `org.apache.jena.graph.Graph`, which are
stateful mutable objects (mutability is too deeply ingrained in Jena
to provide an immutable facade.) However, Aristotle's APIs are
consistent in returning the model from any update operations, as if
graphs were immutable Clojure-style collections. It is reccomended to
rely on the return value of update operations, as if graphs were
immutable, so your code does not break if immutable graph
representations are ever supported.

Jena Graphs are not thread-safe by default; make sure you limit concurrent graph access.

### Creating a Graph

To create a new graph, invoke the `arachne.aristotle/graph` multimethod. The first argument to `graph` is a keyword specifying the type of graph to construct, additional arguments vary depending on the type of graph.

Graph constructors provided by Aristotle include:

|type|model|
|----|-----|
|:simple| Basic in-memory triple store with no inferencing capability |
|:jena-mini| In-memory triple store that performs OWL 1 inferencing using Jena's "Mini" inferencer (a subset of OWL Full with restrictions on some of the less useful forward entailments.)
|:jena-rules| In-memory triple store supporting custom rules, using Jena's [hybrid backward/forward rules engine](https://jena.apache.org/documentation/inference/#rules). Takes a collection of `org.apache.jena.reasoner.rulesys.Rule` objects as an additional argument (the prebuilt collection of rules for Jena Mini is provided at `arachne.aristotle.inference/mini-rules`) |

Clients may wish to provide additional implementations of the `graph` multimethod to support additional underlying graphy or inference types; the only requirement is that the method return an instance of `org.apache.jena.rdf.graph.Graph`. For example, for your project, you may wish to create a Graph backed by on-disk or database storag, or which uses the more powerful Pellet reasoner, which has Jena integration but is not shipped with Aristotle due to license restrictions.

Example:

```
(require '[arachne.aristotle :as aa])
(def m (aa/graph :jena-mini))
```

### Adding data to a graph

In order to do anything useful with a graph, you must add additional facts. Facts may be added either programatically in your code, or by reading serialized data from a file or remote URL.

#### Adding data programatically

To add data programatically, use the `arachne.aristotle/add` function, which takes a graph and some data to add. The data is processed into RDF triples using  `arachne.aristotle.graph/triples`, using the data format documented above. For example:

```
(require '[arachne.aristotle :as aa])

(def g (aa/graph :jena-mini))

(aa/add g {:rdf/about ::luke
           :foaf/firstName "Luke"
           :foaf/lastName "VanderHart"})
```

#### Adding data from a file

To add data from a file, use the `arachne.aristotle/read` function, which takes a graph and a file. The file may be specified by a:

  - String of the absolute or process-relative filename
  - java.net.URI
  - java.net.URL
  - java.io.File

Jena will detect what format the file is in, which may be one of RDF/XML, Turtle, N3, or N-Triples. All of the statements it contains will be added to the graph. Example:

## Query

Aristotle provides a data-oriented interface to Jena's SPARQL query engine. Queries themselves are expressed as Clojure data, and can be programatically generated and combined (similar to queries in Datomic.)

To invoke a query, use the `arachne.aristotle.query/query` function, which takes a query data structure, a graph, and any query inputs. It returns the results of the query.

SPARQL itself is string oriented, with a heavily lexical grammar that does not translate cleanly to data structures. However, SPARQL has an internal algebra that *is* very clean and composable. Aristotle's query data uses this internal SPARQL alegebra (which is exposed by Jena's ARQ data graph) ignoring SPARQL syntax. All queries expressible in SPARQL syntax are also expressible in Aristotle's query data, modulo some features that are not implemented yet (e.g, query fedration across remote data sources.)

Unfortunately, the SPARQL algebra has no well documented syntax. A [rough overview](https://www.w3.org/2011/09/SparqlAlgebra/ARQalgebra) is available, and this readme will document some of the more common forms. For more details, see the [query specs](https://github.com/arachne-framework/aristotle/blob/master/src/arachne/aristotle/query/spec.clj) with their associated docstrings.

Aristotle queries are expressed as compositions of algebraic operations, using the generalized form `[operation expression* sub-operation*]` These operation vectors may be nested arbitrarily.

Expressions are specified using a Clojure list form, with the expression type as a symbol. These expressions take the general form `(expr-type arg*)`.

### Running Queries

To run a query, use the `arachne.aristotle.query/run` function. This function takes a graph, an (optional) binding vector, a query, and (optionally) a map of variable bindings which serve as query inputs.

If a binding vector is given, results will be returned as a set of tuples, one for each unique binding of the variables in the binding vector.

If no binding vector is supplied, results will be returned as a sequence of query solutions, with each solution represented as a map of the variables it binds. In this case, solutions may not be unique (unless the query specifically inclues a `:distinct` operation.)

Some examples follow:

#### Sample: simple query

```clojure
(require '[arachne.aristotle.query :as q])

(q/run my-graph '[:bgp [:example/luke :foaf/knows ?person]
                       [?person :foaf/name ?name]])
```

This query is a single pattern match (using a "basic graph pattern" or "bgp"), binding the `:foaf/name` property of each entity that is the subject of `:foaf/knows` for an entity identified by `:example/luke`. 

An example of the results that might be returned by this query is:

```clojure
({?person <http://example.com/person#james> ?name "Jim"},
 {?person <http://example.com/person#sara> ?name "Sara"},
 {?person <http://example.com/person#jules> ?name "Jules"})
```

#### Sample: simple query with result binding

This is the same query, but using a binding vector

```clojure
(q/run my-graph '[?name]
       '[:bgp [:example/luke :foaf/knows ?person]
              [?person :foaf/name ?name]])
```
In this case, results would look like:

```clojure
#{["Jim"]
  ["Sara"]
  ["Jules"]}
```

#### Sample: query with filtering expression

This example expands on the previous query, using a `:filter` operation with an expression to only return acquaintances above the age of 18: 

```clojure
(q/run my-graph '[?name]
       '[:filter (< 18 ?age)
         '[:bgp [:example/luke :foaf/knows ?person]
                [?person :foaf/name ?name]
                [?person :foaf/age ?age]]])
```

#### Sample: providing inputs

This example is the same as those above, except instead of hardcoding the base individual as `:example/luke`, the starting individual is bound in a separate binding map provided to `q/run`. 

```clojure
(q/run my-graph '[?name]
        [:bgp [?individual :foaf/knows ?person]
              [?person :foaf/name ?name]]
  '{?individual :example/luke})
```

It is also possible to bind multiple possibilities for the value of `?individual`: 

```clojure
(q/run my-graph '[?name]
        [:bgp [?individual :foaf/knows ?person]
              [?person :foaf/name ?name]]
  '{?individual #{:example/luke
                  :example/carin
                  :example/dan}})
```

This will find the names of all persons who are known by Luke, Carin OR Dan.

### Precompiled Queries

Queries can also be precompiled into a Jena Operation object, meaning they do not need to be parsed, interpreted, and optimized again every time they are invoked. To precompile a query, use the `arachne.aristotle.query/build` function: 

```clojure
(def friends-q (q/build '[:bgp [?individual :foaf/knows ?person]
                               [?person :foaf/name ?name]]))
```

You can then use the precompiled query object (bound in this case to `friends-q` in calls to `arachne.aristotle.query/run`:

```clojure
(q/run my-graph friends-q '{?individual :example/luke})
```

The results will be exactly the same as using the inline version.

## Validation

One common use case is to take a given Graph and "validate" it, ensuring its internal consistency (including whether entities in it conform to any OWL or RDFS schema that is present.)

To do this, run the `arachne.aristotle.validation/validate` function. Passed only a graph, it will return any errors returned by the Jena Reasoner that was used when constructing the graph. The `:simple` reasoner will never return any errors, the `:jena-mini` reasoner will return OWL inconsistencies, etc.

If validation is successfull, the validator will return nil or an empty list. If there were any errors, each error will be returned as a map containing details about the specific error type.

#### Closed-World validation

The built-in reasoners use the standard open-world assumption of RDF and OWL. This means that many scenarios that would intuitively be "invalid" to a human (such as a missing min-cardinality attribute) will not be identified, because the reasoner alwas operates under the assumption that it doesn't yet know all the facts.

However, for certain use cases, it can be desirable to assert that yes, the graph actually does contain all pertinent facts, and that we want to make some assertions based on what the graph *actually* knows at a given moment, never mind what facts may be added in the future.

To do this, you can pass additional validator functions to `validate`, providing  a sequence of optional validators as a second argument.

Each of these validator functions takes a graph as its argument, and returns a sequence of validation error maps. An empty sequence implies that the graph is valid.

The "min-cardinality" situation mentioned above has a built in validator, `arachne.aristotle.validators/min-cardinality`. It works by running a SPARQL query on the provided graph that detects if any min-cardinality attributes are missing from entities known to be of an OWL class where they are supposed to be present.

To use it, just provide it in the list of custom validators passed to `validate`: 

```clojure
(v/validate m [v/min-cardinality])
```

This will return the set not only of built in OWL validation errors, but also any min-cardinality violations that are discovered.

Of course, you can provide any additional validator functions as well.
