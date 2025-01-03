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
import java.io.PrintWriter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.request.RequestProgressTracker;
import org.apache.sling.api.request.ResponseUtil;
import org.apache.sling.api.servlets.ErrorHandler;
import org.apache.sling.api.servlets.JakartaErrorHandler;
import org.apache.sling.api.wrappers.JakartaToJavaxRequestWrapper;
import org.apache.sling.api.wrappers.JakartaToJavaxResponseWrapper;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.sling.api.SlingConstants.ERROR_REQUEST_URI;
import static org.apache.sling.api.SlingConstants.ERROR_SERVLET_NAME;

/**
 * The <code>DefaultErrorHandler</code> is used by the
 * {@link SlingRequestProcessorImpl} for error handling. It works
 * in combination with the error filter chain. If a {@link ErrorHandler} service
 * is registered, the actual response generated is delegated to that service.
 */
public class DefaultErrorHandler implements JakartaErrorHandler {

    /** default log */
    private final Logger log = LoggerFactory.getLogger(getClass());

    private volatile String serverInfo = ProductInfoProvider.PRODUCT_NAME;

    /** Use this if not null, and if that fails output a report about that failure */
    private volatile JakartaErrorHandler delegate;

    private volatile JakartaErrorHandler errorHandler;
    private volatile JakartaErrorHandler jakartaErrorHandler;
    private volatile ServiceReference<?> errorHandlerRef;
    private volatile ServiceReference<?> jakartaErrorHandlerRef;

    void setServerInfo(final String serverInfo) {
        this.serverInfo = (serverInfo != null) ? serverInfo : ProductInfoProvider.PRODUCT_NAME;
    }

    public synchronized void setDelegate(final ServiceReference<?> ref, final ErrorHandler eh) {
        if (eh != null) {
            this.errorHandler = new JakartaErrorHandler() {
                @Override
                public void handleError(
                        int status,
                        String message,
                        SlingJakartaHttpServletRequest request,
                        SlingJakartaHttpServletResponse response)
                        throws IOException {
                    eh.handleError(
                            status,
                            message,
                            (SlingHttpServletRequest) JakartaToJavaxRequestWrapper.toJavaxRequest(request),
                            (SlingHttpServletResponse) JakartaToJavaxResponseWrapper.toJavaxResponse(response));
                }

                @Override
                public void handleError(
                        Throwable throwable,
                        SlingJakartaHttpServletRequest request,
                        SlingJakartaHttpServletResponse response)
                        throws IOException {
                    eh.handleError(
                            throwable,
                            (SlingHttpServletRequest) JakartaToJavaxRequestWrapper.toJavaxRequest(request),
                            (SlingHttpServletResponse) JakartaToJavaxResponseWrapper.toJavaxResponse(response));
                }
            };
            this.errorHandlerRef = ref;
            if (this.jakartaErrorHandlerRef == null || this.jakartaErrorHandlerRef.compareTo(ref) < 0) {
                this.delegate = this.errorHandler;
            }
        } else {
            this.errorHandler = null;
            this.errorHandlerRef = null;
            this.delegate = this.jakartaErrorHandler;
        }
    }

    public synchronized void setDelegate(final ServiceReference<?> ref, final JakartaErrorHandler eh) {
        if (eh != null) {
            this.jakartaErrorHandler = eh;
            this.jakartaErrorHandlerRef = ref;
            if (this.errorHandlerRef == null || this.errorHandlerRef.compareTo(ref) < 0) {
                this.delegate = this.jakartaErrorHandler;
            }
        } else {
            this.jakartaErrorHandler = null;
            this.jakartaErrorHandlerRef = null;
            this.delegate = this.errorHandler;
        }
    }

