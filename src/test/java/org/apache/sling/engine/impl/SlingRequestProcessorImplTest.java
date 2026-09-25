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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.request.RequestProgressTracker;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.ServletResolver;
import org.apache.sling.engine.impl.filter.FilterHandle;
import org.apache.sling.engine.impl.filter.ServletFilterManager;
import org.apache.sling.engine.impl.filter.ServletFilterManager.FilterChainType;
import org.apache.sling.engine.impl.parameters.SlingParameterParseException;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link SlingRequestProcessorImpl}, in particular the
 * {@code SlingParameterParseException} to HTTP 400 mapping performed in
 * {@code doProcessRequest}.
 */
public class SlingRequestProcessorImplTest {

    private SlingRequestProcessorImpl processor;
    private ServletFilterManager filterManager;
    private SlingJakartaHttpServletRequest request;
    private SlingJakartaHttpServletResponse response;
    private StringWriter responseBody;

    @Before
    public void setup() throws Exception {
        processor = new SlingRequestProcessorImpl();

        filterManager = mock(ServletFilterManager.class);
        when(filterManager.getFilters(FilterChainType.ERROR)).thenReturn(new FilterHandle[0]);
        setField(processor, "filterManager", filterManager);

        request = mock(SlingJakartaHttpServletRequest.class);
        when(request.getRequestProgressTracker()).thenReturn(mock(RequestProgressTracker.class));
        when(request.getRequestURI()).thenReturn("/test");

        response = mock(SlingJakartaHttpServletResponse.class);
        responseBody = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
    }

    private static void setField(final Object target, final String name, final Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    public void testHandleErrorWithNullMessageStillSetsStatus() throws Exception {
        processor.handleError(SC_BAD_REQUEST, null, request, response);

        verify(response).setStatus(SC_BAD_REQUEST);
        responseBody.flush();
        assertTrue(responseBody.toString().contains(String.valueOf(SC_BAD_REQUEST)));
    }

    /**
     * End-to-end regression test for SLING-13364: a
     * {@link SlingParameterParseException} raised while servicing a request
     * (here simulated by the resolved servlet, standing in for the parameter
     * parsing that {@code RequestData.service} triggers indirectly) must be
     * caught by {@code doProcessRequest} and mapped to a 400 response,
     * instead of propagating as a server error or being swallowed.
     */
    @Test
    public void testDoProcessRequestMapsParameterParseExceptionToBadRequest() throws Exception {
        final Servlet servlet = mock(Servlet.class);
        doThrow(new SlingParameterParseException("Error parsing query string", new IllegalArgumentException("bad")))
                .when(servlet)
                .service(any(ServletRequest.class), any(ServletResponse.class));

        final Resource resource = getMockedResource("/content/test");

        final ResourceResolver resourceResolver = mock(ResourceResolver.class);
        when(resourceResolver.resolve(any(HttpServletRequest.class), anyString()))
                .thenReturn(resource);

        final ServletResolver servletResolver = mock(ServletResolver.class);
        when(servletResolver.resolve(any(SlingJakartaHttpServletRequest.class))).thenReturn(servlet);
        setField(processor, "servletResolver", servletResolver);

        // no request/component filters: the chain falls straight through to
        // the resolved servlet, whose service() call raises the exception
        when(filterManager.getFilters(FilterChainType.REQUEST)).thenReturn(new FilterHandle[0]);
        when(filterManager.getFilters(FilterChainType.COMPONENT)).thenReturn(new FilterHandle[0]);

        final HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        when(httpServletRequest.getRequestURI()).thenReturn("/content/test");
        when(httpServletRequest.getRequestURL()).thenReturn(new StringBuffer("http://localhost/content/test"));
        when(httpServletRequest.getContextPath()).thenReturn("");
        when(httpServletRequest.getServletPath()).thenReturn("");
        when(httpServletRequest.getMethod()).thenReturn("GET");

        final HttpServletResponse httpServletResponse = mock(HttpServletResponse.class);
        final StringWriter writer = new StringWriter();
        when(httpServletResponse.getWriter()).thenReturn(new PrintWriter(writer));

        processor.doProcessRequest(httpServletRequest, httpServletResponse, resourceResolver);

        verify(httpServletResponse).setStatus(SC_BAD_REQUEST);
        writer.flush();
        assertTrue(writer.toString().contains("Error parsing query string"));
    }

    private static @NotNull Resource getMockedResource(final @NotNull String path) {
        final Resource resource = mock(Resource.class);
        when(resource.getPath()).thenReturn(path);
        final ResourceMetadata resourceMetadata = mock(ResourceMetadata.class);
        when(resource.getResourceMetadata()).thenReturn(resourceMetadata);
        when(resourceMetadata.getResolutionPathInfo()).thenReturn(path);
        return resource;
    }
}
