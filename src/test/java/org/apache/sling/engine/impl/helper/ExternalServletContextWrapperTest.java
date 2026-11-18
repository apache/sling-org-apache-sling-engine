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
package org.apache.sling.engine.impl.helper;

import java.util.Collections;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.ServletResponseWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.apache.sling.engine.impl.SlingJakartaHttpServletRequestImpl;
import org.apache.sling.engine.impl.SlingJakartaHttpServletResponseImpl;
import org.apache.sling.engine.impl.SlingRequestProcessorImpl;
import org.apache.sling.engine.impl.helper.ExternalServletContextWrapper.RequestDispatcherWrapper;
import org.apache.sling.engine.impl.request.RequestData;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.*;

public class ExternalServletContextWrapperTest {

    /**
     * Tests that the RequestDispatcher is wrapped.
     */
    @Test
    public void testGetRequestDispatcher() {
        final RequestDispatcher rd = Mockito.mock(RequestDispatcher.class);
        final ServletContext ctx = Mockito.mock(ServletContext.class);
        Mockito.when(ctx.getRequestDispatcher("foo.jsp")).thenReturn(rd);

        ExternalServletContextWrapper wrapper = new ExternalServletContextWrapper(ctx);
        RequestDispatcher dispatcher = wrapper.getRequestDispatcher("foo.jsp");

        assertTrue(dispatcher instanceof RequestDispatcherWrapper);
        assertEquals(rd, ((RequestDispatcherWrapper) dispatcher).getDelegate());
        Mockito.verify(ctx).getRequestDispatcher("foo.jsp");
    }

    /**
     * Unwrapping a non-wrapper request should return the request itself.
     */
    @Test
    public void testUnwrappingRegularRequest() {
        final ServletRequest req = Mockito.mock(ServletRequest.class);

        ServletRequest unwrapped = ExternalServletContextWrapper.RequestDispatcherWrapper.unwrapServletRequest(req);

        assertEquals(req, unwrapped);
    }

    /**
     * Unwrapping a wrapper request should return in the request.
     */
    @Test
    public void testUnwrappingWrappedRequest() {
        final ServletRequest req = Mockito.mock(ServletRequest.class);
        final ServletRequestWrapper wrapper = new ServletRequestWrapper(req);

        ServletRequest unwrapped = ExternalServletContextWrapper.RequestDispatcherWrapper.unwrapServletRequest(wrapper);

        assertEquals(req, unwrapped);
    }

    @Test
    public void testUnwrappingDoubleWrappedRequest() {
        final ServletRequest req = Mockito.mock(ServletRequest.class);
        final ServletRequestWrapper wrapper = new ServletRequestWrapper(req);
        final ServletRequestWrapper wrapper2 = new ServletRequestWrapper(wrapper);

        ServletRequest unwrapped =
                ExternalServletContextWrapper.RequestDispatcherWrapper.unwrapServletRequest(wrapper2);

        assertEquals(req, unwrapped);
    }

    /**
     * Unwrapping a sling request should return the first-level request wrapped
     * by the sling request.
     */
    @Test
    public void testUnwrappingSlingRequest() {
        final HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        Mockito.when(req.getServletPath()).thenReturn("/");
        Mockito.when(req.getPathInfo()).thenReturn("/test");

        final HttpServletRequestWrapper wrapper = new HttpServletRequestWrapper(req);
        final HttpServletRequestWrapper wrapper2 = new HttpServletRequestWrapper(wrapper);
        final SlingJakartaHttpServletRequestImpl slingRequest = new SlingJakartaHttpServletRequestImpl(null, wrapper2);

        ServletRequest unwrapped =
                ExternalServletContextWrapper.RequestDispatcherWrapper.unwrapServletRequest(slingRequest);

        assertEquals(wrapper2, unwrapped);
    }

    /**
     * Unwrapping a wrapped sling request should return the first-level request
     * wrapped by the sling request.
     */
    @Test
    public void testUnwrappingWrappedSlingRequest() {
        final HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        Mockito.when(req.getServletPath()).thenReturn("/");
        Mockito.when(req.getPathInfo()).thenReturn("/test");

        final HttpServletRequestWrapper wrapper = new HttpServletRequestWrapper(req);
        final HttpServletRequestWrapper wrapper2 = new HttpServletRequestWrapper(wrapper);
        final SlingJakartaHttpServletRequestImpl slingRequest = new SlingJakartaHttpServletRequestImpl(null, wrapper2);
        final HttpServletRequestWrapper slingWrapper = new HttpServletRequestWrapper(slingRequest);

        ServletRequest unwrapped =
                ExternalServletContextWrapper.RequestDispatcherWrapper.unwrapServletRequest(slingWrapper);

        assertEquals(wrapper2, unwrapped);
    }

