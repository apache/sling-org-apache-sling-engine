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
import java.util.List;

import junit.framework.TestCase;
import org.apache.sling.api.request.RequestParameter;

public class UtilTest extends TestCase {

    private static final String utf8 = "UTF-8";

    // runic letter e, katakana letter pa, halfwidth katakana letter no
    // encoded LATIN-1 String of UTF-8 encoding
    private static final String utf8Coded = "\u00e1\u009b\u0082\u00e3\u0083\u0091\u00ef\u00be\u0089";

    private static final String utf8String;

    static {
        try {
            utf8String = new String(utf8Coded.getBytes(Util.ENCODING_DIRECT), utf8);
        } catch (UnsupportedEncodingException e) {
            throw new InternalError(e.toString());
        }
    }

    public void test_default_fix_encoding() {
        assertEquals(Util.ENCODING_DIRECT, Util.getDefaultFixEncoding());

        Util.setDefaultFixEncoding(utf8);
        assertEquals(utf8, Util.getDefaultFixEncoding());

        Util.setDefaultFixEncoding(Util.ENCODING_DIRECT);
        assertEquals(Util.ENCODING_DIRECT, Util.getDefaultFixEncoding());

        Util.setDefaultFixEncoding("XX_invalid_encoding_scheme_XX");
        assertEquals(Util.ENCODING_DIRECT, Util.getDefaultFixEncoding());

        Util.setDefaultFixEncoding(utf8);
        assertEquals(utf8, Util.getDefaultFixEncoding());

        Util.setDefaultFixEncoding("XX_invalid_encoding_scheme_XX");
        assertEquals(utf8, Util.getDefaultFixEncoding());

        Util.setDefaultFixEncoding(Util.ENCODING_DIRECT);
        assertEquals(Util.ENCODING_DIRECT, Util.getDefaultFixEncoding());
    }

    public void test_fix_encoding_direct() {

        ParameterMap pm = new ParameterMap();
        pm.addParameter(new ContainerRequestParameter("par", utf8Coded, Util.ENCODING_DIRECT), false);
        Util.fixEncoding(pm);
        assertEquals(utf8Coded, pm.getValue("par").getString());
    }

    public void test_fix_encoding_charset() {
        ParameterMap pm2 = new ParameterMap();
        pm2.addParameter(new ContainerRequestParameter("par", utf8Coded, Util.ENCODING_DIRECT), false);
        pm2.addParameter(new ContainerRequestParameter("_charset_", utf8, Util.ENCODING_DIRECT), false);
        Util.fixEncoding(pm2);
        assertEquals(utf8String, pm2.getValue("par").getString());
    }

    public void test_fix_encoding_configured() {
        ParameterMap pm3 = new ParameterMap();
        pm3.addParameter(new ContainerRequestParameter("par", utf8Coded, Util.ENCODING_DIRECT), false);
        Util.setDefaultFixEncoding(utf8);
        Util.fixEncoding(pm3);
        assertEquals(utf8String, pm3.getValue("par").getString());
        Util.setDefaultFixEncoding(Util.ENCODING_DIRECT);
    }

    public void test_decode_query() throws IllegalArgumentException, UnsupportedEncodingException, IOException {
        final ParameterMap map = new ParameterMap();
        final String query = "a=1&b=2&c=3&a=1&b=2&c=3";
        Util.parseQueryString(
                new ByteArrayInputStream(query.getBytes(Util.ENCODING_DIRECT)), Util.ENCODING_DIRECT, map, false);

        assertEquals(3, map.size());

        List<RequestParameter> pars = map.getRequestParameterList();
        assertEquals(6, pars.size());
        assertEquals("a", pars.get(0).getName());
        assertEquals("1", pars.get(0).getString());
        assertEquals("b", pars.get(1).getName());
        assertEquals("2", pars.get(1).getString());
        assertEquals("c", pars.get(2).getName());
        assertEquals("3", pars.get(2).getString());
        assertEquals("a", pars.get(3).getName());
        assertEquals("1", pars.get(3).getString());
        assertEquals("b", pars.get(4).getName());
        assertEquals("2", pars.get(4).getString());
        assertEquals("c", pars.get(5).getName());
        assertEquals("3", pars.get(5).getString());
    }

