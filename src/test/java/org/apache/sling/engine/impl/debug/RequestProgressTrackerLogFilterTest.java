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

import static org.junit.Assert.assertTrue;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import org.apache.sling.api.request.RequestProgressTracker;
import org.apache.sling.engine.impl.request.SlingRequestProgressTracker;
import org.junit.Test;

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
        };

        final Method activate = filter.getClass().getDeclaredMethod("activate", RequestProgressTrackerLogFilter.Config.class);
        activate.setAccessible(true);
        activate.invoke(filter, new TestConfig());
    }

    @Test
    public void verifySlingRequestProgressTrackerDurationIsNanos() throws Exception {
        // Verify that SlingRequestProgressTracker duration is based on nano time
        final long startMsec = System.currentTimeMillis();
        final SlingRequestProgressTracker rpt = new SlingRequestProgressTracker();
        Thread.sleep(10);
        final long rptElapsed = rpt.getDuration();
        final long elapsedMsec = System.currentTimeMillis() - startMsec;
        assertTrue("Expecting non-zero duration", rptElapsed > 0);
        final float ratio = rptElapsed / elapsedMsec;
        final int minExpectedRatio = RequestProgressTrackerLogFilter.NANOSEC_TO_MSEC / 2;
        assertTrue("Expecting min ratio of " + minExpectedRatio + ", got " + ratio, ratio > minExpectedRatio);
    }

    @Test
    public void testConfigMsec() throws Exception {
        final RequestProgressTrackerLogFilter filter = new RequestProgressTrackerLogFilter();
        final Method allowDuration = filter.getClass().getDeclaredMethod("allowDuration", RequestProgressTracker.class);
        allowDuration.setAccessible(true);

        final SlingRequestProgressTracker rpt = new SlingRequestProgressTracker();
        final int delta = 2;
        Thread.sleep(delta * 2);
        rpt.done();
        final long durationNanos = rpt.getDuration();
        final long durationMsec = durationNanos / 1_000_000;
        final int minMsec = (int)(durationMsec - delta);
        final int maxMsec = (int)(durationMsec + delta);
        setupMinMaxDuration(filter, minMsec, maxMsec);
        assertTrue("Expecting duration " + durationNanos + "/" + durationMsec + " to allowed for min=" + minMsec + " max=" + maxMsec,
            (boolean)allowDuration.invoke(filter, rpt));
    }
}
