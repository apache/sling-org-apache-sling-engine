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
package org.apache.sling.engine.impl;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SlingMainServletTest {
    @Rule
    public final OsgiContext osgiContext = new OsgiContext();

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private SlingMainServlet servlet;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        // Provide mandatory ProductInfoProvider reference (mock is sufficient for activation)
        osgiContext.registerService(ProductInfoProvider.class, Mockito.mock(ProductInfoProvider.class));

        // Provide a ServletContext service with the expected name property to satisfy the target filter
        Dictionary<String, Object> props = new Hashtable<>();
        props.put("name", SlingHttpContext.SERVLET_CONTEXT_NAME);
        osgiContext.bundleContext().registerService(ServletContext.class, Mockito.mock(ServletContext.class), props);

        // Satisfy mandatory reference to SlingRequestProcessorImpl
        osgiContext.registerService(SlingRequestProcessorImpl.class, Mockito.mock(SlingRequestProcessorImpl.class));

        // Activate SlingMainServlet with OSGi config
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("sling_trace_allow", false);
        cfg.put("servlet_name", "test-servlet");
        servlet = osgiContext.registerInjectActivateService(SlingMainServlet.class, cfg);
    }

    @Test
    public void testTraceDisabledReturns405AndAllowHeader() throws ServletException {
        when(request.getMethod()).thenReturn("TRACE");
        // Act
        servlet.service(request, response);

        // Assert
        verify(response, times(1)).setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verify(response, times(1)).setHeader("Allow", "GET, HEAD, POST, PUT, DELETE, OPTIONS");
    }
}
