(ns arachne.aristotle.registry-test
  (:require [clojure.test :refer :all]
            [arachne.aristotle.registry :as reg])
  (:import [clojure.lang ExceptionInfo]))


(reg/alias :mike "http://example.com/people/#mike")
(deftest kw-registration
  (is (= "http://example.com/people/#mike" (reg/iri :mike))))


(reg/prefix 'foaf "http://xmlns.com/foaf/0.1/")
(deftest direct-prefix-registration
  (is (= "http://xmlns.com/foaf/0.1/name" (reg/iri :foaf/name))))

(deftest conflicts
  (testing "Prefix registration conflicts"
    (reg/prefix :aa.bb "http://aa.bb.com/")
    (is (thrown-with-msg? ExceptionInfo #"namespace is already registered to a different prefix"
          (reg/prefix :aa.bb "http://something-else.com/")))
    (is (thrown-with-msg? ExceptionInfo #"prefix is already registered with a different namespace"
          (reg/prefix :something.else "http://aa.bb.com/"))))
  (testing "Alias registration conflicts"
    (reg/alias :abc "http://abc.com/")
    (is (thrown-with-msg? ExceptionInfo #"Cannot alias"
          (reg/alias :abc "http://something-else.com/")))
    (is (thrown-with-msg? ExceptionInfo #"Cannot alias"
          (reg/alias :something-else "http://abc.com/")))))

(deftest fails-on-unknown-kw
  (is (thrown-with-msg? ExceptionInfo #"Could not determine IRI"
                        (reg/iri :foaf.bff/bff)))
  (is (thrown-with-msg? ExceptionInfo #"Could not determine IRI"
                        (reg/iri :billy-bob))))


(reg/prefix :fizz.* "http://example.com/fizz/")
(reg/prefix :fizz.buzz.* "http://example.com/fizz/buzz/")
(reg/prefix :fizz.buzz.bazz "http://example.com/fizzbuzzbazz/")
(reg/prefix :fizz.buzz.booz "http://example.com/fizzbuzzbooz/")

(deftest ns-prefixes
  (is (= "http://example.com/fizz/test1" (reg/iri :fizz/test1)))
  (is (= "http://example.com/fizz/flop/test1" (reg/iri :fizz.flop/test1)))
  (is (= "http://example.com/fizz/buzz/florp/psst/test1"
         (reg/iri :fizz.buzz.florp.psst/test1)))
  (is (= "http://example.com/fizzbuzzbazz/test1" (reg/iri :fizz.buzz.bazz/test1))))

(deftest kw-generation
  (is (= :fizz/test1 (reg/kw "http://example.com/fizz/test1")))
  (is (= :fizz.flop/test1 (reg/kw "http://example.com/fizz/flop/test1")))
  (is (= :fizz.buzz.florp.psst/test1
         (reg/kw "http://example.com/fizz/buzz/florp/psst/test1")))
  (is (= :fizz.buzz.bazz/test1 (reg/kw "http://example.com/fizzbuzzbazz/test1")))
  (is (nil? (reg/kw "http://example.com/fizzbuzzbazz/test1/foo")))
  (is (= :foaf/name (reg/kw "http://xmlns.com/foaf/0.1/name")))
  (is (= :mike (reg/kw "http://example.com/people/#mike")))
  (is (nil? (reg/kw "http://this-is-not-registered#foobar"))))


(reg/prefix :flotsam "http://flotsam.com/")
(reg/prefix :flotsam.jetsam "http://flotsam.com/jetsam#")
(reg/prefix :flotsam.jetsam.yep.* "http://flotsam.com/jetsam.yep/")

(deftest overlapping-generation
  (is (= "http://flotsam.com/foo" (reg/iri :flotsam/foo)))
  (is (= :flotsam/foo (reg/kw "http://flotsam.com/foo")))

  (is (= "http://flotsam.com/jetsam#foo" (reg/iri :flotsam.jetsam/foo)))
  (is (= :flotsam.jetsam/foo (reg/kw "http://flotsam.com/jetsam#foo")))

  (is (= "http://flotsam.com/jetsam.yep/yip/foo" (reg/iri :flotsam.jetsam.yep.yip/foo)))
  (is (= :flotsam.jetsam.yep.yip/foo (reg/kw "http://flotsam.com/jetsam.yep/yip/foo"))))
