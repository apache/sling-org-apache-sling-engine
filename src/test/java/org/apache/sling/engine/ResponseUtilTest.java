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
package org.apache.sling.engine;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@SuppressWarnings("deprecation")
public class ResponseUtilTest {

    @Test
    public void testNullInput() {
        assertNull(ResponseUtil.escapeXml(null));
    }

    @Test
    public void testNoEscapes() {
        assertEquals("foo and bar", ResponseUtil.escapeXml("foo and bar"));
    }

    @Test
    public void testEscapes() {
        assertEquals(
                "&lt;bonnie&gt; &amp; &lt;/clyde&gt; &amp;&amp; others are having fun with &quot; and &apos; characters",
                ResponseUtil.escapeXml("<bonnie> & </clyde> && others are having fun with \" and ' characters"));
    }

    @Test
    public void testXmlEscapingWriter() throws IOException {
        final StringWriter sw = new StringWriter();
        final Writer w = ResponseUtil.getXmlEscapingWriter(sw);
        w.write("<bonnie> & </clyde> && others are having fun with \" and ' characters");
        w.flush();
        assertEquals(
                "&lt;bonnie&gt; &amp; &lt;/clyde&gt; &amp;&amp; others are having fun with &quot; and &apos; characters",
                sw.toString());
    }
}
