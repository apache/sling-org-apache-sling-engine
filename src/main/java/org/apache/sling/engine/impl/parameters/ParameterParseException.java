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

/**
 * Unchecked exception indicating that the request parameters could not be
 * processed as-is, e.g. because the request exceeds the configured maximum
 * number of parameters. This is a client-caused, request-fatal condition: the
 * request must be refused (mapped to an HTTP 400 response by
 * {@code SlingRequestProcessorImpl}) rather than processed with a silently
 * truncated parameter map, which would let an attacker hide parameters from
 * Sling-side consumers while other parsers of the same request (edge WAFs,
 * the container) still see them.
 * <p>
 * Extends {@link IllegalStateException} to remain compatible with callers
 * that only checked for that (more generic) exception type.
 */
public class ParameterParseException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public ParameterParseException(final String message) {
        super(message);
    }
}
