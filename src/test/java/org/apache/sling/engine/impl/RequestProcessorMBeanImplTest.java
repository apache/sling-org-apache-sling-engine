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

import javax.management.NotCompliantMBeanException;

import java.util.Random;

import org.apache.commons.math.stat.descriptive.SummaryStatistics;
import org.apache.sling.engine.impl.request.RequestData;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class RequestProcessorMBeanImplTest {

    /**
     * Asserts that the simple standard deviation algorithm used by the
     * RequestProcessorMBeanImpl is equivalent to the Commons Math
     * SummaryStatistics implementation.
     *
     * It also tests that resetStatistics method, actually resets all the statistics
     *
     * @throws NotCompliantMBeanException not expected
     */
    @Test
    public void test_statistics() throws NotCompliantMBeanException {
        final SummaryStatistics durationStats = new SummaryStatistics();
        final SummaryStatistics servletCallCountStats = new SummaryStatistics();
        final SummaryStatistics peakRecursionDepthStats = new SummaryStatistics();
        final RequestProcessorMBeanImpl bean = new RequestProcessorMBeanImpl();

        assertEquals(0l, bean.getRequestsCount());
        assertEquals(Long.MAX_VALUE, bean.getMinRequestDurationMsec());
        assertEquals(0l, bean.getMaxRequestDurationMsec());
        assertEquals(0.0, bean.getMeanRequestDurationMsec(), 0);
        assertEquals(0.0, bean.getStandardDeviationDurationMsec(), 0);

        assertEquals(Integer.MAX_VALUE, bean.getMinServletCallCount());
        assertEquals(0l, bean.getMaxServletCallCount());
        assertEquals(0.0, bean.getMeanServletCallCount(), 0);
        assertEquals(0.0, bean.getStandardDeviationServletCallCount(), 0);

        assertEquals(Integer.MAX_VALUE, bean.getMinPeakRecursionDepth());
        assertEquals(0l, bean.getMaxPeakRecursionDepth());
        assertEquals(0.0, bean.getMeanPeakRecursionDepth(), 0);
        assertEquals(0.0, bean.getStandardDeviationPeakRecursionDepth(), 0);

        final Random random = new Random(System.currentTimeMillis() / 17);
        final int num = 10000;
        final int min = 85;
        final int max = 250;
        for (int i = 0; i < num; i++) {
            final long durationValue = min + random.nextInt(max - min);
            final int callCountValue = min + random.nextInt(max - min);
            final int peakRecursionDepthValue = min + random.nextInt(max - min);
            durationStats.addValue(durationValue);
            servletCallCountStats.addValue(callCountValue);
            peakRecursionDepthStats.addValue(peakRecursionDepthValue);

            final RequestData requestData = Mockito.mock(RequestData.class, "requestData" + i);
            Mockito.when(requestData.getElapsedTimeMsec()).thenReturn(durationValue);
            Mockito.when(requestData.getServletCallCount()).thenReturn(callCountValue);
            Mockito.when(requestData.getPeakRecusionDepth()).thenReturn(peakRecursionDepthValue);

            bean.addRequestData(requestData);
        }

        assertEquals("Number of points must be the same", durationStats.getN(), bean.getRequestsCount());

        assertEquals("Min Duration must be equal", (long) durationStats.getMin(), bean.getMinRequestDurationMsec());
        assertEquals("Max Duration must be equal", (long) durationStats.getMax(), bean.getMaxRequestDurationMsec());
        assertAlmostEqual("Mean Duration", durationStats.getMean(), bean.getMeanRequestDurationMsec(), num);
        assertAlmostEqual(
                "Standard Deviation Duration",
                durationStats.getStandardDeviation(),
                bean.getStandardDeviationDurationMsec(),
                num);

        assertEquals(
                "Min Servlet Call Count must be equal",
                (long) servletCallCountStats.getMin(),
                bean.getMinServletCallCount());
        assertEquals(
                "Max Servlet Call Count must be equal",
                (long) servletCallCountStats.getMax(),
                bean.getMaxServletCallCount());
        assertAlmostEqual(
                "Mean Servlet Call Count", servletCallCountStats.getMean(), bean.getMeanServletCallCount(), num);
        assertAlmostEqual(
                "Standard Deviation Servlet Call Count",
                servletCallCountStats.getStandardDeviation(),
                bean.getStandardDeviationServletCallCount(),
                num);

        assertEquals(
                "Min Peak Recursion Depth must be equal",
                (long) peakRecursionDepthStats.getMin(),
                bean.getMinPeakRecursionDepth());
        assertEquals(
                "Max Peak Recursion Depth must be equal",
                (long) peakRecursionDepthStats.getMax(),
                bean.getMaxPeakRecursionDepth());
        assertAlmostEqual(
                "Mean Peak Recursion Depth", peakRecursionDepthStats.getMean(), bean.getMeanPeakRecursionDepth(), num);
        assertAlmostEqual(
                "Standard Deviation Peak Recursion Depth",
                peakRecursionDepthStats.getStandardDeviation(),
                bean.getStandardDeviationPeakRecursionDepth(),
                num);

        // check method resetStatistics
        // In the previous test, some requests have been processed, now we reset the statistics so everything statistic
        // is reinitialized
        bean.resetStatistics();

        final RequestData firstRequestDataAfterReset = Mockito.mock(RequestData.class, "firstRequestDataAfterReset");
        Mockito.when(firstRequestDataAfterReset.getElapsedTimeMsec()).thenReturn(100L);
        Mockito.when(firstRequestDataAfterReset.getServletCallCount()).thenReturn(10);
        Mockito.when(firstRequestDataAfterReset.getPeakRecusionDepth()).thenReturn(5);

        final RequestData secondRequestDataAfterReset = Mockito.mock(RequestData.class, "secondRequestDataAfterReset");
        Mockito.when(secondRequestDataAfterReset.getElapsedTimeMsec()).thenReturn(200L);
        Mockito.when(secondRequestDataAfterReset.getServletCallCount()).thenReturn(20);
        Mockito.when(secondRequestDataAfterReset.getPeakRecusionDepth()).thenReturn(15);

        bean.addRequestData(firstRequestDataAfterReset);

        assertEquals("After resetStatistics Number of requests must be one", 1, bean.getRequestsCount());
        assertEquals("After resetStatistics Min Duration must be equal", 100L, bean.getMinRequestDurationMsec());
        assertEquals("After resetStatistics Max Duration must be equal", 100L, bean.getMaxRequestDurationMsec());
        assertEquals("After resetStatistics Mean Duration must be equal", 100.0, bean.getMeanRequestDurationMsec(), 0d);
        assertEquals(
                "After resetStatistics Standard Deviation Duration must be zero",
                0.0,
                bean.getStandardDeviationDurationMsec(),
                0d);

        assertEquals("After resetStatistics Min Servlet Call Count must be equal", 10, bean.getMinServletCallCount());
        assertEquals("After resetStatistics Max Servlet Call Count must be equal", 10, bean.getMaxServletCallCount());
        assertEquals("After resetStatistics Mean Servlet Call Count", 10.0, bean.getMeanServletCallCount(), 0d);
        assertEquals(
                "After resetStatistics Standard Deviation Servlet Call Count must be zero",
                0.0,
                bean.getStandardDeviationServletCallCount(),
                0d);

        assertEquals(
                "After resetStatistics Min Peak Recursion Depth must be equal", 5, bean.getMinPeakRecursionDepth());
        assertEquals(
                "After resetStatistics Max Peak Recursion Depth must be equal", 5, bean.getMaxPeakRecursionDepth());
        assertEquals("After resetStatistics Mean Peak Recursion Depth", 5.0, bean.getMeanPeakRecursionDepth(), 0d);
        assertEquals(
                "After resetStatistics Standard Deviation Peak Recursion Depth must be zero",
                0.0,
                bean.getStandardDeviationPeakRecursionDepth(),
                0d);

        bean.addRequestData(secondRequestDataAfterReset);

        assertEquals("After processing second request Number of requests must be two", 2, bean.getRequestsCount());
        assertEquals(
                "After processing second request Min Duration must be equal", 100L, bean.getMinRequestDurationMsec());
        assertEquals(
                "After processing second request Max Duration must be equal", 200L, bean.getMaxRequestDurationMsec());
        assertEquals(
                "After processing second request Mean Duration must be equal",
                150.0,
                bean.getMeanRequestDurationMsec(),
                0d);

        assertEquals(
                "After processing second request Min Servlet Call Count must be equal",
                10,
                bean.getMinServletCallCount());
        assertEquals(
                "After processing second request Max Servlet Call Count must be equal",
                20,
                bean.getMaxServletCallCount());
        assertEquals(
                "After processing second request Mean Servlet Call Count", 15.0, bean.getMeanServletCallCount(), 0d);

        assertEquals(
                "After processing second request Min Peak Recursion Depth must be equal",
                5,
                bean.getMinPeakRecursionDepth());
        assertEquals(
                "After processing second request Max Peak Recursion Depth must be equal",
                15,
                bean.getMaxPeakRecursionDepth());
        assertEquals(
                "After processing second request Mean Peak Recursion Depth",
                10.0,
                bean.getMeanPeakRecursionDepth(),
                0d);
    }

    private void assertAlmostEqual(final String message, final double v1, final double v2, int samples) {
        final double centi = v1 / samples;
        if (v2 < (v1 - centi) || v2 > (v1 + centi)) {
            fail(message + " (expected: " + v2 + " in (" + (v1 - centi) + "," + (v1 + centi) + "))");
        }
    }
}
