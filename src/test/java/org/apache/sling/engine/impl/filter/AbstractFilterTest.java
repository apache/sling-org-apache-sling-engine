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

import java.util.Dictionary;
import java.util.Hashtable;

import jakarta.servlet.Filter;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.request.builder.Builders;
import org.mockito.Mockito;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceReference;

public abstract class AbstractFilterTest {

    protected ServiceReference<Filter> mockService(Object... map) {

        final Dictionary<String, Object> props = new Hashtable<>();
        for (int i = 0; i < map.length; i += 2) {
            props.put(map[i].toString(), map[i + 1]);
        }

        ServiceReference<Filter> ref = new ServiceReference<Filter>() {
            @Override
            public Object getProperty(String key) {
                return props.get(key);
            }

            @Override
            public String[] getPropertyKeys() {
                return new String[0];
            }

            @Override
            public Bundle getBundle() {
                return null;
            }

            @Override
            public Bundle[] getUsingBundles() {
                return new Bundle[0];
            }

            @Override
            public boolean isAssignableTo(Bundle bundle, String className) {
                return false;
            }

            @Override
            public int compareTo(Object reference) {
                return 0;
            }

            @Override
            public Dictionary<String, Object> getProperties() {
                return props;
            }
        };
        return ref;
    }

    protected SlingJakartaHttpServletRequest mockRequest(
            final String path,
            final String extension,
            final String[] selectors,
            final String method,
            final String suffix) {
        final RequestPathInfo info = Mockito.mock(RequestPathInfo.class, "info " + path + extension + method + suffix);
        Mockito.when(info.getExtension()).thenReturn(extension);
        Mockito.when(info.getSuffix()).thenReturn(suffix);
        Mockito.when(info.getSelectors()).thenReturn(selectors == null ? new String[0] : selectors);
        Mockito.when(info.getResourcePath()).thenReturn(path);

        final SlingJakartaHttpServletRequest req =
                Mockito.mock(SlingJakartaHttpServletRequest.class, "req " + path + extension + method + suffix);
        Mockito.when(req.getRequestProgressTracker()).thenReturn(Builders.newRequestProgressTracker());
        Mockito.when(req.getRequestPathInfo()).thenReturn(info);
        Mockito.when(req.getMethod()).thenReturn(method);
        Mockito.when(req.getPathInfo()).thenReturn(path);
        Mockito.when(req.getServletPath()).thenReturn(path);
        Mockito.when(req.getAttribute(Mockito.anyString())).thenReturn(new Object());
        return req;
    }

    protected SlingJakartaHttpServletRequest mockRequest(
            final String resourcePath, final String requestPath, final String extension) {
        final RequestPathInfo info =
                Mockito.mock(RequestPathInfo.class, "info " + resourcePath + requestPath + extension);
        Mockito.when(info.getExtension()).thenReturn(extension);
        Mockito.when(info.getSuffix()).thenReturn(null);
        Mockito.when(info.getSelectors()).thenReturn(new String[0]);
        Mockito.when(info.getResourcePath()).thenReturn(resourcePath);

        final SlingJakartaHttpServletRequest req =
                Mockito.mock(SlingJakartaHttpServletRequest.class, "req " + resourcePath + requestPath + extension);
        Mockito.when(req.getRequestPathInfo()).thenReturn(info);
        Mockito.when(req.getMethod()).thenReturn(null);
        Mockito.when(req.getPathInfo()).thenReturn(requestPath);
        return req;
    }

    protected FilterPredicate predicate(Object... args) {
        FilterPredicate predicate = new FilterPredicate(mockService(args));
        return predicate;
    }

    protected SlingJakartaHttpServletRequest whateverRequest() {
        return mockRequest("/content/test/what/ever", "json", new String[] {"test"}, "GET", null);
    }
}
