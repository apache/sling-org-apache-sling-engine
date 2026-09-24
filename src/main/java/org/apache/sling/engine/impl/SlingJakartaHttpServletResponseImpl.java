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
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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

    /**
     * The reason why the response was committed. This is used to determine whether
     * the change to the content type header can be ignored or not.
     */
    public enum CommitReason {
        SEND_ERROR,
        SEND_REDIRECT
    }

    private static final String CALL_STACK_MESSAGE = "Call stack causing the content type override violation: ";

    private static final String HEADER_CONTENT_TYPE = "Content-Type";

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

    private CommitReason committedReason;

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
                && this.requestData.getDispatchingInfo().getType() == jakarta.servlet.DispatcherType.ERROR;
    }

    private boolean isProtectHeadersOnInclude() {
        // the dispatch info is null on the initial request, so it defaults to false for
        // the initial request always, and therefore checks for the configuration set
        // only for includes
        return this.requestData.getDispatchingInfo() != null
                && this.requestData.getDispatchingInfo().isProtectHeadersOnInclude();
    }

    private boolean isCheckContentTypeOnInclude() {
        // the dispatch info is null on the initial request, so it defaults to false for
        // the initial request always, and therefore checks for the configuration set
        // only for includes
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
            logHeaderModificationCallOnIncludeForMethod("setStatus");
            super.setStatus(sc);
        }
    }

    @Override
    public void reset() {
        if (!this.isProtectHeadersOnInclude() || isError()) {
            if (!this.isProtectHeadersOnInclude()) {
                logHeaderModificationCallOnIncludeForMethod("reset");
            }
            super.reset();
        } else {
            // ignore if not committed: because we want the exception to be thrown when the
            // response is committed. but we do not want to call reset when the headers
            // should be protected, as this would reset them as well
            if (this.isCommitted()) {
                super.reset();
            }
        }
    }

    @Override
    public void setContentLength(final int len) {
        if (!this.isProtectHeadersOnInclude()) {
            logHeaderModificationCallOnIncludeForMethod("setContentLength()");
            super.setContentLength(len);
        }
    }

    @Override
    public void setContentLengthLong(final long len) {
        if (!this.isProtectHeadersOnInclude()) {
            logHeaderModificationCallOnIncludeForMethod("setContentLengthLong()");
            super.setContentLengthLong(len);
        }
    }

    @Override
    public void setLocale(final Locale loc) {
        if (!this.isProtectHeadersOnInclude()) {
            logHeaderModificationCallOnIncludeForMethod("setLocale()");
            super.setLocale(loc);
        }
    }

    @Override
    public void addCookie(final Cookie cookie) {
        if (!this.isProtectHeadersOnInclude()) {
            logHeaderModificationCallOnIncludeForMethod("addCookie()");
            super.addCookie(cookie);
        }
    }

    @Override
    public void addDateHeader(final String name, final long value) {
        if (!this.isProtectHeadersOnInclude()) {
            logHeaderModificationCallOnIncludeForMethod("addDateHeader()");
            super.addDateHeader(name, value);
        }
    }

    @Override
    public void addHeader(final String name, final String value) {
        if (this.isInclude() && HEADER_CONTENT_TYPE.equalsIgnoreCase(name)) {
            // changing the Content-Type header during an include must be subject
            // to the same enforcement as setContentType (see setHeader)
            this.setContentType(value);
            return;
        }
        if (!this.isProtectHeadersOnInclude()) {
            logHeaderModificationCallOnIncludeForMethod("addHeader()");
            super.addHeader(name, value);
        }
    }

    @Override
    public void addIntHeader(final String name, final int value) {
        if (!this.isProtectHeadersOnInclude()) {
            logHeaderModificationCallOnIncludeForMethod("addIntHeader()");
            super.addIntHeader(name, value);
        }
    }

    @Override
    public void sendRedirect(final String location) throws IOException {
        if (!this.isProtectHeadersOnInclude()) {
            logHeaderModificationCallOnIncludeForMethod("sendRedirect");
            this.committedReason = CommitReason.SEND_REDIRECT;
            super.sendRedirect(location);
        }
    }

    @Override
    public void setDateHeader(final String name, final long value) {
        if (!this.isProtectHeadersOnInclude()) {
            logHeaderModificationCallOnIncludeForMethod("setDateHeader()");
            super.setDateHeader(name, value);
        }
    }

    @Override
    public void setHeader(final String name, final String value) {
        if (this.isInclude() && HEADER_CONTENT_TYPE.equalsIgnoreCase(name)) {
            // changing the Content-Type header during an include must be subject
            // to the same enforcement as setContentType (see setHeader)
            this.setContentType(value);
            return;
        }
        if (!this.isProtectHeadersOnInclude()) {
            logHeaderModificationCallOnIncludeForMethod("setHeader()");
            super.setHeader(name, value);
        }
    }

    @Override
    public void setIntHeader(final String name, final int value) {
        if (!this.isProtectHeadersOnInclude()) {
            logHeaderModificationCallOnIncludeForMethod("setIntHeader()");
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
        boolean isCommitedDueToSendErrorOrRedirect = this.isCommitted()
                && (CommitReason.SEND_ERROR == this.committedReason
                        || CommitReason.SEND_REDIRECT == this.committedReason);
        if (isCommitedDueToSendErrorOrRedirect || !isInclude()) {
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

    @Override
    public void setCharacterEncoding(final String charset) {
        boolean isCommitedDueToSendErrorOrRedirect = this.isCommitted()
                && (CommitReason.SEND_ERROR == this.committedReason
                        || CommitReason.SEND_REDIRECT == this.committedReason);
        if (isCommitedDueToSendErrorOrRedirect || !isInclude()) {
            super.setCharacterEncoding(charset);
            return;
        }
        final Optional<String> message = checkCharacterEncodingOverride(charset);
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
            super.setCharacterEncoding(charset);
        } else {
            super.setCharacterEncoding(charset);
        }
    }

    @Override
    public void setCharacterEncoding(final Charset charset) {
        // funnel the Charset variant through the checked String variant so
        // that the include protections cannot be bypassed via this method
        this.setCharacterEncoding(charset == null ? null : charset.name());
    }

    /**
     * Checks if the response character encoding is being changed by an include
     * and provides a message to log if it is. Changing the character encoding
     * changes the charset parameter of the 'Content-Type' header and is
     * therefore subject to the same include protections as
     * {@link #setContentType(String)}.
     *
     * @param charset the character encoding that is being set
     * @return an optional message to log
     */
    protected Optional<String> checkCharacterEncodingOverride(@Nullable String charset) {
        // A previously detected violation must not disable the check itself -
        // otherwise the second and any later override attempt within the same
        // request would pass unchecked even though the first one was blocked.
        final boolean isFirstViolation =
                requestData.getSlingRequestProcessor().getContentTypeHeaderState() != ContentTypeHeaderState.VIOLATED;
        final String currentCharset = getCharacterEncoding();
        if (charset != null && charsetsEqual(charset, currentCharset)) {
            // not an effective change
            return Optional.empty();
        }
        requestData.getSlingRequestProcessor().setContentTypeHeaderState(ContentTypeHeaderState.VIOLATED);
        final String currentContentType = getContentType();
        final String base = currentContentType == null ? "" : getMimeTypePart(currentContentType);
        final String newContentType = (charset == null) ? base : base + ";charset=" + charset;
        return Optional.of(
                isFirstViolation
                        ? getMessage(currentContentType, newContentType)
                        : getShortMessage(currentContentType, newContentType));
    }

    /**
     * Checks if the 'Content-Type' header is being overridden and provides a
     * message to log if it is. Both the media type and the charset parameter
     * are compared: changing only the charset (e.g. from
     * 'text/html;charset=UTF-8' to 'text/html;charset=UTF-7') changes the
     * effective response header just as much as changing the media type.
     *
     * @param contentType the 'Content-Type' value that is being set
     * @return an optional message to log
     */
    protected Optional<String> checkContentTypeOverride(@Nullable String contentType) {
        // A previously detected violation must not disable
        // the check itself - otherwise the second and any later override attempt
        // within the same request would pass unchecked even though the first one
        // was blocked.
        // Return a shorter message in any subsequent case (without the stack)
        final boolean isFirstViolation =
                requestData.getSlingRequestProcessor().getContentTypeHeaderState() != ContentTypeHeaderState.VIOLATED;
        String currentContentType = getContentType();
        if (contentType == null) {
            requestData.getSlingRequestProcessor().setContentTypeHeaderState(ContentTypeHeaderState.VIOLATED);
            return Optional.of(
                    isFirstViolation
                            ? getMessage(currentContentType, null)
                            : getShortMessage(currentContentType, null));
        }
        final String currentMime = currentContentType == null ? "null" : getMimeTypePart(currentContentType);
        final String setMime = getMimeTypePart(contentType);
        if (!currentMime.equalsIgnoreCase(setMime)) {
            requestData.getSlingRequestProcessor().setContentTypeHeaderState(ContentTypeHeaderState.VIOLATED);
            return Optional.of(
                    isFirstViolation
                            ? getMessage(currentContentType, contentType)
                            : getShortMessage(currentContentType, contentType));
        }
        final String setCharset = getCharsetPart(contentType);
        if (setCharset != null) {
            String currentCharset = currentContentType == null ? null : getCharsetPart(currentContentType);
            if (currentCharset == null) {
                currentCharset = getCharacterEncoding();
            }
            if (!charsetsEqual(setCharset, currentCharset)) {
                requestData.getSlingRequestProcessor().setContentTypeHeaderState(ContentTypeHeaderState.VIOLATED);
                return Optional.of(
                        isFirstViolation
                                ? getMessage(currentContentType, contentType)
                                : getShortMessage(currentContentType, contentType));
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the media type of a 'Content-Type' header value, i.e. the part
     * before the first parameter separator, trimmed.
     */
    private static String getMimeTypePart(final String contentType) {
        final int semi = contentType.indexOf(';');
        final String mime = semi >= 0 ? contentType.substring(0, semi) : contentType;
        return mime.trim();
    }

    /**
     * Returns the value of the charset parameter of a 'Content-Type' header
     * value, or {@code null} if no charset parameter is present.
     */
    @Nullable
    private static String getCharsetPart(final String contentType) {
        final String[] parts = contentType.split(";");
        for (int i = 1; i < parts.length; i++) {
            final String param = parts[i].trim();
            final int eq = param.indexOf('=');
            if (eq > 0 && "charset".equalsIgnoreCase(param.substring(0, eq).trim())) {
                String value = param.substring(eq + 1).trim();
                if (value.length() > 1 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
                    value = value.substring(1, value.length() - 1).trim();
                }
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    /**
     * Compares two charset names for equality, treating charsets that are
     * merely spelled differently (e.g. {@code UTF8} vs. {@code UTF-8}, or
     * {@code Cp1252} vs. {@code windows-1252}) as equal, not just names that
     * differ by case. Both names are resolved via {@link Charset#forName(String)}
     * and compared as canonical {@link Charset} instances; if either name is
     * not a valid or supported charset name - which the Servlet API does not
     * strictly forbid - this falls back to a case-insensitive textual
     * comparison instead of throwing.
     *
     * @param a the first charset name, may be {@code null}
     * @param b the second charset name, may be {@code null}
     * @return {@code true} if the two names identify the same charset, or are
     *         textually equal (ignoring case) when at least one of them
     *         cannot be resolved to a {@link Charset}
     */
    static boolean charsetsEqual(@Nullable final String a, @Nullable final String b) {
        if (a == null || b == null) {
            return Objects.equals(a, b);
        }
        if (a.equalsIgnoreCase(b)) {
            return true;
        }
        try {
            return Charset.forName(a).equals(Charset.forName(b));
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            // already known to differ textually (checked above), and at
            // least one name is not a valid/known charset name, so canonical
            // comparison is not possible
            return false;
        }
    }

    /**
     * Short variant of {@link #getMessage(String, String)} used for repeated
     * violations within the same request: it omits the include stack and the
     * progress tracker messages which have already been reported with the first
     * violation.
     *
     * @param currentContentType the current 'Content-Type' header
     * @param setContentType     the 'Content-Type' header that is being set
     */
    private String getShortMessage(@Nullable String currentContentType, @Nullable String setContentType) {
        return String.format(
                "Servlet %s tried to override the 'Content-Type' header from '%s' to '%s'. This is a violation of "
                        + "the RequestDispatcher.include() contract. See the previously reported violation for the "
                        + "include stack and the RequestProgressTracker messages.",
                requestData.getActiveServletName(), currentContentType, setContentType);
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
            String message = messagesIterator.next();
            // skip all log filter messages, as they are not helpful to understand this issue
            if (message.contains("LOG Calling filter:")) {
                continue;
            }
            nrOfOriginalMessages++;
            if (gotCut || lastMessages.size() >= MAX_NR_OF_MESSAGES) {
                lastMessages.removeFirst();
                gotCut = true;
            }
            lastMessages.add(message);
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
     * @param setContentType     the 'Content-Type' header that is being set
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

    /**
     * log a message for calling a header-modifying API when called as part of an include
     * @param method the name of the method
     */
    private void logHeaderModificationCallOnIncludeForMethod(String method) {
        if (isInclude()) {
            String msg = String.format(
                    "Calling '%s' within an include is not compliant to the Servlet spec (see SLING-13322)", method);
            requestData.getRequestProgressTracker().log("WARN:" + msg);
            if (!LOG.isDebugEnabled()) {
                LOG.warn("{}; enable DEBUG logging to get the full stacktrace", msg);
            } else {
                LOG.warn("{}; call trace: {} ", msg, getCurrentStackTrace());
            }
        }
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
            logHeaderModificationCallOnIncludeForMethod("sendError()");
            checkCommitted();

            this.committedReason = CommitReason.SEND_ERROR;

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
