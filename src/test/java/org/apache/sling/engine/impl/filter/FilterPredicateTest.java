/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.engine.impl.filter;

import static org.apache.sling.engine.EngineConstants.SLING_FILTER_EXTENSIONS;
import static org.apache.sling.engine.EngineConstants.SLING_FILTER_METHODS;
import static org.apache.sling.engine.EngineConstants.SLING_FILTER_PATTERN;
import static org.apache.sling.engine.EngineConstants.SLING_FILTER_REQUEST_PATTERN;
import static org.apache.sling.engine.EngineConstants.SLING_FILTER_RESOURCE_PATTERN;
import static org.apache.sling.engine.EngineConstants.SLING_FILTER_SELECTORS;
import static org.apache.sling.engine.EngineConstants.SLING_FILTER_SUFFIX_PATTERN;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FilterPredicateTest extends AbstractFilterTest {

    /**
     * This one is especially important because it represents >99% of the requests
     */
    @Test
    public void testNoPredicate() {
        assertTrue("predicate with no recognised configuration should pass", predicate().test(whateverRequest()));
    }

    @Test
    public void testPathPattern() {
        FilterPredicate predicate = predicate(SLING_FILTER_PATTERN,"/content/test/.*");
        assertTrue("/content/test/foo should be selected", predicate.test(mockRequest("/content/test/foo","json", null, null, null)));
        assertFalse("/content/bar/foo should not be selected", predicate.test(mockRequest("/content/bar/foo","json", null, null, null)));
    }

    @Test
    public void testRequestPathPattern() {
        FilterPredicate predicate = predicate(SLING_FILTER_REQUEST_PATTERN,"/content/test/.*");
        assertTrue("/content/test/foo should be selected", predicate.test(mockRequest("/content/bar/foo", "/content/test/foo", "json")));
        assertFalse("/content/bar/foo should not be selected", predicate.test(mockRequest("/content/bar/foo","/content/bar/foo", "json")));
    }

    @Test
    public void testResourcePathPattern() {
        FilterPredicate predicate = predicate(SLING_FILTER_RESOURCE_PATTERN,"/content/test/.*");
        assertTrue("/content/test/foo should be selected", predicate.test(mockRequest("/content/test/foo", "/content/bar/foo", "json")));
        assertFalse("/content/bar/foo should not be selected", predicate.test(mockRequest("/content/bar/foo","/content/bar/foo", "json")));
    }

    @Test
    public void testExtensions() {
        FilterPredicate predicate = predicate(SLING_FILTER_PATTERN,
                "/content/test/.*",
                SLING_FILTER_EXTENSIONS,
                new String[]{"txt","xml"});
        assertTrue("/content/test/foo.txt should be selected", predicate.test(mockRequest("/content/test/foo","txt", null, null, null)));
        assertFalse("/content/test/foo.json should not be selected", predicate.test(mockRequest("/content/test/foo","json", null, null, null)));
    }

    @Test
    public void testSuffix() {
        FilterPredicate predicate = predicate(SLING_FILTER_PATTERN,
                "/content/test/.*",
                SLING_FILTER_SUFFIX_PATTERN,
                "/foo/.*");
        assertTrue("/content/test/foo /foo/bar should be selected", predicate.test(mockRequest("/content/test/foo",null, null, null, "/foo/bar")));
        assertFalse("/content/test/foo /bar/foo should not be selected", predicate.test(mockRequest("/content/test/foo",null, null, null, "/bar/foo")));
    }

    @Test
    public void testMethod() {
        FilterPredicate predicate = predicate(SLING_FILTER_PATTERN,
                "/content/test/.*",
                SLING_FILTER_METHODS,
                new String[]{"POST","PUT"});
        assertTrue("POST /content/test/foo should be selected", predicate.test(mockRequest("/content/test/foo",null, null, "POST", null)));
        assertFalse("GET /content/test/foo should not be selected", predicate.test(mockRequest("/content/test/foo",null, null, "GET", null)));
    }

    @Test
    public void testSelectors() {
        FilterPredicate predicate = predicate(SLING_FILTER_PATTERN,
                "/content/test/.*",
                SLING_FILTER_SELECTORS,
                new String[]{"test","foo","bar"});
        assertTrue("POST /content/test/foo.foo.test.someother.json should be selected", predicate.test(mockRequest("/content/test/two","json", new String[]{"foo","test","someother"}, "POST", null)));
        assertTrue("POST /content/test/foo.test.someother.json should be selected", predicate.test(mockRequest("/content/test/one","json", new String[]{"test","someother"}, "PUT", null)));
        assertFalse("GET /content/test/foo.json should not be selected", predicate.test(mockRequest("/content/test/no","json",  null, "GET", null)));
    }

    @Test
    public void testRootWithNoPath() {
        FilterPredicate predicate = predicate(SLING_FILTER_PATTERN,"/");
        assertTrue("'' path based request should be selected with a slash", predicate.test(mockRequest("",null, null, null, null)));
    }

    @Test
    public void testToString() {
        FilterPredicate predicate = predicate(SLING_FILTER_PATTERN, "/content/test",SLING_FILTER_EXTENSIONS, new String[]{"json"}, SLING_FILTER_METHODS, new String[]{"GET"});
        String patternString = predicate.toString();
        assertTrue("there should be /content/test in the string representation", patternString.contains("/content/test"));
        assertTrue("there should be json in the string representation", patternString.contains("json"));
        assertTrue("there should be GET in the string representation", patternString.contains("GET"));
    }
}