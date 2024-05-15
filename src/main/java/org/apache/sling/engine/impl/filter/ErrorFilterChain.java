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

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import java.io.IOException;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.ErrorHandler;

public class ErrorFilterChain extends AbstractSlingFilterChain {

    private static final String RECURSION_ATTRIBUTE = ErrorFilterChain.class.getName() + ".RECURSION";

    private static final String PREFIX_COMMITTED = "handleError: Response already committed; cannot send error ";

    private static final String PREFIX_RECURSION = "handleError: Recursive invocation. Not further handling status ";

    private enum Mode {
        THROWABLE,
        STATUS
    };

    private final int status;

    private final String message;

    private final ErrorHandler errorHandler;

    private final Throwable throwable;

    private final Mode mode;

    private boolean firstCall = true;

    public ErrorFilterChain(
            final FilterHandle[] filters, final ErrorHandler errorHandler, final int status, final String message) {
        super(filters);
        this.mode = Mode.STATUS;
        this.status = status;
        this.message = message;
        this.errorHandler = errorHandler;
        this.throwable = null;
    }

    public ErrorFilterChain(final FilterHandle[] filters, final ErrorHandler errorHandler, final Throwable t) {
        super(filters);
        this.mode = Mode.THROWABLE;
        this.status = 0;
        this.message = null;
        this.throwable = t;
        this.errorHandler = errorHandler;
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response)
            throws ServletException, IOException {
        if (firstCall) {
            if (request.getAttribute(RECURSION_ATTRIBUTE) != null) {
                if (this.mode == Mode.STATUS) {
                    if (message == null) {
                        LOG.warn(PREFIX_RECURSION.concat(String.valueOf(status)));
                    } else {
                        LOG.warn(PREFIX_RECURSION
                                .concat(String.valueOf(status))
                                .concat(" : ")
                                .concat(message));
                    }
                } else {
                    if (throwable.getMessage() != null) {
                        LOG.warn(PREFIX_RECURSION.concat(throwable.getMessage()), throwable);
                    } else {
                        LOG.warn(PREFIX_RECURSION.concat(throwable.getClass().getName()), throwable);
                    }
                }
                return;
            }
            request.setAttribute(RECURSION_ATTRIBUTE, "true");
            firstCall = false;
            // do nothing if response is already committed
            if (response.isCommitted()) {
                if (this.mode == Mode.STATUS) {
                    if (message == null) {
                        LOG.warn(PREFIX_COMMITTED.concat(String.valueOf(status)));
                    } else {
                        LOG.warn(PREFIX_COMMITTED
                                .concat(String.valueOf(status))
                                .concat(" : ")
                                .concat(message));
                    }
                } else {
                    if (throwable.getMessage() != null) {
                        LOG.warn(PREFIX_COMMITTED.concat(throwable.getMessage()), throwable);
                    } else {
                        LOG.warn(PREFIX_COMMITTED.concat(throwable.getClass().getName()), throwable);
                    }
                }
                return;
            }
            // reset the response to clear headers and body
            response.reset();
        }
        super.doFilter(request, response);
    }

    protected void render(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
            throws IOException, ServletException {
        if (this.mode == Mode.STATUS) {
            this.errorHandler.handleError(this.status, this.message, request, response);
        } else {
            this.errorHandler.handleError(this.throwable, request, response);
        }
    }
}
