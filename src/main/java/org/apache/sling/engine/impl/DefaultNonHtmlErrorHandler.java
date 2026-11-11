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

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.servlets.JakartaErrorHandler;
import org.osgi.service.component.annotations.Component;

@Component(
        service = JakartaErrorHandler.class,
        property = {SlingRequestProcessorImpl.ERROR_HANDLER_NAME + "=" + DefaultErrorHandler.NON_HTML})
public class DefaultNonHtmlErrorHandler implements JakartaErrorHandler {

    @Override
    public void handleError(
            int status,
            String message,
            SlingJakartaHttpServletRequest request,
            SlingJakartaHttpServletResponse response)
            throws IOException {
        response.setStatus(status);
        response.setContentLength(0);
        // commit the response
        response.flushBuffer();
    }

    @Override
    public void handleError(
            Throwable throwable, SlingJakartaHttpServletRequest request, SlingJakartaHttpServletResponse response)
            throws IOException {
        this.handleError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, null, request, response);
    }
}
