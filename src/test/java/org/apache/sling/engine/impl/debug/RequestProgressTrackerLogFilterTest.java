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
package org.apache.sling.engine.impl.debug;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Iterator;

import org.apache.sling.api.request.RequestProgressTracker;
import org.apache.sling.api.request.builder.Builders;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.helpers.MessageFormatter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Partial tests of RequestProgressTrackerLogFilter */
public class RequestProgressTrackerLogFilterTest {

    private void setupMinMaxDuration(RequestProgressTrackerLogFilter filter, final int min, final int max)
            throws Exception {

        class TestConfig implements RequestProgressTrackerLogFilter.Config {
            @Override
            public Class<? extends Annotation> annotationType() {
                return null;
            }

            @Override
            public String[] extensions() {
                return null;
            }

            @Override
            public int minDurationMs() {
                return min;
            }

            @Override
            public int maxDurationMs() {
                return max;
            }

            @Override
            public boolean compactLogFormat() {
                return false;
            }
        }
        ;

        final Method activate =
                filter.getClass().getDeclaredMethod("activate", RequestProgressTrackerLogFilter.Config.class);
        activate.setAccessible(true);
        activate.invoke(filter, new TestConfig());
    }

    @Test
    public void verifySlingRequestProgressTrackerDurationIsNanos() throws Exception {
        // Verify that SlingRequestProgressTracker duration is based on nano time
        final long startMsec = System.currentTimeMillis();
        final RequestProgressTracker rpt = Builders.newRequestProgressTracker();
        Thread.sleep(10);
        final long elapsedMsec = System.currentTimeMillis() - startMsec;
        final long rptElapsed = rpt.getDuration();
        assertTrue("Expecting non-zero duration", rptElapsed > 0);

        /**
         * there must be a certain ratio between the time we know in milis and the recorded time in nanos;
         * in the exact case it would be exactly 1_000_000, but we relax it to 500_000.
         *
         * The order in which we captured the timings above even favors the rptElpased, so it will be always
         * bigger than 10 milis, and the ratio will be larger than 500_000 for sure.
         */
        final float ratio = rptElapsed / elapsedMsec;
        final int minExpectedRatio = RequestProgressTrackerLogFilter.NANOSEC_TO_MSEC / 2;
        assertTrue("Expecting min ratio of " + minExpectedRatio + ", got " + ratio, ratio > minExpectedRatio);
    }

    @Test
    public void testEscapeLogMessageEscapesCrLf() {
        // a percent-encoded CR/LF in the request path is decoded by the
        // container, recorded by the tracker and must not forge log lines
        assertEquals(
                "17 LOG Method=GET, PathInfo=/content/x\\r\\n2026-08-11 FAKE admin login OK",
                RequestProgressTrackerLogFilter.escapeLogMessage(
                        "17 LOG Method=GET, PathInfo=/content/x\r\n2026-08-11 FAKE admin login OK\n"));
    }

    @Test
    public void testEscapeLogMessageKeepsBenignMessage() {
        assertEquals(
                "20 TIMER_START{handleSecurity}",
                RequestProgressTrackerLogFilter.escapeLogMessage("20 TIMER_START{handleSecurity}\n"));
        assertEquals("", RequestProgressTrackerLogFilter.escapeLogMessage("\n"));
        assertNull(RequestProgressTrackerLogFilter.escapeLogMessage(null));
    }

    /**
     * Replaces the filter's private final SLF4J {@code Logger} with a mock so
     * the exact arguments reaching the logger can be captured.
     */
    private Logger injectMockLogger(final RequestProgressTrackerLogFilter filter) throws Exception {
        final Logger logger = mock(Logger.class);
        final Field logField = RequestProgressTrackerLogFilter.class.getDeclaredField("log");
        logField.setAccessible(true);
        logField.set(filter, logger);
        return logger;
    }

    private void invokeLogFormat(
            final RequestProgressTrackerLogFilter filter, final String methodName, final RequestProgressTracker rpt)
            throws Exception {
        final Method method = filter.getClass().getDeclaredMethod(methodName, RequestProgressTracker.class);
        method.setAccessible(true);
        method.invoke(filter, rpt);
    }

    private RequestProgressTracker rptWithMessages(final String... messages) {
        final RequestProgressTracker rpt = mock(RequestProgressTracker.class);
        final Iterator<String> it = Arrays.asList(messages).iterator();
        when(rpt.getMessages()).thenAnswer(invocation -> it);
        return rpt;
    }

