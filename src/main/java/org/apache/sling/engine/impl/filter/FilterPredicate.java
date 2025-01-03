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

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Pattern;

import jakarta.servlet.Filter;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.Resource;
import org.osgi.framework.ServiceReference;
import org.osgi.util.converter.Converters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Arrays.asList;
import static org.apache.sling.engine.EngineConstants.SLING_FILTER_EXTENSIONS;
import static org.apache.sling.engine.EngineConstants.SLING_FILTER_METHODS;
import static org.apache.sling.engine.EngineConstants.SLING_FILTER_PATTERN;
import static org.apache.sling.engine.EngineConstants.SLING_FILTER_REQUEST_PATTERN;
import static org.apache.sling.engine.EngineConstants.SLING_FILTER_RESOURCETYPES;
import static org.apache.sling.engine.EngineConstants.SLING_FILTER_RESOURCE_PATTERN;
import static org.apache.sling.engine.EngineConstants.SLING_FILTER_SELECTORS;
import static org.apache.sling.engine.EngineConstants.SLING_FILTER_SUFFIX_PATTERN;

/**
 * Contains a set of predicates that helps testing whether to enable a filter for a request or not
 * it can only be constructed from a filter service reference, from whose properties it builds its
 * predicates
 */
public class FilterPredicate {

    private static final Logger LOG = LoggerFactory.getLogger(FilterPredicate.class);

    private final Collection<String> methods;
    private final Collection<String> selectors;
    private final Collection<String> extensions;
    private final Collection<String> resourceTypes;
    private final Pattern pathRegex;
    private final Pattern resourcePathRegex;
    private final Pattern requestPathRegex;
    private final Pattern suffixRegex;

    /**
     * Create a new predicate
     * @param reference osgi service configuration
     */
    public FilterPredicate(final ServiceReference<Filter> reference) {
        this.selectors = asCollection(reference, SLING_FILTER_SELECTORS);
        this.extensions = asCollection(reference, SLING_FILTER_EXTENSIONS);
        this.resourceTypes = asCollection(reference, SLING_FILTER_RESOURCETYPES);
        this.methods = asCollection(reference, SLING_FILTER_METHODS);
        this.pathRegex = asPattern(reference, SLING_FILTER_PATTERN);
        this.resourcePathRegex = asPattern(reference, SLING_FILTER_RESOURCE_PATTERN);
        this.requestPathRegex = asPattern(reference, SLING_FILTER_REQUEST_PATTERN);
        this.suffixRegex = asPattern(reference, SLING_FILTER_SUFFIX_PATTERN);
    }

    /**
     * @param reference osgi service reference
     * @param propertyName configuration property name
     * @return value of the given property, as a collection, or null if it does not exist
     */
    private Collection<String> asCollection(final ServiceReference<Filter> reference, final String propertyName) {
        final String[] value = Converters.standardConverter()
                .convert(reference.getProperty(propertyName))
                .to(String[].class);
        return value != null && value.length > 0 ? asList(value) : null;
    }

    /**
     * @param reference osgi service reference
     * @param propertyName configuration property name
     * @return value of the given property, as a compiled pattern, or null if it does not exist
     */
    private Pattern asPattern(final ServiceReference<Filter> reference, String propertyName) {
        String pattern = Converters.standardConverter()
                .convert(reference.getProperty(propertyName))
                .to(String.class);
        return pattern != null && pattern.length() > 0 ? Pattern.compile(pattern) : null;
    }

    /**
     * @param allowed configured element
     * @param actual elements of the given request
     * @return true if any elements matches the configured ones, or if not or misconfigured
     */
    private boolean anyElementMatches(final Collection<String> allowed, final String... actual) {
        return allowed == null || !Collections.disjoint(allowed, asList(actual));
    }

    /**
     * @param resourceTypes configured resourceTypes
     * @param request request that is being tested
     * @return true if the request's resource is of one of the types, or if not or misconfigured
     */
    private boolean anyResourceTypeMatches(
            final Collection<String> resourceTypes, final SlingJakartaHttpServletRequest request) {
        if (resourceTypes == null) {
            return true;
        }
        Resource resource = request.getResource();
        for (final String resourceType : resourceTypes) {
            if (resource.isResourceType(resourceType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param pattern configured compiled pattern
     * @param candidate tested string
     * @return true if candidate is matching the given pattern, or if not or misconfigured
     */
    private boolean patternMatches(final Pattern pattern, final String candidate) {
        return pattern == null
                || candidate == null
                || pattern.matcher(candidate).matches();
    }

    /**
     * @param req request that is tested upon this predicate
     * @return true if this predicate's configuration match the request
     */
    boolean test(final SlingJakartaHttpServletRequest req) {
        LOG.debug("starting filter test against {} request", req);
        final RequestPathInfo requestPathInfo = req.getRequestPathInfo();
        final String path = requestPathInfo.getResourcePath();
        final String uri = req.getPathInfo();
        boolean select = anyElementMatches(methods, req.getMethod())
                && anyElementMatches(selectors, requestPathInfo.getSelectors())
                && anyElementMatches(extensions, requestPathInfo.getExtension())
                && anyResourceTypeMatches(resourceTypes, req)
                && (patternMatches(pathRegex, path == null || path.isEmpty() ? "/" : path)
                        || patternMatches(pathRegex, uri == null || uri.isEmpty() ? "/" : uri))
                && (patternMatches(requestPathRegex, uri == null || uri.isEmpty() ? "/" : uri))
                && (patternMatches(resourcePathRegex, path == null || path.isEmpty() ? "/" : path))
                && patternMatches(suffixRegex, requestPathInfo.getSuffix());
        LOG.debug("selection of {} returned {}", this, select);
        return select;
    }

    @Override
    public String toString() {
        return "FilterPredicate{" + "methods='"
                + methods + '\'' + ", pathRegex="
                + pathRegex + ", requestPathRegex="
                + requestPathRegex + ", resourcePathRegex="
                + resourcePathRegex + ", suffixRegex="
                + suffixRegex + ", selectors='"
                + selectors + '\'' + ", extensions='"
                + extensions + '\'' + ", resourceTypes='"
                + resourceTypes + '\'' + '}';
    }
}
