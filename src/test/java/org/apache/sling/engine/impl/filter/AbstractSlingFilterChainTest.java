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

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.engine.impl.SlingJakartaHttpServletRequestImpl;
import org.apache.sling.engine.impl.SlingRequestProcessorImpl;
import org.apache.sling.engine.impl.request.RequestData;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class AbstractSlingFilterChainTest extends AbstractFilterTest {

    @Test
    public void testDoubleCall() throws Exception {
        Filter badFilter = new Filter() {
            @Override
            public void init(FilterConfig filterConfig) throws ServletException {}

            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                    throws IOException, ServletException {
                chain.doFilter(request, response);
                chain.doFilter(request, response);
            }

            @Override
            public void destroy() {}
        };

        FilterHandle handle = new FilterHandle(badFilter, null, 1, 1, null, null);

        AbstractSlingFilterChain chain = new AbstractSlingFilterChain(new FilterHandle[] {handle}) {
            @Override
            protected void render(SlingJakartaHttpServletRequest request, SlingJakartaHttpServletResponse response)
                    throws IOException, ServletException {}
        };
        HttpServletRequest httpReq = whateverRequest();
        final RequestData requestData = new RequestData(
                new SlingRequestProcessorImpl(), httpReq, context.mock(HttpServletResponse.class), false, false, true);
        final SlingJakartaHttpServletRequestImpl req = new SlingJakartaHttpServletRequestImpl(requestData, httpReq);
        boolean illegalStateCaught = false;
        try {
            chain.doFilter(req, context.mock(SlingJakartaHttpServletResponse.class));
        } catch (IllegalStateException e) {
            illegalStateCaught = true;
        }
        assertTrue("an illegal state exception should have been caught", illegalStateCaught);
    }
}
