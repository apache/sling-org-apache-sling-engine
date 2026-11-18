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
import java.util.Collections;
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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Test.None;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 *
 */
public class ParameterSupportTest {

    private ParameterSupport support;
    private HttpServletRequest mockRequest;

    @Before
    public void before() {
        ParameterSupport.configure(false, 50);
        mockRequest = Mockito.mock(HttpServletRequest.class);
        support = ParameterSupport.getInstance(mockRequest);
    }

    @After
    public void after() {
        ParameterSupport.configure(false, 50);
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

        final SlingParameterParseException failure =
                assertThrows(SlingParameterParseException.class, support::getRequestParameterList);
        assertTrue(failure.getCause() instanceof FileCountLimitExceededException);
        assertSame(failure, assertThrows(SlingParameterParseException.class, support::getParameterMap));
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

    @Test
    public void testQueryStringDecodingIgnoresClientControlledCharacterEncoding() {
        // the client claims (via Content-Type) that the request uses UTF-16BE.
        // Per HTTP this only applies to the request body, not the query
        // string. If the engine honored it for the query string anyway, the
        // two raw bytes 0x00 0x41 would be read as a single UTF-16BE
        // character ('A'), rather than as two ISO-8859-1 bytes/characters -
        // exactly the kind of divergence a container or WAF (which decodes
        // the query string as its own configured/default charset) would not
        // see.
        final HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getCharacterEncoding()).thenReturn("UTF-16BE");
        when(request.getQueryString()).thenReturn("a=%00A");
        when(request.getAttribute(ParameterSupport.MARKER_IS_SERVICE_PROCESSING))
                .thenReturn(null);
        when(request.getParameterMap()).thenReturn(Collections.emptyMap());

        final ParameterSupport parameterSupport = ParameterSupport.getInstance(request);

        assertEquals(
                "query string must always be decoded byte-for-byte as ISO-8859-1, "
                        + "never with the client supplied (body) character encoding",
                "\u0000A",
                parameterSupport.getParameter("a"));
    }

    @Test
    public void testWwwFormEncodedContentTypeExactMatchIsParsedAsFormParameters() throws Exception {
        final HttpServletRequest request = postRequest("application/x-www-form-urlencoded", "a=b");

        final ParameterSupport parameterSupport = ParameterSupport.getInstance(request);

        assertEquals("b", parameterSupport.getParameter("a"));
    }

    @Test
    public void testWwwFormEncodedContentTypeWithCharsetParameterIsParsedAsFormParameters() throws IOException {
        final HttpServletRequest request = postRequest("application/x-www-form-urlencoded; charset=UTF-8", "a=b");

        final ParameterSupport parameterSupport = ParameterSupport.getInstance(request);

        assertEquals("b", parameterSupport.getParameter("a"));
    }

    @Test
    public void testContentTypeThatOnlyStartsWithFormEncodedMediaTypeIsNotParsedAsFormParameters() throws IOException {
        // a container/perimeter doing an exact media type match considers
        // this request to carry no parameters at all; if the engine parsed
        // the body anyway (prefix match) it would act on data invisible to
        // that inspection.
        final HttpServletRequest request = postRequest("application/x-www-form-urlencoded-bla", "a=b");

        final ParameterSupport parameterSupport = ParameterSupport.getInstance(request);

        assertNull(parameterSupport.getParameter("a"));
    }

    @Test(expected = SlingParameterParseException.class)
    public void testMalformedQueryStringIsRejected() {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        // a malformed escape aborts the parse after 'a' was already added;
        // 'c' must not be silently dropped while processing continues
        when(request.getQueryString()).thenReturn("a=1&b=%zz&c=3");

        final ParameterSupport support = ParameterSupport.getInstance(request);
        support.getParameter("c");
    }

    @Test(expected = SlingParameterParseException.class)
    public void testMalformedQueryStringFailureIsCachedAcrossAccessors() {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getQueryString()).thenReturn("a=1&b=%zz&c=3");

