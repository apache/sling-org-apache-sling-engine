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

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;

import java.io.IOException;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.ErrorHandler;
import org.apache.sling.engine.impl.DefaultErrorHandler;
import org.apache.sling.engine.impl.SlingHttpServletResponseImpl;
import org.apache.sling.engine.impl.request.RequestData;
import org.junit.Test;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * This class tests the error filter chain in combination with the
 * {@link DefaultErrorHandler}.
 */
public class ErrorFilterChainTest {

    @Test
    public void testResponseCommitted() throws IOException, ServletException {
        final DefaultErrorHandler handler = new DefaultErrorHandler();
        final ErrorHandler errorHandler = Mockito.mock(ErrorHandler.class);
        handler.setDelegate(errorHandler);

        final SlingHttpServletRequest request = Mockito.mock(SlingHttpServletRequest.class);
        final SlingHttpServletResponse response = Mockito.mock(SlingHttpServletResponse.class);
        Mockito.when(response.isCommitted()).thenReturn(true);

        final ErrorFilterChain chain1 = new ErrorFilterChain(new FilterHandle[0], handler, new Exception());
        chain1.doFilter(request, response);

        final ErrorFilterChain chain2 = new ErrorFilterChain(new FilterHandle[0], handler, 500, "message");
        chain2.doFilter(request, response);

        Mockito.verify(errorHandler, never()).handleError(any(Throwable.class), eq(null), eq(response));
        Mockito.verify(errorHandler, never()).handleError(anyInt(), anyString(), eq(null), eq(response));
    }

    @Test
    public void testResponseNotCommitted() throws IOException, ServletException {
        final DefaultErrorHandler handler = new DefaultErrorHandler();
        final ErrorHandler errorHandler = Mockito.mock(ErrorHandler.class);
        handler.setDelegate(errorHandler);

        final SlingHttpServletRequest request = Mockito.mock(SlingHttpServletRequest.class);
        final SlingHttpServletResponse response = Mockito.mock(SlingHttpServletResponse.class);
        Mockito.when(response.isCommitted()).thenReturn(false);

        final ErrorFilterChain chain1 = new ErrorFilterChain(new FilterHandle[0], handler, new Exception());
        chain1.doFilter(request, response);
        Mockito.verify(errorHandler, times(1)).handleError(any(Throwable.class), eq(request), eq(response));

        final ErrorFilterChain chain2 = new ErrorFilterChain(new FilterHandle[0], handler, 500, "message");
        chain2.doFilter(request, response);
        Mockito.verify(errorHandler, times(1)).handleError(anyInt(), anyString(), eq(request), eq(response));
    }

    @Test
    public void testResponseDispatcherInfoOnError() throws IOException, ServletException {
        // mocks a final method in SlingHttpServletResponseImpl, needs mockito-inline
        final DefaultErrorHandler handler = new DefaultErrorHandler();
        final ErrorHandler errorHandler = Mockito.mock(ErrorHandler.class);
        handler.setDelegate(errorHandler);

        final SlingHttpServletRequest request = Mockito.mock(SlingHttpServletRequest.class);
        final SlingHttpServletResponseImpl response = Mockito.mock(SlingHttpServletResponseImpl.class);
        RequestData requestData = Mockito.mock(RequestData.class);
        when(response.getRequestData()).thenReturn(requestData);

        final ErrorFilterChain chain2 = new ErrorFilterChain(new FilterHandle[0], handler, 404, "not found");
        chain2.doFilter(request, response);
        verify(response, times(1)).reset();
        verify(requestData, times(1))
                .setDispatchingInfo(Mockito.argThat(info -> info.getType() == DispatcherType.ERROR));
    }
}
