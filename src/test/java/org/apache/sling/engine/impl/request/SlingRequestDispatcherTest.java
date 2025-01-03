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
package org.apache.sling.engine.impl.request;

import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestDispatcherOptions;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.api.servlets.ServletResolver;
import org.apache.sling.engine.impl.SlingJakartaHttpServletRequestImpl;
import org.apache.sling.engine.impl.SlingJakartaHttpServletResponseImpl;
import org.apache.sling.engine.impl.SlingRequestProcessorImpl;
import org.apache.sling.engine.impl.filter.FilterHandle;
import org.apache.sling.engine.impl.filter.ServletFilterManager;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SlingRequestDispatcherTest {

    @Test
    public void testForwardResponseBufferClosed() throws Exception {
        Servlet forwardedServlet = mock(Servlet.class);
        doAnswer(invocationOnMock -> {
                    ServletResponse servletResponse = invocationOnMock.getArgument(1);
                    when(servletResponse.isCommitted()).thenReturn(true);
                    doThrow(new IOException("Response is committed"))
                            .when(servletResponse)
                            .getWriter();
                    doThrow(new IOException("Response is committed"))
                            .when(servletResponse)
                            .getOutputStream();
                    doThrow(new IOException("Response is committed"))
                            .when(servletResponse)
                            .flushBuffer();
                    verify(servletResponse, never()).flushBuffer();
                    return null;
                })
                .when(forwardedServlet)
                .service(any(ServletRequest.class), any(ServletResponse.class));
        testForwarding(forwardedServlet);
    }

    @Test
    public void testForwardResponseBufferNotClosed() throws Exception {
        Servlet forwardedServlet = mock(Servlet.class);
        AtomicReference<ServletResponse> outerResponse = new AtomicReference<>();
        doAnswer(invocationOnMock -> {
                    ServletResponse servletResponse = invocationOnMock.getArgument(1);
                    when(servletResponse.isCommitted()).thenReturn(false);
                    outerResponse.set(servletResponse);
                    return null;
                })
                .when(forwardedServlet)
                .service(any(ServletRequest.class), any(ServletResponse.class));
        testForwarding(forwardedServlet);
        assertNotNull(outerResponse.get());
        verify(outerResponse.get(), times(1)).flushBuffer();
    }

    private @NotNull Resource getMockedResource(@NotNull String path) {
        Resource resource = mock(Resource.class);
        when(resource.getPath()).thenReturn(path);
        ResourceMetadata resourceMetadata = mock(ResourceMetadata.class);
        when(resource.getResourceMetadata()).thenReturn(resourceMetadata);
        when(resourceMetadata.getResolutionPathInfo()).thenReturn(path);
        return resource;
    }

    private void testForwarding(@NotNull Servlet servlet) throws Exception {
        Resource forwardResource = getMockedResource("/forward");

        RequestDispatcher requestDispatcher =
                new SlingRequestDispatcher(forwardResource, new RequestDispatcherOptions(), false, false);
        SlingRequestProcessorImpl slingRequestProcessor = new SlingRequestProcessorImpl();

        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        HttpServletResponse httpServletResponse = mock(HttpServletResponse.class);

        RequestData requestData =
                new RequestData(slingRequestProcessor, httpServletRequest, httpServletResponse, false, false, false);
        SlingHttpServletRequest request = spy(new SlingJakartaHttpServletRequestImpl(requestData, httpServletRequest));
        SlingHttpServletResponse response =
                spy(new SlingJakartaHttpServletResponseImpl(requestData, httpServletResponse));
        Resource initialResource = getMockedResource("/initial");
        when(request.getResource()).thenReturn(initialResource);

        ServletResolver servletResolver = mock(ServletResolver.class);
        when(servletResolver.resolveServlet(any(SlingHttpServletRequest.class))).thenReturn(servlet);
        Field servletResolverField = slingRequestProcessor.getClass().getDeclaredField("servletResolver");
        servletResolverField.setAccessible(true);
        servletResolverField.set(slingRequestProcessor, servletResolver);

        ServletFilterManager filterManager = mock(ServletFilterManager.class);
        when(filterManager.getFilters(any())).thenReturn(new FilterHandle[] {});
        Field filterManagerField = slingRequestProcessor.getClass().getDeclaredField("filterManager");
        filterManagerField.setAccessible(true);
        filterManagerField.set(slingRequestProcessor, filterManager);

        requestData.initServlet(initialResource, servletResolver);

        requestDispatcher.forward(request, response);
        verify(servlet).service(any(ServletRequest.class), any(ServletResponse.class));
    }
}