    private void delegateFailed(
            int originalStatus,
            String originalMessage,
            Throwable t,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {
        // don't include Throwable in the response, gives too much information
        final String m = "Error handler failed:" + t.getClass().getName();
        log.error(m, t);

        if (response.isCommitted()) {
            log.warn("handleError: Response already committed; cannot send error " + originalStatus + " : "
                    + originalMessage);
            return;
        }
        // reset the response to clear headers and body
        // the error filters are NOT called in this edge case
        response.reset();
        sendError(originalStatus, originalMessage, null, request, response);
    }

    // ---------- ErrorHandler interface (default implementation) --------------

    /**
     * Backend implementation of the HttpServletResponse.sendError methods.
     * <p>
     * This implementation resets the response before sending back a
     * standardized response which just conveys the status, the message (either
     * provided or a message derived from the status code), and server
     * information.
     * <p>
     * This method logs error and does not write back and response data if the
     * response has already been committed.
     */
    @Override
    public void handleError(
            final int status,
            String message,
            final SlingJakartaHttpServletRequest request,
            final SlingJakartaHttpServletResponse response)
            throws IOException {
        // If we have a delegate let it handle the error
        if (delegate != null) {
            try {
                delegate.handleError(status, message, request, response);
            } catch (final Exception e) {
                delegateFailed(status, message, e, request, response);
            }
            return;
        }

        if (message == null) {
            message = "HTTP ERROR:" + String.valueOf(status);
        } else {
            message = "HTTP ERROR:" + status + " - " + message;
        }

        sendError(status, message, null, request, response);
    }

    /**
     * Backend implementation of handling uncaught throwables.
     * <p>
     * This implementation resets the response before sending back a
     * standardized response which just conveys the status as 500/INTERNAL
     * SERVER ERROR, the message from the throwable, the stacktrace, and server
     * information.
     * <p>
     * This method logs error and does not write back and response data if the
     * response has already been committed.
     */
    @Override
    public void handleError(
            final Throwable throwable,
            final SlingJakartaHttpServletRequest request,
            final SlingJakartaHttpServletResponse response)
            throws IOException {
        final int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        // If we have a delegate let it handle the error
        if (delegate != null) {
            try {
                delegate.handleError(throwable, request, response);
            } catch (final Exception e) {
                delegateFailed(status, throwable.toString(), e, request, response);
            }
            return;
        }

        sendError(status, throwable.getMessage(), throwable, request, response);
    }

    private void sendError(
            final int status,
            final String message,
            final Throwable throwable,
            final HttpServletRequest request,
            final HttpServletResponse response)
            throws IOException {
        // error situation
        final String servletName = (String) request.getAttribute(ERROR_SERVLET_NAME);
        String requestURI = (String) request.getAttribute(ERROR_REQUEST_URI);
        if (requestURI == null) {
            requestURI = request.getRequestURI();
        }

        // set the status, content type and encoding
        response.setStatus(status);
        response.setContentType("text/html; charset=UTF-8");

        final PrintWriter pw = response.getWriter();
        pw.print("<html><head><title>");
        if (message == null) {
            pw.print("Internal error");
        } else {
            pw.print(ResponseUtil.escapeXml(message));
        }
        pw.println("</title></head><body><h1>");
        if (throwable != null) {
            pw.println(ResponseUtil.escapeXml(throwable.toString()));
        } else if (message != null) {
            pw.println(ResponseUtil.escapeXml(message));
        } else {
            pw.println("Internal error (no Exception to report)");
        }
        pw.println("</h1><p>");
        pw.print("RequestURI=");
        pw.println(ResponseUtil.escapeXml(request.getRequestURI()));
        if (servletName != null) {
            pw.println("</p><p>Servlet=");
            pw.println(ResponseUtil.escapeXml(servletName));
        }
        pw.println("</p>");

        if (throwable != null) {
            final PrintWriter escapingWriter = new PrintWriter(ResponseUtil.getXmlEscapingWriter(pw));
            pw.println("<h3>Exception stacktrace:</h3>");
            pw.println("<pre>");
            pw.flush();
            throwable.printStackTrace(escapingWriter);
            escapingWriter.flush();
            pw.println("</pre>");

            final RequestProgressTracker tracker =
                    ((SlingJakartaHttpServletRequest) request).getRequestProgressTracker();
            pw.println("<h3>Request Progress:</h3>");
            pw.println("<pre>");
            pw.flush();
            tracker.dump(new PrintWriter(escapingWriter));
            escapingWriter.flush();
            pw.println("</pre>");
        }

        pw.println("<hr /><address>");
        pw.println(ResponseUtil.escapeXml(serverInfo));
        pw.println("</address></body></html>");

        // commit the response
        response.flushBuffer();
        // close the response (SLING-2724)
        pw.close();
    }
}
