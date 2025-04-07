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

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

import jakarta.servlet.http.Part;

/**
 * The <code>MultipartRequestParameter</code> represents a request parameter
 * from a multipart/form-data POST request.
 * <p>
 * To not add a dependency to Servlet API 3 this class does not implement the
 * Servlet API 3 {@code Part} interface. To support Servlet API 3 {@code Part}s
 * the {@link SlingPart} class wraps instances of this class.
 */
public class MultipartRequestParameter extends AbstractRequestParameter {

    private final Part part;

    private String encodedFileName;

    private String cachedValue;

    private byte[] cachedContent;

    public static final String FORM_DATA = "form-data";

    public static final String ATTACHMENT = "attachment";

    private static final String FILENAME_KEY = "filename";

    public MultipartRequestParameter(Part part) {
        this(part, null);
    }

    public MultipartRequestParameter(Part part, String encoding) {
        super(part.getName(), encoding);
        this.part = part;
    }

    void dispose() throws IOException {
        this.part.delete();
    }

    Part getPart() {
        return this.part;
    }

    @Override
    void setEncoding(String encoding) {
        super.setEncoding(encoding);
        cachedValue = null;
    }

    public byte[] get() {
        if (cachedContent != null) {
            return cachedContent;
        }
        // read the content of the part into a byte array
        try (InputStream in = part.getInputStream()) {
            cachedContent = in.readAllBytes();
            return cachedContent;
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    public String getContentType() {
        return this.part.getContentType();
    }

    public InputStream getInputStream() throws IOException {
        return this.part.getInputStream();
    }

    public String getFileName() {
        return this.part.getSubmittedFileName();
    }

    public long getSize() {
        return this.part.getSize();
    }

    public String getString() {
        // only apply encoding in the case of a form field
        if (this.isFormField()) {
            if (this.cachedValue == null) {
                // try explicit encoding if available
                byte[] data = get();
                String encoding = getEncoding();
                if (encoding != null) {
                    try {
                        this.cachedValue = new String(data, encoding);
                    } catch (UnsupportedEncodingException uee) {
                        // don't care, fall back to platform default
                    }
                }

                // if there is no encoding, or an illegal encoding,
                // use platform default
                if (cachedValue == null) {
                    cachedValue = new String(data);
                }
            }

            return this.cachedValue;
        }

        return new String(this.get(), getCharset());
    }

    public String getString(String enc) throws UnsupportedEncodingException {
        return new String(this.get(), enc);
    }

    public boolean isFormField() {
        return getFileName() == null;
    }

    public String toString() {
        if (this.isFormField()) {
            return this.getString();
        }

        return "File: " + this.getFileName() + " (" + this.getSize() + " bytes)";
    }

    private Charset getCharset() {
        final String contentType = getContentType();
        if (contentType != null) {
            for (String param : contentType.split(";")) {
                if (param.trim().toLowerCase().startsWith("charset=")) {
                    return Charset.forName(param.trim().substring("charset=".length()));
                }
            }
        }
        return Charset.forName(Util.ENCODING_DIRECT);
    }
}