    @Test
    public void testLogDefaultFormatNeverConcatenatesMessageIntoFormatStringAndEscapesCrLf() throws Exception {
        // a percent-encoded CR/LF in the request path, decoded by the
        // container and recorded by the tracker, plus a literal "{}" that an
        // attacker could use to try to smuggle an extra SLF4J placeholder
        final String attackerMessage = "17 LOG Method=GET, PathInfo=/content/x\r\n2026-08-11 FAKE admin login OK {}";
        final RequestProgressTracker rpt = rptWithMessages(attackerMessage);

        final RequestProgressTrackerLogFilter filter = new RequestProgressTrackerLogFilter();
        final Logger logger = injectMockLogger(filter);

        invokeLogFormat(filter, "logDefaultFormat", rpt);

        // the message must be passed to SLF4J as a parameter, never
        // concatenated into the format string itself - otherwise attacker
        // supplied "{}" sequences would be (mis)interpreted as placeholders
        final ArgumentCaptor<String> formatCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<Object> requestIdCaptor = ArgumentCaptor.forClass(Object.class);
        final ArgumentCaptor<Object> messageCaptor = ArgumentCaptor.forClass(Object.class);
        verify(logger, times(1)).debug(formatCaptor.capture(), requestIdCaptor.capture(), messageCaptor.capture());

        assertEquals("REQUEST_{} - {}", formatCaptor.getValue());
        final String escapedMessage = (String) messageCaptor.getValue();
        assertFalse(escapedMessage.contains("\r"));
        assertFalse(escapedMessage.contains("\n"));

        // simulate the actual SLF4J rendering to prove the injected CRLF can
        // no longer forge a new log record and the attacker's literal "{}"
        // is not treated as an additional placeholder
        final String rendered = MessageFormatter.arrayFormat(
                        formatCaptor.getValue(), new Object[] {requestIdCaptor.getValue(), messageCaptor.getValue()})
                .getMessage();
        assertFalse(rendered.contains("\r"));
        assertFalse(rendered.contains("\n"));
        assertEquals(
                "REQUEST_1 - 17 LOG Method=GET, PathInfo=/content/x\\r\\n2026-08-11 FAKE admin login OK {}", rendered);
    }

    @Test
    public void testLogCompactFormatEscapesEveryMessageBeforeJoining() throws Exception {
        final String benign = "20 TIMER_START{handleSecurity}";
        final String attackerMessage = "17 LOG Method=GET, PathInfo=/content/x\r\nFORGED admin login OK";
        final RequestProgressTracker rpt = rptWithMessages(benign, attackerMessage);

        final RequestProgressTrackerLogFilter filter = new RequestProgressTrackerLogFilter();
        final Logger logger = injectMockLogger(filter);

        invokeLogFormat(filter, "logCompactFormat", rpt);

        final ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(logger, times(1)).debug(messageCaptor.capture());

        final String logged = messageCaptor.getValue();
        assertFalse(logged.contains("\r"));
        assertFalse("no raw newline may remain other than the message separators", logged.contains("\r\n"));
        assertEquals(
                "\n" + benign + "\n" + "17 LOG Method=GET, PathInfo=/content/x\\r\\nFORGED admin login OK", logged);
    }

    @Test
    public void testConfigMsec() throws Exception {
        final RequestProgressTrackerLogFilter filter = new RequestProgressTrackerLogFilter();
        final Method allowDuration = filter.getClass().getDeclaredMethod("allowDuration", RequestProgressTracker.class);
        allowDuration.setAccessible(true);

        final RequestProgressTracker rpt = Builders.newRequestProgressTracker();
        final int delta = 2;
        Thread.sleep(delta * 2);
        rpt.done();
        final long durationNanos = rpt.getDuration();
        final long durationMsec = durationNanos / 1_000_000;
        final int minMsec = (int) (durationMsec - delta);
        final int maxMsec = (int) (durationMsec + delta);
        setupMinMaxDuration(filter, minMsec, maxMsec);
        assertTrue(
                "Expecting duration " + durationNanos + "/" + durationMsec + " to allowed for min=" + minMsec + " max="
                        + maxMsec,
                (boolean) allowDuration.invoke(filter, rpt));
    }
}
