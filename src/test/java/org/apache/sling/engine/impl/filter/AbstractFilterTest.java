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
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceReference;

public abstract class AbstractFilterTest {

    protected final Mockery context = new JUnit4Mockery();

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
        final RequestPathInfo info = context.mock(RequestPathInfo.class, "info " + path + extension + method + suffix);
        context.checking(new Expectations() {
            {
                allowing(info).getExtension();
                will(returnValue(extension));
                allowing(info).getSuffix();
                will(returnValue(suffix));
                allowing(info).getSelectors();
                will(returnValue(selectors == null ? new String[0] : selectors));
                allowing(info).getResourcePath();
                will(returnValue(path));
            }
        });

        final SlingJakartaHttpServletRequest req =
                context.mock(SlingJakartaHttpServletRequest.class, "req " + path + extension + method + suffix);
        context.checking(new Expectations() {
            {
                allowing(req).getRequestProgressTracker();
                will(returnValue(Builders.newRequestProgressTracker()));
                allowing(req).getRequestPathInfo();
                will(returnValue(info));
                allowing(req).getMethod();
                will(returnValue(method));
                allowing(req).getPathInfo();
                will(returnValue(path));
                allowing(req).getServletPath();
                will(returnValue(path));
                allowing(req).getAttribute(with(any(String.class)));
                will(returnValue(new Object()));
            }
        });
        return req;
    }

    protected SlingJakartaHttpServletRequest mockRequest(
            final String resourcePath, final String requestPath, final String extension) {
        final RequestPathInfo info =
                context.mock(RequestPathInfo.class, "info " + resourcePath + requestPath + extension);
        context.checking(new Expectations() {
            {
                allowing(info).getExtension();
                will(returnValue(extension));
                allowing(info).getSuffix();
                will(returnValue(null));
                allowing(info).getSelectors();
                will(returnValue(new String[0]));
                allowing(info).getResourcePath();
                will(returnValue(resourcePath));
            }
        });

        final SlingJakartaHttpServletRequest req =
                context.mock(SlingJakartaHttpServletRequest.class, "req " + resourcePath + requestPath + extension);
        context.checking(new Expectations() {
            {
                allowing(req).getRequestPathInfo();
                will(returnValue(info));
                allowing(req).getMethod();
                will(returnValue(null));
                allowing(req).getPathInfo();
                will(returnValue(requestPath));
            }
        });
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