    /**
     * Unwrapping a non-wrapper response should return the response itself.
     */
    @Test
    public void testUnwrappingRegularResponse() {
        final ServletResponse req = Mockito.mock(ServletResponse.class);

        ServletResponse unwrapped = ExternalServletContextWrapper.RequestDispatcherWrapper.unwrapServletResponse(req);

        assertEquals(req, unwrapped);
    }

    /**
     * Unwrapping a wrapper response should return in the response.
     */
    @Test
    public void testUnwrappingWrappedResponse() {
        final ServletResponse resp = Mockito.mock(ServletResponse.class);
        final ServletResponseWrapper wrapper = new ServletResponseWrapper(resp);

        ServletResponse unwrapped =
                ExternalServletContextWrapper.RequestDispatcherWrapper.unwrapServletResponse(wrapper);

        assertEquals(resp, unwrapped);
    }

    @Test
    public void testUnwrappingDoubleWrappedResponse() {
        final ServletResponse resp = Mockito.mock(ServletResponse.class);
        final ServletResponseWrapper wrapper = new ServletResponseWrapper(resp);
        final ServletResponseWrapper wrapper2 = new ServletResponseWrapper(wrapper);

        ServletResponse unwrapped =
                ExternalServletContextWrapper.RequestDispatcherWrapper.unwrapServletResponse(wrapper2);

        assertEquals(resp, unwrapped);
    }

    /**
     * Unwrapping a sling response should return the first-level response wrapped
     * by the sling response.
     */
    @Test
    public void testUnwrappingSlingResponse() {
        final HttpServletResponse resp = Mockito.mock(HttpServletResponse.class);
        final HttpServletResponseWrapper wrapper = new HttpServletResponseWrapper(resp);
        final HttpServletResponseWrapper wrapper2 = new HttpServletResponseWrapper(wrapper);
        final RequestData rd = Mockito.mock(RequestData.class);
        final SlingRequestProcessorImpl processor = Mockito.mock(SlingRequestProcessorImpl.class);
        Mockito.when(rd.getSlingRequestProcessor()).thenReturn(processor);
        Mockito.when(processor.getAdditionalResponseHeaders()).thenReturn(Collections.emptyList());

        final SlingJakartaHttpServletResponseImpl slingResponse = new SlingJakartaHttpServletResponseImpl(rd, wrapper2);

        ServletResponse unwrapped =
                ExternalServletContextWrapper.RequestDispatcherWrapper.unwrapServletResponse(slingResponse);

        assertEquals(wrapper2, unwrapped);
    }

    /**
     * Unwrapping a wrapped sling response should return the first-level response
     * wrapped by the sling response.
     */
    @Test
    public void testUnwrappingWrappedSlingResponse() {
        final HttpServletResponse resp = Mockito.mock(HttpServletResponse.class);
        final HttpServletResponseWrapper wrapper = new HttpServletResponseWrapper(resp);
        final HttpServletResponseWrapper wrapper2 = new HttpServletResponseWrapper(wrapper);
        final RequestData rd = Mockito.mock(RequestData.class);
        final SlingRequestProcessorImpl processor = Mockito.mock(SlingRequestProcessorImpl.class);
        Mockito.when(rd.getSlingRequestProcessor()).thenReturn(processor);
        Mockito.when(processor.getAdditionalResponseHeaders()).thenReturn(Collections.emptyList());

        final SlingJakartaHttpServletResponseImpl slingResponse = new SlingJakartaHttpServletResponseImpl(rd, wrapper2);
        final HttpServletResponseWrapper slingWrapper = new HttpServletResponseWrapper(slingResponse);

        ServletResponse unwrapped =
                ExternalServletContextWrapper.RequestDispatcherWrapper.unwrapServletResponse(slingWrapper);

        assertEquals(wrapper2, unwrapped);
    }
}
