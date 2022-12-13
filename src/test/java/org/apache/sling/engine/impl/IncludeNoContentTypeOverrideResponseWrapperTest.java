/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 ~ Licensed to the Apache Software Foundation (ASF) under one
 ~ or more contributor license agreements.  See the NOTICE file
 ~ distributed with this work for additional information
 ~ regarding copyright ownership.  The ASF licenses this file
 ~ to you under the Apache License, Version 2.0 (the
 ~ "License"); you may not use this file except in compliance
 ~ with the License.  You may obtain a copy of the License at
 ~
 ~   http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing,
 ~ software distributed under the License is distributed on an
 ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 ~ KIND, either express or implied.  See the License for the
 ~ specific language governing permissions and limitations
 ~ under the License.
 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
package org.apache.sling.engine.impl;


import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestProgressTracker;
import org.apache.sling.engine.impl.request.RequestData;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IncludeNoContentTypeOverrideResponseWrapperTest {

    private static final String ACTIVE_SERVLET_NAME = "activeServlet";

    @Test
    public void testContentTypeOverrideThrows() {
        RequestData requestData = mock(RequestData.class);
        when(requestData.getActiveServletName()).thenReturn(ACTIVE_SERVLET_NAME);
        RequestProgressTracker requestProgressTracker = mock(RequestProgressTracker.class);
        when(requestData.getRequestProgressTracker()).thenReturn(requestProgressTracker);
        SlingHttpServletResponse response = mock(SlingHttpServletResponse.class);
        when(response.getContentType()).thenReturn("text/html");


        IncludeNoContentTypeOverrideResponseWrapper wrapper = new IncludeNoContentTypeOverrideResponseWrapper(requestData,
                response
        );

        Throwable throwable = null;
        try {
            wrapper.setContentType("application/json");
        } catch (Throwable t) {
            throwable = t;
        }
        assertNotNull(throwable);
        assertEquals("Servlet activeServlet tried to override the 'Content-Type' header from 'text/html' to " +
                "'application/json', however the org.apache.sling.engine.impl.SlingMainServlet forbids this via the " +
                "sling.includes.checkcontenttype configuration property.", throwable.getMessage());
    }

    @Test
    public void testContentTypeOverrideLenient() throws Throwable {
        RequestData requestData = mock(RequestData.class);
        when(requestData.getActiveServletName()).thenReturn(ACTIVE_SERVLET_NAME);
        RequestProgressTracker requestProgressTracker = mock(RequestProgressTracker.class);
        when(requestData.getRequestProgressTracker()).thenReturn(requestProgressTracker);
        SlingHttpServletResponse response = mock(SlingHttpServletResponse.class);
        when(response.getContentType()).thenReturn("text/html");
        doAnswer(invocationOnMock -> {
            String setContentType = invocationOnMock.getArgument(0);
            when(response.getContentType()).thenReturn(setContentType);
            return null;
        }).when(response).setContentType(anyString());


        IncludeNoContentTypeOverrideResponseWrapper wrapper = new IncludeNoContentTypeOverrideResponseWrapper(requestData,
                response
        );

        Throwable throwable = null;
        try {
            wrapper.setContentType("text/html;utf-8");
        } catch (Throwable t) {
            throwable = t;
        }
        if (throwable != null) {
            throw throwable;
        }
        assertEquals("text/html;utf-8", wrapper.getContentType());

        try {
            wrapper.setContentType(null);
        } catch (Throwable t) {
            throwable = t;
        }
        assertNotNull(throwable);
        assertEquals("Servlet activeServlet tried to override the 'Content-Type' header from 'text/html;utf-8' to " +
                "'null', however the org.apache.sling.engine.impl.SlingMainServlet forbids this via the sling" +
                ".includes.checkcontenttype configuration property.", throwable.getMessage());
        assertEquals("text/html;utf-8", wrapper.getContentType());
    }

}
