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
package org.apache.sling.engine.impl.log;

import junit.framework.TestCase;
import org.apache.sling.engine.impl.request.RequestData;
import org.mockito.Mockito;

/**
 * The <code>CustomLogFormatTest</code> class tests the
 * <code>CustomLogFormat</code> class.
 */
public class CustomLogFormatTest extends TestCase {

    public void testCase0() {
        this.testCase0Helper("%t [%R] -> %m %U%q %H");
        this.testCase0Helper("%{Content-Type}i");
        this.testCase0Helper("%400t");
        this.testCase0Helper("%!400t");
        this.testCase0Helper("%300,400t");
        this.testCase0Helper("%!300,400t");
        this.testCase0Helper("%!300,400{Content-Type}i");
        this.testCase0Helper("xyz %Dms");
        this.testCase0Helper("xyz %{foo}M");
    }

    private void testCase0Helper(String format) {
        CustomLogFormat clf = new CustomLogFormat(format);
        String format2 = clf.toString();
        assertEquals(format, format2);
    }

    public void testHeaderEscape() {

        // single whitespace character
        assertEquals("\\n", CustomLogFormat.HeaderParameter.escape("\n"));
        assertEquals("\\r", CustomLogFormat.HeaderParameter.escape("\r"));
        assertEquals("\\t", CustomLogFormat.HeaderParameter.escape("\t"));
        assertEquals("\\f", CustomLogFormat.HeaderParameter.escape("\f"));
        assertEquals("\\b", CustomLogFormat.HeaderParameter.escape("\b"));

        // single special character
        assertEquals("\\\\", CustomLogFormat.HeaderParameter.escape("\\"));
        assertEquals("\\\"", CustomLogFormat.HeaderParameter.escape("\""));

        // plain word
        assertEquals("This is a plain word", CustomLogFormat.HeaderParameter.escape("This is a plain word"));

        // embedded whitespace special
        assertEquals("This is a\\nplain word", CustomLogFormat.HeaderParameter.escape("This is a\nplain word"));

        // embedded non-printable
        assertEquals("Das isch \\u00e4n Umlut", CustomLogFormat.HeaderParameter.escape("Das isch \u00e4n Umlut"));
        assertEquals(
                "This is a special character \\u1234",
                CustomLogFormat.HeaderParameter.escape("This is a special character \u1234"));
    }

    public void testRequestParameterValueEscaped() {
        final RequestLoggerRequest request = Mockito.mock(RequestLoggerRequest.class);
        Mockito.when(request.getParameter("ref"))
                .thenReturn("x\r\n192.168.1.1 - admin \"POST /system/console HTTP/1.1\" 200");

        final CustomLogFormat.ParamParameter param = new CustomLogFormat.ParamParameter("ref");
        final String value = param.getValue(request);

        // no raw CR/LF may end up in the log line
        assertFalse(value.contains("\r"));
        assertFalse(value.contains("\n"));
        assertEquals("x\\r\\n192.168.1.1 - admin \\\"POST /system/console HTTP/1.1\\\" 200", value);
    }

    public void testContentPathEscaped() {
        final RequestLoggerRequest request = Mockito.mock(RequestLoggerRequest.class);
        Mockito.when(request.getAttribute(RequestData.REQUEST_RESOURCE_PATH_ATTR))
                .thenReturn("/content/foo\r\nFORGED LINE");

        final CustomLogFormat.ContentPathParameter param = new CustomLogFormat.ContentPathParameter();
        final String value = param.getValue(request);

        assertFalse(value.contains("\r"));
        assertFalse(value.contains("\n"));
        assertEquals("/content/foo\\r\\nFORGED LINE", value);
    }

    public void testRemoteHostEscaped() {
        final RequestLoggerRequest request = Mockito.mock(RequestLoggerRequest.class);
        Mockito.when(request.getRemoteHost()).thenReturn("evil\nhost.example.com");

        final CustomLogFormat.RemoteHostParameter param = new CustomLogFormat.RemoteHostParameter();
        final String value = param.getValue(request);

        assertFalse(value.contains("\n"));
        assertEquals("evil\\nhost.example.com", value);
    }

    public void testThreadNameEscaped() {
        // %P (ThreadParameter) does not read from the request but from the
        // name of the thread handling it, and the threadname can contain the
        // requested path
        final Thread currentThread = Thread.currentThread();
        final String originalName = currentThread.getName();
        currentThread.setName("/content/\r\nfoo");
        try {
            final RequestLoggerRequest request = Mockito.mock(RequestLoggerRequest.class);
            final CustomLogFormat.ThreadParameter param = new CustomLogFormat.ThreadParameter(null);
            final String value = param.getValue(request);

            assertFalse(value.contains("\r"));
            assertFalse(value.contains("\n"));
            assertEquals("/content/\\r\\nfoo", value);
        } finally {
            currentThread.setName(originalName);
        }
    }
}
