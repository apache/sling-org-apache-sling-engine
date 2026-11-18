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
package org.apache.sling.engine.impl.helper;

import org.jetbrains.annotations.Nullable;

public final class ContentTypeUtil {

    private ContentTypeUtil() {}

    /**
     * Returns the charset parameter of a Content-Type header, or {@code null}
     * if no non-empty charset parameter is present.
     */
    public static @Nullable String getCharset(@Nullable final String contentType) {
        if (contentType == null) {
            return null;
        }
        final String[] parts = contentType.split(";");
        for (int i = 1; i < parts.length; i++) {
            final String param = parts[i].trim();
            final int eq = param.indexOf('=');
            if (eq > 0 && "charset".equalsIgnoreCase(param.substring(0, eq).trim())) {
                String value = param.substring(eq + 1).trim();
                if (value.length() > 1 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
                    value = value.substring(1, value.length() - 1).trim();
                }
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }
}
