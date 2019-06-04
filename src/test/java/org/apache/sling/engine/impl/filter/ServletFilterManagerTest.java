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

import org.apache.commons.lang3.ArrayUtils;
import org.apache.sling.engine.EngineConstants;
import org.apache.sling.engine.impl.filter.ServletFilterManager.FilterChainType;
import org.apache.sling.testing.mock.osgi.context.ContextCallback;
import org.apache.sling.testing.mock.osgi.context.OsgiContextImpl;
import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.apache.sling.testing.mock.osgi.junit.OsgiContextBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.osgi.framework.BundleContext;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;
import java.util.Hashtable;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ServletFilterManagerTest {

    @Rule
    public OsgiContext osgiContext = new OsgiContextBuilder()
            .afterSetUp(new ContextCallback<OsgiContextImpl>() {
                @Override
                public void execute(OsgiContextImpl context) throws Exception {
                    servletFilterManager = new ServletFilterManager(context.bundleContext(), null);
                    servletFilterManager.open();
                }
            })
            .build();

    private ServletFilterManager servletFilterManager;

    @Test
    public void registerFilterWithMultipleScopes() throws Exception {

        FilterChainType[] scopes = {FilterChainType.REQUEST, FilterChainType.INCLUDE};

        TestFilter testFilter = registerFilterForScopes(osgiContext.bundleContext(), scopes);

        assertFilterInScopes(servletFilterManager, testFilter, scopes);
    }

    @Test
    public void registerFilterWithComponentScope() throws Exception {

        TestFilter testFilter = registerFilterForScopes(osgiContext.bundleContext(), FilterChainType.COMPONENT);

        // COMPONENT implies INCLUDE ande FORWARD
        assertFilterInScopes(servletFilterManager, testFilter,
                FilterChainType.COMPONENT, FilterChainType.INCLUDE, FilterChainType.FORWARD);
    }

    @Test
    public void registerFilterWithAllScopes() throws Exception {

        FilterChainType[] allScopes = FilterChainType.values();

        TestFilter testFilter = registerFilterForScopes(osgiContext.bundleContext(), allScopes);

        assertFilterInScopes(servletFilterManager, testFilter, allScopes);
    }


    private static TestFilter registerFilterForScopes(BundleContext bundleContext, FilterChainType... scopes) {
        String[] scopeNames = new String[scopes.length];
        for (int i = 0; i < scopes.length; i++) {
            scopeNames[i] = scopes[i].name();
        }

        TestFilter testFilter = new TestFilter();
        Hashtable<String, Object> properties = new Hashtable<>();
        properties.put(EngineConstants.SLING_FILTER_SCOPE, scopeNames);
        bundleContext.registerService(Filter.class, testFilter, properties);
        return testFilter;
    }

    private static void assertFilterInScopes(ServletFilterManager mgr, Filter filterInstance, FilterChainType... scopes) {
        for (FilterChainType scope : FilterChainType.values()) {
            if (ArrayUtils.contains(scopes, scope)) {
                assertTrue("Expected filter in scope " + scope.name(), hasFilterInScope(mgr, filterInstance, scope));
            } else {
                assertFalse("Unexpected filter in scope " + scope.name(), hasFilterInScope(mgr, filterInstance, scope));
            }
        }
    }

    private static boolean hasFilterInScope(ServletFilterManager mgr, Filter instance, FilterChainType scope) {
        FilterHandle[] filterHandles = mgr.getFilters(scope);
        for (FilterHandle filterHandle : filterHandles) {
            if (filterHandle.getFilter() == instance) {
                return true;
            }
        }
        return false;
    }


    private static class TestFilter implements Filter {

        @Override
        public void init(FilterConfig filterConfig) throws ServletException {

        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
            chain.doFilter(request, response);
        }

        @Override
        public void destroy() {

        }
    }
}