        final ParameterSupport support = ParameterSupport.getInstance(request);
        try {
            support.getParameter("c");
            fail("Expected the first access to throw SlingParameterParseException");
        } catch (SlingParameterParseException expected) {
            // expected on first access; the point of this test is that the
            // failure must be cached and rethrown on the *next* access too -
            // even via a different accessor method - instead of silently
            // continuing with a partial map
        }
        support.getRequestParameterMap();
    }

    @Test(expected = SlingParameterParseException.class)
    public void testMalformedFormEncodedBodyIsRejected() throws IOException {
        final HttpServletRequest request = postRequest("application/x-www-form-urlencoded", "a=1&b=%zz&c=3");

        final ParameterSupport support = ParameterSupport.getInstance(request);
        support.getParameter("c");
    }

    @Test(expected = SlingParameterParseException.class)
    public void testMalformedMultipartBodyIsRejected() throws IOException, ServletException {
        final HttpServletRequest request = postRequest("multipart/form-data", "irrelevant body content");
        when(request.getParts()).thenThrow(new ServletException("Missing multipart boundary"));

        final ParameterSupport support = ParameterSupport.getInstance(request);
        support.getParameter("anything");
    }

    @Test(expected = SlingParameterParseException.class)
    public void testMalformedStreamedMultipartBodyIsRejected() throws IOException, ServletException {
        final HttpServletRequest request = postRequest("multipart/form-data", "irrelevant body content");
        when(request.getParts()).thenThrow(new ServletException("Missing multipart boundary"));
        when(request.getHeader(ParameterSupport.SLING_UPLOADMODE_HEADER)).thenReturn(ParameterSupport.STREAM_UPLOAD);

        final ParameterSupport support = ParameterSupport.getInstance(request);
        support.getRequestParameterMap();
        final ArgumentCaptor<RequestPartsIterator> captor = ArgumentCaptor.forClass(RequestPartsIterator.class);
        verify(request).setAttribute(eq(ParameterSupport.REQUEST_PARTS_ITERATOR_ATTRIBUTE), captor.capture());
        captor.getValue().hasNext();
    }

    @Test
    public void testStreamedUploadModeEnforcesConfiguredFileCountLimit() throws Exception {
        ParameterSupport.configure(false, 1);
        try {
            final HttpServletRequest request = postRequest("multipart/form-data; boundary=AaB03x", "");
            final Part first = mock(Part.class);
            final Part second = mock(Part.class);
            when(first.getSubmittedFileName()).thenReturn("a.txt");
            when(second.getSubmittedFileName()).thenReturn("b.txt");
            when(request.getParts()).thenReturn(List.of(first, second));
            when(request.getHeader(ParameterSupport.SLING_UPLOADMODE_HEADER))
                    .thenReturn(ParameterSupport.STREAM_UPLOAD);

            final ParameterSupport support = ParameterSupport.getInstance(request);
            support.getRequestParameterMap();

            @SuppressWarnings("unchecked")
            final ArgumentCaptor<Iterator<Part>> captor = ArgumentCaptor.forClass(Iterator.class);
            verify(request).setAttribute(eq(ParameterSupport.REQUEST_PARTS_ITERATOR_ATTRIBUTE), captor.capture());
            final Iterator<Part> parts = captor.getValue();

            final SlingParameterParseException failure =
                    assertThrows(SlingParameterParseException.class, parts::hasNext);
            assertTrue(failure.getCause() instanceof FileCountLimitExceededException);
        } finally {
            // restore defaults so this test does not leak static state into
            // the other tests in this class
            ParameterSupport.configure(false, 50);
        }
    }

    private static HttpServletRequest postRequest(final String contentType, final String body) throws IOException {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getCharacterEncoding()).thenReturn("UTF-8");
        when(request.getContentType()).thenReturn(contentType);
        when(request.getContentLength()).thenReturn(body.length());
        when(request.getQueryString()).thenReturn(null);
        when(request.getAttribute(ParameterSupport.MARKER_IS_SERVICE_PROCESSING))
                .thenReturn(null);
        when(request.getParameterMap()).thenReturn(Collections.emptyMap());
        when(request.getInputStream()).thenReturn(toServletInputStream(body));
        return request;
    }

    private static ServletInputStream toServletInputStream(final String content) throws UnsupportedEncodingException {
        final ByteArrayInputStream in = new ByteArrayInputStream(content.getBytes(Util.ENCODING_DIRECT));
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return in.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int read() {
                return in.read();
            }
        };
    }

    @Test
    public void testContainerMultipartSizeLimitIsRejectedAndCached() throws IOException, ServletException {
        mockMultipartPost();
        Mockito.doThrow(new IllegalStateException("Request exceeds configured multipart size"))
                .when(mockRequest)
                .getParts();

        final SlingParameterParseException failure =
                assertThrows(SlingParameterParseException.class, support::getRequestParameterMap);
        assertTrue(failure.getCause() instanceof IllegalStateException);
        assertSame(failure, assertThrows(SlingParameterParseException.class, support::getRequestParameterList));
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
