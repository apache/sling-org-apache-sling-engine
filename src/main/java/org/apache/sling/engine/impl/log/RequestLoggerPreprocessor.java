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
package org.apache.sling.engine.impl.log;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import java.io.IOException;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.propertytypes.ServiceRanking;
import org.osgi.service.http.whiteboard.Preprocessor;

/**
 * Record the starting time of the request and make it available to the request logger
 */
@Component(service = Preprocessor.class)
@ServiceRanking(Integer.MAX_VALUE)
public class RequestLoggerPreprocessor implements Preprocessor {

    private static final String ATTR_NAME = Preprocessor.class.getName() + ".startTime";

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {
        request.setAttribute(ATTR_NAME, System.currentTimeMillis());
        try {
            chain.doFilter(request, response);
        } finally {
            request.removeAttribute(ATTR_NAME);
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // nothing to do
    }

    @Override
    public void destroy() {
        // nothing to do
    }

    public static long getRequestStartTime(final ServletRequest request) {
        final Object val = request == null ? null : request.getAttribute(ATTR_NAME);
        if (val instanceof Long) {
            return (Long) val;
        }
        // default is *now*
        return System.currentTimeMillis();
    }
}
