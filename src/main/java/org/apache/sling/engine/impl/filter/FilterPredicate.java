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

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.Resource;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Filter;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Pattern;

import static java.util.Arrays.asList;
import static org.apache.sling.engine.EngineConstants.SLING_FILTER_EXTENSIONS;
import static org.apache.sling.engine.EngineConstants.SLING_FILTER_METHODS;
import static org.apache.sling.engine.EngineConstants.SLING_FILTER_PATTERN;
import static org.apache.sling.engine.EngineConstants.SLING_FILTER_SUFFIX_PATTERN;
import static org.apache.sling.engine.EngineConstants.SLING_FILTER_RESOURCETYPES;
import static org.apache.sling.engine.EngineConstants.SLING_FILTER_SELECTORS;

/**
 * Contains a set of predicates that helps testing whether to enable a filter for a request or not
 * it can only be constructed from a filter service reference, from whose properties it builds its
 * predicates
 */
public class FilterPredicate {

    private static final Logger LOG = LoggerFactory.getLogger(FilterPredicate.class);

    Collection<String> methods;
    Collection<String> selectors;
    Collection<String> extensions;
    Collection<String> resourceTypes;
    Pattern pathRegex;
    Pattern suffixRegex;

    /*
     * @param reference osgi service configuration
     */
    public FilterPredicate(ServiceReference<Filter> reference) {
        selectors = asCollection(reference, SLING_FILTER_SELECTORS);
        extensions = asCollection(reference, SLING_FILTER_EXTENSIONS);
        resourceTypes = asCollection(reference, SLING_FILTER_RESOURCETYPES);
        methods = asCollection(reference, SLING_FILTER_METHODS);
        pathRegex = asPattern(reference, SLING_FILTER_PATTERN);
        suffixRegex = asPattern(reference, SLING_FILTER_SUFFIX_PATTERN);
    }

    private static Collection<String> asCollection(final ServiceReference<Filter> reference, final String propertyName) {
        String[] value = getTypedProperty(reference, propertyName, String[].class);
        return value == null ? null : asList(value);
    }

    private static Pattern asPattern(final ServiceReference<Filter> reference, String propertyName) {
        String pattern = getTypedProperty(reference, propertyName, String.class);
        return pattern == null ? null : Pattern.compile(pattern);
    }

    private static <T> T getTypedProperty(final ServiceReference<Filter> reference, final String propertyName, final Class<T> type) {
        Object property = reference.getProperty(propertyName);
        return type.cast(property);
    }

    /**
     * @param req request that is tested upon
     * @return true if this instance's configuration match the request
     */
    boolean test(SlingHttpServletRequest req) {
        LOG.debug("starting filter test against {} request", req);
        RequestPathInfo requestPathInfo = req.getRequestPathInfo();
        String path = requestPathInfo.getResourcePath();
        boolean select = anyElementMatches(methods, req.getMethod())
                && anyElementMatches(selectors, requestPathInfo.getSelectors())
                && anyElementMatches(extensions, requestPathInfo.getExtension())
                && anyResourceTypeMatches(resourceTypes, req)
                && patternMatches(pathRegex, path == null || path.isEmpty() ? "/" : path)
                && patternMatches(suffixRegex, requestPathInfo.getSuffix());
        LOG.debug("selection of {} returned {}", this, select);
        return select;
    }

    private static boolean anyElementMatches(final Collection<String> allowed, final String... actual) {
        return allowed == null || !Collections.disjoint(allowed, asList(actual));
    }

    private static boolean anyResourceTypeMatches(final Collection<String> resourceTypes, final SlingHttpServletRequest request) {
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

    private static boolean patternMatches(final Pattern pattern, final String path) {
        return pattern == null || path == null || pattern.matcher(path).matches();
    }

    @Override
    public String toString() {
        return "FilterPredicate{" +
                "methods='" + methods + '\'' +
                ", pathRegex=" + pathRegex +
                ", suffixRegex=" + suffixRegex +
                ", selectors='" + selectors + '\'' +
                ", extensions='" + extensions + '\'' +
                ", resourceTypes='" + resourceTypes + '\'' +
                '}';
    }

}
