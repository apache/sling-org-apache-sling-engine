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

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Filter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import static org.apache.sling.engine.EngineConstants.SLING_FILTER_PATTERN;
import static org.apache.sling.engine.EngineConstants.SLING_FILTER_EXTENSIONS;
import static org.apache.sling.engine.EngineConstants.SLING_FILTER_METHODS;
import static org.apache.sling.engine.EngineConstants.SLING_FILTER_PATHS;
import static org.apache.sling.engine.EngineConstants.SLING_FILTER_RESOURCETYPES;
import static org.apache.sling.engine.EngineConstants.SLING_FILTER_SELECTORS;
import static org.apache.sling.engine.EngineConstants.SLING_FILTER_PATTERN_SUFFIX;

/**
 * Contains a set of predicates that helps testing whether to enable a filter for a request or not
 * it can only be constructed from a filter service reference, from whose properties it builds its
 * predicates
 */
public class FilterPredicate {
    private static final Logger LOG = LoggerFactory.getLogger(FilterPredicate.class);

    /**
     * Predicate indicating for a given request if we should consider it.
     */
    interface RequestPredicate {
        boolean test(SlingHttpServletRequest request);
    }

    /**
     * sub predicate based on a pattern
     */
    abstract class RequestMatchesPredicate implements RequestPredicate {
        Pattern pattern;
        RequestMatchesPredicate(Pattern pattern){
            this.pattern = pattern;
        }
        @Override
        public boolean test(SlingHttpServletRequest request) {
            return pattern.matcher(getItem(request)).matches();
        }
        abstract String getItem(SlingHttpServletRequest request);
    }

    /**
     * sub predicate based on presence of a string into a collection
     */
    abstract class RequestContainPredicate implements RequestPredicate {
        Collection<String> items;
        RequestContainPredicate(Collection<String> items){
            this.items = items;
        }

        @Override
        public boolean test(SlingHttpServletRequest request) {
            if (items.size() > 0){
                return items.contains(getItem(request));
            }
            return false;
        }

        abstract String getItem(SlingHttpServletRequest request);
    }

    private static final String [] PREDICATES = new String[]{
            SLING_FILTER_SELECTORS,
            SLING_FILTER_PATHS,
            SLING_FILTER_METHODS,
            SLING_FILTER_EXTENSIONS,
            SLING_FILTER_PATTERN_SUFFIX,
            SLING_FILTER_PATTERN};

    Collection<String> selectors;
    Collection<String> extensions;
    Collection<String> resourceTypes;
    Collection<String> methods;
    Collection<String> paths;
    Pattern pathRegex;
    Pattern suffixRegex;

    List<RequestPredicate> predicates;

    /*
     * @param reference osgi service configuration
     */
    public FilterPredicate(ServiceReference<Filter> reference) {
        try {
            for (String key : PREDICATES) {
                Object value = reference.getProperty(key);
                if (value != null) {
                    if (predicates == null) {
                        predicates = new ArrayList<>();
                    }
                    if ((SLING_FILTER_PATTERN.equals(key))) {
                        pathRegex = Pattern.compile((String) value);
                        predicates.add(new RequestMatchesPredicate(pathRegex) {
                            @Override
                            public String getItem(SlingHttpServletRequest request) {
                                String path = request.getRequestPathInfo().getResourcePath();
                                return StringUtils.isBlank(path) ? "/" : path;
                            }
                        });
                    } else if (SLING_FILTER_METHODS.equals(key)) {
                        methods = Arrays.asList((String[]) value);
                        predicates.add(new RequestContainPredicate(methods){
                            @Override
                            public String getItem(SlingHttpServletRequest request) {
                                return request.getMethod();
                            }
                        });
                    } else if (SLING_FILTER_PATTERN_SUFFIX.equals(key)) {
                        suffixRegex = Pattern.compile((String) value);
                        predicates.add(new RequestMatchesPredicate(suffixRegex) {
                            @Override
                            public String getItem(SlingHttpServletRequest request) {
                                return request.getRequestPathInfo().getSuffix();
                            }
                        });
                    } else if (SLING_FILTER_EXTENSIONS.equals(key)) {
                        extensions = Arrays.asList((String[])value);
                        predicates.add(new RequestContainPredicate(extensions) {
                            @Override
                            public String getItem(SlingHttpServletRequest request) {
                                return request.getRequestPathInfo().getExtension();
                            }
                        });
                    } else if (SLING_FILTER_PATHS.equals(key)) {
                        paths = Arrays.asList((String[])value);
                        predicates.add(new RequestContainPredicate(paths) {
                            @Override
                            public String getItem(SlingHttpServletRequest request) {
                                return request.getRequestPathInfo().getResourcePath();
                            }
                        });
                    } else if (SLING_FILTER_RESOURCETYPES.equals(key)) {
                        resourceTypes = Arrays.asList((String[])value);
                        if (resourceTypes.size() > 0) {
                            predicates.add(new RequestPredicate() {
                                @Override
                                public boolean test(SlingHttpServletRequest request) {
                                    for (String type : resourceTypes) {
                                        if (request.getResource().isResourceType(type)) {
                                            return true;
                                        }
                                    }
                                    return false;
                                }
                            });
                        }
                    } else if (SLING_FILTER_SELECTORS.equals(key)) {
                        selectors = Arrays.asList((String[])value);
                        if (selectors.size() > 0) {
                            predicates.add(new RequestPredicate() {
                                @Override
                                public boolean test(SlingHttpServletRequest request) {
                                    String[] sels = request.getRequestPathInfo().getSelectors();
                                    if (sels != null) {
                                        for (String sel : sels) {
                                            if (selectors.contains(sel)){
                                                return true;
                                            }
                                        }
                                    }
                                    return false;
                                }
                            });
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("unable to build up filter predicate, will consider no predicate here", e);
            predicates = null;
        }
    }

    /**
     * @param req request that is tested upon
     * @return true if this instance's configuration match the request
     */
    boolean test(SlingHttpServletRequest req) {
        LOG.debug("starting filter test against {} request", req);
        boolean select = true;
        if ((predicates != null) && (predicates.size() > 0)){//null in case there has been no predicate configuration for this filter
            //in case it's not empty, all predicates present must pass
            for (RequestPredicate p : predicates){
                select &= p.test(req);
                if (! select){
                    break;
                }
            }
        }
        LOG.debug("selection of {} returned {}", this, select);
        return select;
    }

    @Override
    public String toString() {
        return "FilterPredicate{" +
                "methods='" + methods + '\'' +
                ", pathRegex=" + pathRegex +
                ", suffixRegex=" + suffixRegex +
                ", selectors='" + selectors + '\'' +
                ", extensions='" + extensions + '\'' +
                ", paths='" + paths + '\'' +
                '}';
    }

}