    public void test_getParameter_with_space() throws Exception {
        final ParameterMap map = new ParameterMap();
        final String query = "cmsaction=createPage&templateName=/apps/geometrixx/templates/contentpage"
                + "&label=&title=Some Page&parentPath=/content/geometrixx";
        Util.parseQueryString(
                new ByteArrayInputStream(query.getBytes(Util.ENCODING_DIRECT)), Util.ENCODING_DIRECT, map, false);
        assertEquals("createPage", map.getStringValue("cmsaction"));
        assertEquals("/apps/geometrixx/templates/contentpage", map.getStringValue("templateName"));
        assertEquals("", map.getStringValue("label"));
        assertEquals("Some Page", map.getStringValue("title"));
        assertEquals("/content/geometrixx", map.getStringValue("parentPath"));
    }

    public void test_decode_valid_escapes() throws Exception {
        final ParameterMap map = new ParameterMap();
        final String query = "a=%41%62c&b=x%2fy&c=%0d%0A";
        Util.parseQueryString(
                new ByteArrayInputStream(query.getBytes(Util.ENCODING_DIRECT)), Util.ENCODING_DIRECT, map, false);
        assertEquals("Abc", map.getStringValue("a"));
        assertEquals("x/y", map.getStringValue("b"));
        assertEquals("\r\n", map.getStringValue("c"));
    }

    public void test_signed_escape_sequences_rejected() throws Exception {
        // Integer.parseInt(s, 16) accepts a leading sign, a standards compliant
        // URL decoder does not: %+d/%+a would decode to CR/LF invisible to any
        // RFC 3986 decoder (e.g. a WAF), %-1 to byte 0xFF
        for (String query : new String[] {"r=x%+d%+ay", "r=x%-1", "%+d=x", "r=%4+"}) {
            try {
                Util.parseQueryString(
                        new ByteArrayInputStream(query.getBytes(Util.ENCODING_DIRECT)),
                        Util.ENCODING_DIRECT,
                        new ParameterMap(),
                        false);
                fail("Expected IllegalArgumentException for query: " + query);
            } catch (IllegalArgumentException expected) {
                // expected: signed/non-hex escape sequences are malformed
            }
        }
    }

    public void test_trailing_incomplete_escape_rejected() throws Exception {
        // a trailing "%2" was silently swallowed before, yielding value "b"
        // for "a=b%2" - malformed input must be rejected, not truncated
        for (String query : new String[] {"a=b%2", "a=b%", "a%2"}) {
            try {
                Util.parseQueryString(
                        new ByteArrayInputStream(query.getBytes(Util.ENCODING_DIRECT)),
                        Util.ENCODING_DIRECT,
                        new ParameterMap(),
                        false);
                fail("Expected IllegalArgumentException for query: " + query);
            } catch (IllegalArgumentException expected) {
                // expected: incomplete trailing escape sequence
            }
        }
    }

    public void test_bad_escape_sequence_message_is_sanitized() throws Exception {
        // raw CR/LF bytes after the '%' escape, as they may occur in an
        // application/x-www-form-urlencoded POST body; the exception message
        // (which ends up in the error log) must not carry them unneutralized,
        // otherwise the attacker can split the log record (CVE-2022-32549 class)
        final String query = "a=%\r\n&b=2";
        try {
            Util.parseQueryString(
                    new ByteArrayInputStream(query.getBytes(Util.ENCODING_DIRECT)),
                    Util.ENCODING_DIRECT,
                    new ParameterMap(),
                    false);
            fail("Expected IllegalArgumentException for the bad escape sequence");
        } catch (IllegalArgumentException expected) {
            final String message = expected.getMessage();
            assertFalse("message must not contain a raw CR", message.contains("\r"));
            assertFalse("message must not contain a raw LF", message.contains("\n"));
            assertTrue("message must contain the escaped CR", message.contains("\\u000d"));
            assertTrue("message must contain the escaped LF", message.contains("\\u000a"));
        }
    }
}
