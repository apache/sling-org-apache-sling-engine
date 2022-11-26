/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.engine.impl;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.io.IOException;

import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.engine.servlets.ErrorHandler;
import org.junit.Test;
import org.mockito.Mockito;

public class DefaultErrorHandlerTest {

    @Test public void testResponseCommitted() throws IOException {
        final DefaultErrorHandler handler = new DefaultErrorHandler();
        final ErrorHandler errorHandler = Mockito.mock(ErrorHandler.class);
        handler.setDelegate(errorHandler);
        final SlingHttpServletResponse response = Mockito.mock(SlingHttpServletResponse.class);
        Mockito.when(response.isCommitted()).thenReturn(true);

        handler.handleError(new Exception(), null, response);
        handler.handleError(500, "message", null, response);

        Mockito.verify(errorHandler, never()).handleError(any(Throwable.class), eq(null), eq(response));
        Mockito.verify(errorHandler, never()).handleError(anyInt(), anyString(), eq(null), eq(response));
    }

    @Test public void testResponseNotCommitted() throws IOException {
        final DefaultErrorHandler handler = new DefaultErrorHandler();
        final ErrorHandler errorHandler = Mockito.mock(ErrorHandler.class);
        handler.setDelegate(errorHandler);
        final SlingHttpServletResponse response = Mockito.mock(SlingHttpServletResponse.class);
        Mockito.when(response.isCommitted()).thenReturn(false);

        handler.handleError(new Exception(), null, response);
        Mockito.verify(errorHandler, times(1)).handleError(any(Throwable.class), eq(null), eq(response));
        handler.handleError(500, "message", null, response);
        Mockito.verify(errorHandler, times(1)).handleError(anyInt(), anyString(), eq(null), eq(response));
    }
}
