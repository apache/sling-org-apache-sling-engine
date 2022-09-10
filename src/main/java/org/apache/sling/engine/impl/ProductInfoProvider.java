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

import java.util.Dictionary;
import java.util.Map;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

@Component(service = ProductInfoProvider.class)
public class ProductInfoProvider {

    /**
     * The name of the product to report in the {@link #getServerInfo()} method
     * (value is "ApacheSling").
     */
    public static String PRODUCT_NAME = "ApacheSling";

    /**
     * The product information part of the {@link #serverInfo} returns from the
     * <code>ServletContext.getServerInfo()</code> method. This field defaults
     * to {@link #PRODUCT_NAME} and is amended with the major and minor version
     * of the Sling Engine bundle while this component is being
     * {@link #activate(BundleContext, Map, Config)} activated}.
     */
    private volatile String productInfo = PRODUCT_NAME;

    @Activate
    protected void activate(final BundleContext bundleContext) {
        this.setProductInfo(bundleContext);
    }

    /**
     * Sets the {@link #productInfo} field from the providing bundle's version
     * and the {@link #PRODUCT_NAME}.
     * <p>
     * Also {@link #setServerInfo() updates} the {@link #serverInfo} based
     * on the product info calculated.
     *
     * @param bundleContext Provides access to the "Bundle-Version" manifest
     *            header of the containing bundle.
     */
    private void setProductInfo(final BundleContext bundleContext) {
        final Dictionary<?, ?> props = bundleContext.getBundle().getHeaders();
        final Version bundleVersion = Version.parseVersion((String) props.get(Constants.BUNDLE_VERSION));
        final String productVersion = bundleVersion.getMajor() + "."
            + bundleVersion.getMinor();
        this.productInfo = PRODUCT_NAME + "/" + productVersion;
    }

    public String getProductInfo() {
        return this.productInfo;
    }
}
