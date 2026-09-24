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
import java.io.UnsupportedEncodingException;
import java.util.Collections;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression tests for SLING-13362 (f010): the engine's request parameter
 * decoding must not diverge from a standards compliant (container/WAF)
 * decoder, otherwise a client can smuggle data past inspection that the
 * engine still acts on.
 */
public class ParameterSupportTest {

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
                // not needed for these tests
            }

            @Override
            public int read() {
                return in.read();
            }
        };
    }
}
