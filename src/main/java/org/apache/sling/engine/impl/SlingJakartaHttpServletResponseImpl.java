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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.apache.sling.api.SlingException;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.engine.impl.request.RequestData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SlingJakartaHttpServletResponseImpl extends HttpServletResponseWrapper
        implements SlingJakartaHttpServletResponse {

    private static final String CALL_STACK_MESSAGE = "Call stack causing the content type override violation: ";

    private static final Logger LOG = LoggerFactory.getLogger(SlingJakartaHttpServletResponseImpl.class);

    // this regex matches TIMER_START{ followed by any characters except }, and then
    // a closing }. The part inside the braces is captured for later use.
    private static final String REGEX_TIMER_START = "TIMER_START\\{([^}]+)\\}";

    // this regex matches TIMER_END{ followed by one or more digits, a comma, any
    // characters except }, and then a closing }. The part after the comma and
    // before the closing brace is captured for later use.
    private static final String REGEX_TIMER_END = "TIMER_END\\{\\d+,([^}]+)\\}";

    private static final String TIMER_SEPARATOR = " -> ";

    public static class WriterAlreadyClosedException extends IllegalStateException {
        // just a marker class.
    }

    private static final Exception FLUSHER_STACK_DUMMY = new Exception();

    private static final int MAX_NR_OF_MESSAGES = 500;

    private Exception flusherStacktrace;

    private final RequestData requestData;

    private final boolean firstSlingResponse;

    public SlingJakartaHttpServletResponseImpl(RequestData requestData, HttpServletResponse response) {
        super(response);
        this.requestData = requestData;
        this.firstSlingResponse = !(response instanceof SlingJakartaHttpServletResponse);

        if (firstSlingResponse) {
            for (final StaticResponseHeader mapping :
                    requestData.getSlingRequestProcessor().getAdditionalResponseHeaders()) {
                response.addHeader(mapping.getResponseHeaderName(), mapping.getResponseHeaderValue());
            }
        }
    }

    public final RequestData getRequestData() {
        return requestData;
    }

    // ---------- Adaptable interface

    public <AdapterType> AdapterType adaptTo(Class<AdapterType> type) {
        return getRequestData().getSlingRequestProcessor().adaptTo(this, type);
    }

    // ---------- Redirection support through PathResolver --------------------

    @Override
    public String encodeURL(final String url) {
        // remove context path
        String path = removeContextPath(url);

        // make the path absolute
        path = makeAbsolutePath(path);

        // resolve the url to as if it would be a resource path
        path = map(path);

        // have the servlet container to further encodings
        return super.encodeURL(path);
    }

    @Override
    public String encodeRedirectURL(final String url) {
        // remove context path
        String path = removeContextPath(url);

        // make the path absolute
        path = makeAbsolutePath(path);

        // resolve the url to as if it would be a resource path
        path = map(path);

        // have the servlet container to further encodings
        return super.encodeRedirectURL(path);
    }

    @Override
    public void flushBuffer() throws IOException {
        initFlusherStacktrace();
        super.flushBuffer();
    }

    private void initFlusherStacktrace() {
        if (flusherStacktrace == null) {
            if (LOG.isDebugEnabled()) {
                flusherStacktrace = new Exception("stacktrace where response was flushed");
            } else {
                // avoid creating exceptions if debug logging is not enabled
                flusherStacktrace = FLUSHER_STACK_DUMMY;
            }
        }
    }

    private boolean isInclude() {
        return this.requestData.getDispatchingInfo() != null
                && this.requestData.getDispatchingInfo().getType() == jakarta.servlet.DispatcherType.INCLUDE;
    }

    private boolean isError() {
        return this.requestData.getDispatchingInfo() != null
                && this.requestData.getDispatchingInfo().getType() == javax.servlet.DispatcherType.ERROR;
    }

    private boolean isProtectHeadersOnInclude() {
        return this.requestData.getDispatchingInfo() != null
                && this.requestData.getDispatchingInfo().isProtectHeadersOnInclude();
    }

    private boolean isCheckContentTypeOnInclude() {
        return this.requestData.getDispatchingInfo() != null
                && this.requestData.getDispatchingInfo().isCheckContentTypeOnInclude();
    }

    @Override
    public void setStatus(final int sc) {
        if (this.isProtectHeadersOnInclude()) {
            // ignore
            return;
        }
        if (isCommitted()) {
            if (flusherStacktrace != null && flusherStacktrace != FLUSHER_STACK_DUMMY) {
                LOG.warn(
                        "Response already committed. Failed to set status code from {} to {}.",
                        getStatus(),
                        sc,
                        flusherStacktrace);
            } else {
                String explanation = flusherStacktrace != null
                        ? "Enable debug logging to find out where the response was committed."
                        : "The response was auto-committed due to the number of bytes written.";
                LOG.warn(
                        "Response already committed. Failed to set status code from {} to {}. {}",
                        getStatus(),
                        sc,
                        explanation);
            }
        } else { // response is not yet committed, so the statuscode can be changed
            super.setStatus(sc);
        }
    }

    @Override
    public void reset() {
        if (!this.isProtectHeadersOnInclude() || isError()) {
            super.reset();
        } else {
            // ignore if not committed
            if (super.isCommitted()) {
                super.reset();
            }
        }
    }

    @Override
    public void setContentLength(final int len) {
        if (!this.isProtectHeadersOnInclude()) {
            super.setContentLength(len);
        }
    }

    @Override
    public void setContentLengthLong(final long len) {
        if (!this.isProtectHeadersOnInclude()) {
            super.setContentLengthLong(len);
        }
    }

    @Override
    public void setLocale(final Locale loc) {
        if (!this.isProtectHeadersOnInclude()) {
            super.setLocale(loc);
        }
    }

    @Override
    public void setBufferSize(final int size) {
        if (!this.isProtectHeadersOnInclude()) {
            super.setBufferSize(size);
        }
    }

    @Override
    public void addCookie(final Cookie cookie) {
        if (!this.isProtectHeadersOnInclude()) {
            super.addCookie(cookie);
        }
    }

    @Override
    public void addDateHeader(final String name, final long value) {
        if (!this.isProtectHeadersOnInclude()) {
            super.addDateHeader(name, value);
        }
    }

    @Override
    public void addHeader(final String name, final String value) {
        if (!this.isProtectHeadersOnInclude()) {
            super.addHeader(name, value);
        }
    }

    @Override
    public void addIntHeader(final String name, final int value) {
        if (!this.isProtectHeadersOnInclude()) {
            super.addIntHeader(name, value);
        }
    }

    @Override
    public void sendRedirect(final String location) throws IOException {
        if (!this.isProtectHeadersOnInclude()) {
            super.sendRedirect(location);
        }
    }

    @Override
    public void setDateHeader(final String name, final long value) {
        if (!this.isProtectHeadersOnInclude()) {
            super.setDateHeader(name, value);
        }
    }

    @Override
    public void setHeader(final String name, final String value) {
        if (!this.isProtectHeadersOnInclude()) {
            super.setHeader(name, value);
        }
    }

    @Override
    public void setIntHeader(final String name, final int value) {
        if (!this.isProtectHeadersOnInclude()) {
            super.setIntHeader(name, value);
        }
    }

    private String getCurrentStackTrace() {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        StringBuilder stackTraceBuilder = new StringBuilder();
        for (StackTraceElement element : stackTraceElements) {
            stackTraceBuilder.append(element.toString()).append(System.lineSeparator());
        }
        return stackTraceBuilder.toString();
    }

    @Override
    public void setContentType(final String type) {
        if (super.isCommitted() || !isInclude()) {
            super.setContentType(type);
        } else {
            Optional<String> message = checkContentTypeOverride(type);
            if (message.isPresent()) {
                if (isCheckContentTypeOnInclude()) {
                    requestData.getRequestProgressTracker().log("ERROR: " + message.get());
                    LOG.error(CALL_STACK_MESSAGE + getCurrentStackTrace());
                    throw new ContentTypeChangeException(message.get());
                }
                if (isProtectHeadersOnInclude()) {
                    LOG.error(message.get());
                    LOG.error(CALL_STACK_MESSAGE + getCurrentStackTrace());
                    requestData.getRequestProgressTracker().log("ERROR: " + message.get());
                    return;
                }
                LOG.warn(message.get());
                LOG.warn(CALL_STACK_MESSAGE + getCurrentStackTrace());
                requestData.getRequestProgressTracker().log("WARN: " + message.get());
                super.setContentType(type);
            } else {
                super.setContentType(type);
            }
        }
    }

    /**
     * Checks if the 'Content-Type' header is being overridden and provides a
     * message to log if it is.
     *
     * @param contentType the 'Content-Type' value that is being set
     * @return an optional message to log
     */
    protected Optional<String> checkContentTypeOverride(@Nullable String contentType) {
        if (requestData.getSlingRequestProcessor().getContentTypeHeaderState() == ContentTypeHeaderState.VIOLATED) {
            // return immediatly as the content type header has already been violated
            // prevoiously, no more checks needed
            return Optional.empty();
        }
        String currentContentType = getContentType();
        if (contentType == null) {
            requestData.getSlingRequestProcessor().setContentTypeHeaderState(ContentTypeHeaderState.VIOLATED);
            return Optional.of(getMessage(currentContentType, null));
        } else {
            Optional<String> currentMime = currentContentType == null
                    ? Optional.of("null")
                    : Arrays.stream(currentContentType.split(";")).findFirst();
            Optional<String> setMime = Arrays.stream(contentType.split(";")).findFirst();
            if (currentMime.isPresent()
                    && setMime.isPresent()
                    && !currentMime.get().equals(setMime.get())) {
                requestData.getSlingRequestProcessor().setContentTypeHeaderState(ContentTypeHeaderState.VIOLATED);
                return Optional.of(getMessage(currentContentType, contentType));
            }
        }
        return Optional.empty();
    }

    private List<String> getLastMessagesOfProgressTracker() {
        // Collect the last MAX_NR_OF_MESSAGES messages from the RequestProgressTracker
        // to prevent excessive memory
        // consumption errors when close to infinite recursive calls are made
        int nrOfOriginalMessages = 0;
        boolean gotCut = false;
        Iterator<String> messagesIterator =
                requestData.getRequestProgressTracker().getMessages();
        LinkedList<String> lastMessages = new LinkedList<>();
        while (messagesIterator.hasNext()) {
            nrOfOriginalMessages++;
            if (gotCut || lastMessages.size() >= MAX_NR_OF_MESSAGES) {
                lastMessages.removeFirst();
                gotCut = true;
            }
            lastMessages.add(messagesIterator.next());
        }

        if (gotCut) {
            lastMessages.addFirst("... cut " + (nrOfOriginalMessages - MAX_NR_OF_MESSAGES) + " messages ...");
        }
        return lastMessages;
    }

    /**
     * Finds unmatched TIMER_START messages in a log of messages.
     *
     * @return a string containing the unmatched TIMER_START messages
     */
    private String findUnmatchedTimerStarts() {
        Iterator<String> messages = getLastMessagesOfProgressTracker().iterator();
        List<String> unmatchedStarts = new ArrayList<>();
        Deque<String> timerDeque = new ArrayDeque<>();

        Pattern startPattern = Pattern.compile(REGEX_TIMER_START);
        Pattern endPattern = Pattern.compile(REGEX_TIMER_END);

        while (messages.hasNext()) {
            String message = messages.next();
            Matcher startMatcher = startPattern.matcher(message);
            Matcher endMatcher = endPattern.matcher(message);

            // use a Deque to keep track of the timers that have been started. When
            // an end timer is found, it is compared to the top of the deque. If they match,
            // the timer is removed from the deque. If they don't match, the timer is added
            // to the list of unmatched starts. As the deque is a LIFO data structure, the
            // last timer that was started will be the first one to be ended. There is no
            // Start1, Start2, End1 scenario, without an End2 in between.
            if (startMatcher.find()) {
                timerDeque.push(startMatcher.group(1));
            } else if (endMatcher.find()) {
                String endTimer = endMatcher.group(1);
                if (!timerDeque.isEmpty() && timerDeque.peek().equals(endTimer)) {
                    timerDeque.pop();
                } else {
                    unmatchedStarts.add(endTimer);
                }
            }
        }

        // ignore the first element, as it will never have a matching end, as it is the
        // first timer started and is not finished processing
        while (timerDeque.size() > 1) {
            unmatchedStarts.add(timerDeque.pop());
        }
        StringBuilder sb = new StringBuilder();
        for (String script : unmatchedStarts) {
            sb.append(script).append(TIMER_SEPARATOR);
        }
        String ret = sb.toString();
        if (ret.endsWith(TIMER_SEPARATOR)) {
            ret = ret.substring(0, ret.length() - TIMER_SEPARATOR.length());
        }
        return ret;
    }

    /**
     * Retrieves the message to log when the 'Content-Type' header is changed via an
     * include.
     *
     * @param currentContentType the current 'Content-Type' header
     * @param setContentType the 'Content-Type' header that is being set
     */
    private String getMessage(@Nullable String currentContentType, @Nullable String setContentType) {
        String unmatchedStartTimers = findUnmatchedTimerStarts();

        String allMessages =
                getLastMessagesOfProgressTracker().stream().collect(Collectors.joining(System.lineSeparator()));

        if (!isCheckContentTypeOnInclude()) {
            return String.format(
                    "Servlet %s tried to override the 'Content-Type' header from '%s' to '%s'. This is a violation of "
                            + "the RequestDispatcher.include() contract - "
                            + "https://jakarta.ee/specifications/servlet/4.0/apidocs/javax/servlet/requestdispatcher#include-javax.servlet.ServletRequest-javax.servlet.ServletResponse-. , Include stack: %s. All RequestProgressTracker messages: %s",
                    requestData.getActiveServletName(),
                    currentContentType,
                    setContentType,
                    unmatchedStartTimers,
                    allMessages);
        }
        return String.format(
                "Servlet %s tried to override the 'Content-Type' header from '%s' to '%s', however the"
                        + " %s forbids this via the %s configuration property. This is a violation of the "
                        + "RequestDispatcher.include() contract - "
                        + "https://jakarta.ee/specifications/servlet/4.0/apidocs/javax/servlet/requestdispatcher#include-javax.servlet.ServletRequest-javax.servlet.ServletResponse-. , Include stack: %s. All RequestProgressTracker messages: %s",
                requestData.getActiveServletName(),
                currentContentType,
                setContentType,
                Config.PID,
                "sling.includes.checkcontenttype",
                unmatchedStartTimers,
                allMessages);
    }

    private static class ContentTypeChangeException extends SlingException {
        protected ContentTypeChangeException(String text) {
            super(text);
        }
    }

    // ---------- Error handling through Sling Error Resolver -----------------

    @Override
    public void sendError(int status) throws IOException {
        if (!this.isProtectHeadersOnInclude()) {
            sendError(status, null);
        }
    }

    @Override
    public void sendError(int status, String message) throws IOException {
        if (!this.isProtectHeadersOnInclude()) {
            checkCommitted();

            final SlingRequestProcessorImpl eh = getRequestData().getSlingRequestProcessor();
            eh.handleError(status, message, requestData.getSlingRequest(), this);
        }
    }

    // ---------- Internal helper ---------------------------------------------

    @Override
    public PrintWriter getWriter() throws IOException {
        PrintWriter result = super.getWriter();
        if (firstSlingResponse) {
            final PrintWriter delegatee = result;
            result = new PrintWriter(result) {

                private boolean isClosed = false;

                private void checkClosed() {
                    if (this.isClosed) {
                        throw new WriterAlreadyClosedException();
                    }
                }

                @Override
                public PrintWriter append(final char arg0) {
                    this.checkClosed();
                    return delegatee.append(arg0);
                }

                @Override
                public PrintWriter append(final CharSequence arg0, final int arg1, final int arg2) {
                    this.checkClosed();
                    return delegatee.append(arg0, arg1, arg2);
                }

                @Override
                public PrintWriter append(final CharSequence arg0) {
                    this.checkClosed();
                    return delegatee.append(arg0);
                }

                @Override
                public boolean checkError() {
                    this.checkClosed();
                    return delegatee.checkError();
                }

                @Override
                public void close() {
                    this.checkClosed();
                    this.isClosed = true;
                    delegatee.close();
                }

                @Override
                public void flush() {
                    this.checkClosed();
                    initFlusherStacktrace();
                    delegatee.flush();
                }

                @Override
                public PrintWriter format(final Locale arg0, final String arg1, final Object... arg2) {
                    this.checkClosed();
                    return delegatee.format(arg0, arg1, arg2);
                }

                @Override
                public PrintWriter format(final String arg0, final Object... arg1) {
                    this.checkClosed();
                    return delegatee.format(arg0, arg1);
                }

                @Override
                public void print(final boolean arg0) {
                    this.checkClosed();
                    delegatee.print(arg0);
                }

                @Override
                public void print(final char arg0) {
                    this.checkClosed();
                    delegatee.print(arg0);
                }

                @Override
                public void print(final char[] arg0) {
                    this.checkClosed();
                    delegatee.print(arg0);
                }

                @Override
                public void print(final double arg0) {
                    this.checkClosed();
                    delegatee.print(arg0);
                }

                @Override
                public void print(final float arg0) {
                    this.checkClosed();
                    delegatee.print(arg0);
                }

                @Override
                public void print(final int arg0) {
                    this.checkClosed();
                    delegatee.print(arg0);
                }

                @Override
                public void print(final long arg0) {
                    this.checkClosed();
                    delegatee.print(arg0);
                }

                @Override
                public void print(final Object arg0) {
                    this.checkClosed();
                    delegatee.print(arg0);
                }

                @Override
                public void print(final String arg0) {
                    this.checkClosed();
                    delegatee.print(arg0);
                }

                @Override
                public PrintWriter printf(final Locale arg0, final String arg1, final Object... arg2) {
                    this.checkClosed();
                    return delegatee.printf(arg0, arg1, arg2);
                }

                @Override
                public PrintWriter printf(final String arg0, final Object... arg1) {
                    this.checkClosed();
                    return delegatee.printf(arg0, arg1);
                }

                @Override
                public void println() {
                    this.checkClosed();
                    delegatee.println();
                }

                @Override
                public void println(final boolean arg0) {
                    this.checkClosed();
                    delegatee.println(arg0);
                }

                @Override
                public void println(final char arg0) {
                    this.checkClosed();
                    delegatee.println(arg0);
                }

                @Override
                public void println(final char[] arg0) {
                    this.checkClosed();
                    delegatee.println(arg0);
                }

                @Override
                public void println(final double arg0) {
                    this.checkClosed();
                    delegatee.println(arg0);
                }

                @Override
                public void println(final float arg0) {
                    this.checkClosed();
                    delegatee.println(arg0);
                }

                @Override
                public void println(final int arg0) {
                    this.checkClosed();
                    delegatee.println(arg0);
                }

                @Override
                public void println(final long arg0) {
                    this.checkClosed();
                    delegatee.println(arg0);
                }

                @Override
                public void println(final Object arg0) {
                    this.checkClosed();
                    delegatee.println(arg0);
                }

                @Override
                public void println(final String arg0) {
                    this.checkClosed();
                    delegatee.println(arg0);
                }

                @Override
                public void write(final char[] arg0, final int arg1, final int arg2) {
                    this.checkClosed();
                    delegatee.write(arg0, arg1, arg2);
                }

                @Override
                public void write(final char[] arg0) {
                    this.checkClosed();
                    delegatee.write(arg0);
                }

                @Override
                public void write(final int arg0) {
                    this.checkClosed();
                    delegatee.write(arg0);
                }

                @Override
                public void write(final String arg0, final int arg1, final int arg2) {
                    this.checkClosed();
                    delegatee.write(arg0, arg1, arg2);
                }

                @Override
                public void write(final String arg0) {
                    this.checkClosed();
                    delegatee.write(arg0);
                }
            };
        }
        return result;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        final ServletOutputStream outputStream = super.getOutputStream();
        if (firstSlingResponse) {
            return new DelegatingServletOutputStream(outputStream) {
                @Override
                public void flush() throws IOException {
                    initFlusherStacktrace();
                    super.flush();
                }
            };
        }
        return outputStream;
    }

    private void checkCommitted() {
        if (isCommitted()) {
            throw new IllegalStateException("Response has already been committed");
        }
    }

    private String makeAbsolutePath(String path) {
        if (path.startsWith("/")) {
            return path;
        }

        String base = getRequestData().getContentData().getResource().getPath();
        int lastSlash = base.lastIndexOf('/');
        if (lastSlash >= 0) {
            path = base.substring(0, lastSlash + 1) + path;
        } else {
            path = "/" + path;
        }

        return path;
    }

    private String map(String url) {
        return getRequestData().getResourceResolver().map(getRequestData().getServletRequest(), url);
    }

    private String removeContextPath(final String path) {
        final String contextPath =
                this.getRequestData().getSlingRequest().getContextPath().concat("/");
        if (contextPath.length() > 1 && path.startsWith(contextPath)) {
            return path.substring(contextPath.length() - 1);
        }
        return path;
    }

    /**
     * A simple implementation of ServletOutputStream, that delegates all methods
     * to a delegate instance. It separates the "boring" delegation logic from any
     * added logic in order to (hopefully) make the code more readable.
     */
    private abstract class DelegatingServletOutputStream extends ServletOutputStream {

        final ServletOutputStream delegate;

        DelegatingServletOutputStream(final ServletOutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void print(final String s) throws IOException {
            delegate.print(s);
        }

        @Override
        public void print(final boolean b) throws IOException {
            delegate.print(b);
        }

        @Override
        public void print(final char c) throws IOException {
            delegate.print(c);
        }

        @Override
        public void print(final int i) throws IOException {
            delegate.print(i);
        }

        @Override
        public void print(final long l) throws IOException {
            delegate.print(l);
        }

        @Override
        public void print(final float f) throws IOException {
            delegate.print(f);
        }

        @Override
        public void print(final double d) throws IOException {
            delegate.print(d);
        }

        @Override
        public void println() throws IOException {
            delegate.println();
        }

        @Override
        public void println(final String s) throws IOException {
            delegate.println(s);
        }

        @Override
        public void println(final boolean b) throws IOException {
            delegate.println(b);
        }

        @Override
        public void println(final char c) throws IOException {
            delegate.println(c);
        }

        @Override
        public void println(final int i) throws IOException {
            delegate.println(i);
        }

        @Override
        public void println(final long l) throws IOException {
            delegate.println(l);
        }

        @Override
        public void println(final float f) throws IOException {
            delegate.println(f);
        }

        @Override
        public void println(final double d) throws IOException {
            delegate.println(d);
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setWriteListener(final WriteListener writeListener) {
            delegate.setWriteListener(writeListener);
        }

        @Override
        public void write(final int b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void write(final byte[] b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void write(final byte[] b, final int off, final int len) throws IOException {
            delegate.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
