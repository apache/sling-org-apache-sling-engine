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
package org.apache.sling.engine.impl.parameters;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.request.RequestParameterMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.Test.None;
import org.mockito.Mockito;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;

/**
 *
 */
public class ParameterSupportTest {

    private ParameterSupport support;
    private HttpServletRequest mockRequest;

    @Before
    public void before() {
        mockRequest = Mockito.mock(HttpServletRequest.class);
        support = ParameterSupport.getInstance(mockRequest);
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.ParameterSupport#getInstance(jakarta.servlet.http.HttpServletRequest)}.
     */
    @Test
    public void testGetInstance() {
        String attrName = ParameterSupport.class.getName();
        final ParameterSupport support1 = ParameterSupport.getInstance(mockRequest);
        assertNotNull(support1);
        Mockito.verify(mockRequest, times(1)).setAttribute(attrName, support1);

        // cover cached value in request attr
        Mockito.doReturn(support1).when(mockRequest).getAttribute(attrName);
        assertEquals(support1, ParameterSupport.getInstance(mockRequest));
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.ParameterSupport#getParameterSupportRequestWrapper(jakarta.servlet.http.HttpServletRequest)}.
     */
    @Test
    public void testGetParameterSupportRequestWrapper() {
        assertNotNull(ParameterSupport.getParameterSupportRequestWrapper(mockRequest));
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.ParameterSupport#configure(boolean)}.
     */
    @Test(expected = None.class)
    public void testConfigure() {
        ParameterSupport.configure(false, -1);

        // alternate options
        ParameterSupport.configure(true, 50);
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.ParameterSupport#requestDataUsed()}.
     */
    @Test
    public void testRequestDataUsed() {
        assertFalse(support.requestDataUsed());
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.ParameterSupport#getParameter(java.lang.String)}.
     */
    @Test
    public void testGetParameter() throws IOException {
        mockFormEncodedPost();
        assertEquals("value1", support.getParameter("key1"));
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.ParameterSupport#getParameterValues(java.lang.String)}.
     */
    @Test
    public void testGetParameterValues() throws IOException, ServletException {
        mockMultipartPost();
        assertArrayEquals(new String[] {"value1"}, support.getParameterValues("key1"));
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.ParameterSupport#getParameterMap()}.
     */
    @Test
    public void testGetParameterMap() throws IOException {
        mockFormEncodedPost();
        final Map<String, String[]> parameterMap = support.getParameterMap();
        assertEquals(4, parameterMap.size());
        assertArrayEquals(new String[] {"value1"}, parameterMap.get("key1"));
        assertArrayEquals(new String[] {"qvalue2"}, parameterMap.get("qkey1"));
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.ParameterSupport#getParameterNames()}.
     */
    @Test
    public void testGetParameterNames() throws IOException {
        mockFormEncodedPost();
        final Enumeration<String> parameterNames = support.getParameterNames();
        assertNotNull(parameterNames);
        assertTrue(parameterNames.hasMoreElements());
        assertNotNull(parameterNames.nextElement());
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.ParameterSupport#getRequestParameter(java.lang.String)}.
     */
    @Test
    public void testGetRequestParameter() throws IOException {
        mockFormEncodedPost();
        assertNotNull(support.getRequestParameter("key1"));
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.ParameterSupport#getRequestParameters(java.lang.String)}.
     */
    @Test
    public void testGetRequestParameters() throws IOException {
        mockFormEncodedPost();
        assertNotNull(support.getRequestParameters("key1"));
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.ParameterSupport#getPart(java.lang.String)}.
     */
    @Test
    public void testGetPart() throws IOException, ServletException {
        mockMultipartPost();
        assertNotNull(support.getPart("key1"));
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.ParameterSupport#getParts()}.
     */
    @Test
    public void testGetParts() throws IOException, ServletException {
        mockMultipartPost();
        assertEquals(2, support.getParts().size());
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.ParameterSupport#getRequestParameterMap()}.
     */
    @Test
    public void testGetRequestParameterMap() throws IOException {
        mockFormEncodedPost();
        final RequestParameterMap map1 = support.getRequestParameterMap();
        assertTrue(map1.containsKey("key1"));
        assertEquals("value1", map1.getValue("key1").getString());
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.ParameterSupport#getRequestParameterList()}.
     */
    @Test
    public void testGetRequestParameterList() throws IOException {
        mockFormEncodedPost();
        final List<RequestParameter> list1 = support.getRequestParameterList();
        assertEquals(4, list1.size());
    }

    @Test
    public void testMultipartPost() throws IOException, ServletException {
        mockMultipartPost();
        final List<RequestParameter> list1 = support.getRequestParameterList();
        assertEquals(4, list1.size());
    }

    @Test
    public void testMultipartPostWithTooManyFiles() throws IOException, ServletException {
        ParameterSupport.configure(true, 5);

        mockMultipartPost();

        // add a few more parts
        List<Part> parts = new ArrayList<>(mockRequest.getParts());
        for (int i = 0; i < 5; i++) {
            Part mockPart = Mockito.mock(Part.class);
            Mockito.doReturn(String.format("anotherfile%d.txt", i))
                    .when(mockPart)
                    .getSubmittedFileName();
            parts.add(mockPart);
        }
        Mockito.doReturn(parts).when(mockRequest).getParts();

        // expected FileCountLimitExceededException to be caught and logged but continue
        final List<RequestParameter> list1 = support.getRequestParameterList();
        assertEquals(4, list1.size());
    }

    @Test
    public void testStreamedMultipartPost1() throws IOException, ServletException {
        mockMultipartPost();
        Mockito.doReturn(ParameterSupport.STREAM_UPLOAD)
                .when(mockRequest)
                .getHeader(ParameterSupport.SLING_UPLOADMODE_HEADER);
        assertStreamedMultipartPost();
    }

    @Test
    public void testStreamedMultipartPost2() throws IOException, ServletException {
        mockMultipartPost();
        Mockito.doReturn("uploadmode=stream&qkey2=qvalue2").when(mockRequest).getQueryString();
        assertStreamedMultipartPost();
    }

    @SuppressWarnings("unchecked")
    protected void assertStreamedMultipartPost() {
        AtomicReference<Iterator<Part>> partsIt = new AtomicReference<>();
        Mockito.doAnswer(invocation -> {
                    partsIt.set(invocation.getArgument(1, Iterator.class));
                    return null;
                })
                .when(mockRequest)
                .setAttribute(eq(ParameterSupport.REQUEST_PARTS_ITERATOR_ATTRIBUTE), any(Iterator.class));
        final List<RequestParameter> list1 = support.getRequestParameterList();
        assertEquals(2, list1.size());

        final Iterator<Part> iterator = partsIt.get();
        assertNotNull(iterator);
        assertTrue(iterator.hasNext());
        assertNotNull(iterator.next());
    }

    @Test
    public void testPostWithFallbackToContainerParams() {
        mockFallbackPost();
        Mockito.doReturn(null).when(mockRequest).getContentType();
        final List<RequestParameter> list1 = support.getRequestParameterList();
        assertEquals(2, list1.size());
    }

    @Test
    public void testUnsupportedCharacterEncoding1() throws IOException {
        mockFormEncodedPost();
        Mockito.doReturn(null).when(mockRequest).getCharacterEncoding();
        Mockito.doThrow(UnsupportedEncodingException.class)
                .when(mockRequest)
                .setCharacterEncoding(Util.ENCODING_DIRECT);

        assertThrows(SlingUnsupportedEncodingException.class, support::getParameterMap);
    }

    @Test
    public void testUnsupportedCharacterEncoding2() throws IOException {
        mockFormEncodedPost();
        Mockito.doReturn("invalid").when(mockRequest).getCharacterEncoding();

        assertThrows(SlingUnsupportedEncodingException.class, support::getParameterMap);
    }

    @Test
    public void testUnsupportedCharacterEncoding3() throws IOException {
        mockFormEncodedPost();
        Mockito.doReturn("invalid").when(mockRequest).getCharacterEncoding();
        Mockito.doReturn(null).when(mockRequest).getQueryString();

        assertThrows(SlingUnsupportedEncodingException.class, support::getParameterMap);
    }

    private void mockFormEncodedPost() throws IOException {
        Mockito.doReturn("POST").when(mockRequest).getMethod();
        Mockito.doReturn("application/x-www-form-urlencoded").when(mockRequest).getContentType();
        Mockito.doReturn("qkey1=qvalue2&qkey2=qvalue2").when(mockRequest).getQueryString();

        Map<String, String[]> containerParams = new HashMap<>();
        containerParams.put("key1", new String[] {"value1"});
        containerParams.put("key2", new String[] {"value2"});
        Mockito.doReturn(containerParams).when(mockRequest).getParameterMap();

        Map<String, String> params = new HashMap<>();
        params.put("key1", "value1");
        params.put("key2", "value2");

        String formUrlEncoded = params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        ServletInputStream inStream = new ServletInputStream() {
            private final InputStream is = new ByteArrayInputStream(formUrlEncoded.getBytes(StandardCharsets.UTF_8));

            @Override
            public int read() throws IOException {
                return is.read();
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public boolean isFinished() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                throw new UnsupportedOperationException();
            }
        };
        Mockito.doReturn(inStream).when(mockRequest).getInputStream();
    }

    private void mockMultipartPost() throws IOException, ServletException {
        Mockito.doReturn("POST").when(mockRequest).getMethod();
        Mockito.doReturn("multipart/form-data").when(mockRequest).getContentType();
        Mockito.doReturn("qkey1=qvalue2&qkey2=qvalue2").when(mockRequest).getQueryString();

        Map<String, String[]> containerParams = new HashMap<>();
        containerParams.put("key1", new String[] {"value1"});
        containerParams.put("key2", new String[] {"value2"});
        Mockito.doReturn(containerParams).when(mockRequest).getParameterMap();

        Part part1 = Mockito.mock(Part.class);
        Mockito.doReturn("key1").when(part1).getName();
        Mockito.doReturn(new ByteArrayInputStream("value1".getBytes()))
                .when(part1)
                .getInputStream();
        Part part2 = Mockito.mock(Part.class);
        Mockito.doReturn("key2").when(part2).getName();
        Mockito.doReturn("file2.txt").when(part2).getSubmittedFileName();
        Mockito.doReturn(new ByteArrayInputStream("value2".getBytes()))
                .when(part2)
                .getInputStream();
        Mockito.doReturn(List.of(part1, part2)).when(mockRequest).getParts();
    }

    private void mockFallbackPost() {
        Mockito.doReturn("POST").when(mockRequest).getMethod();
        Mockito.doReturn(null).when(mockRequest).getContentType();
        Mockito.doReturn(null).when(mockRequest).getQueryString();

        Map<String, String[]> containerParams = new HashMap<>();
        containerParams.put("key1", new String[] {"value1"});
        containerParams.put("key2", new String[] {"value2"});
        Mockito.doReturn(containerParams).when(mockRequest).getParameterMap();
    }
}
