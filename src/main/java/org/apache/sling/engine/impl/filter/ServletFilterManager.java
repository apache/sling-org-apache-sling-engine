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
package org.apache.sling.engine.impl.filter;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;

import org.apache.sling.commons.osgi.OsgiUtil;
import org.apache.sling.engine.EngineConstants;
import org.apache.sling.engine.impl.console.WebConsoleConfigPrinter;
import org.apache.sling.engine.impl.helper.SlingFilterConfig;
import org.apache.sling.engine.impl.helper.SlingServletContext;
import org.apache.sling.engine.jmx.FilterProcessorMBean;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServletFilterManager extends ServiceTracker<Filter, Filter> {

    private static final String JMX_OBJECTNAME = "jmx.objectname";

    public static enum FilterChainType {
        /**
         * Indicates request level filters.
         *
         * @see EngineConstants#FILTER_SCOPE_REQUEST
         */
        REQUEST("Request"),

        /**
         * Indicates error level filters.
         *
         * @see EngineConstants#FILTER_SCOPE_ERROR
         */
        ERROR("Error"),

        /**
         * Indicates include level filters.
         *
         * @see EngineConstants#FILTER_SCOPE_INCLUDE
         */
        INCLUDE("Include"),

        /**
         * Indicates forward level filters.
         *
         * @see EngineConstants#FILTER_SCOPE_FORWARD
         */
        FORWARD("Forward"),

        /**
         * Indicates component level filters.
         *
         * @see EngineConstants#FILTER_SCOPE_COMPONENT
         */
        COMPONENT("Component");

        private final String message;

        private FilterChainType(final String message) {
            this.message = message;
        }

        @Override
        public String toString() {
            return message;
        }
    }

    /** default log */
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final SlingServletContext servletContext;

    private final SlingFilterChainHelper[] filterChains;

    private final Map<Long, MBeanReg> mbeanMap = new ConcurrentHashMap<>();

    private volatile ServiceRegistration printerRegistration;

    private static final org.osgi.framework.Filter SERVICE_FILTER;
    static {
        org.osgi.framework.Filter f = null;
        try {
            f = FrameworkUtil.createFilter("(&(" + Constants.OBJECTCLASS + "=" + Filter.class.getName()
                    + ")(|(" + EngineConstants.SLING_FILTER_SCOPE + "=*)(" + EngineConstants.FILTER_SCOPE + "=*)))");
        } catch (InvalidSyntaxException e) {
            // we ignore this here as the above is a constant expression
        }
        SERVICE_FILTER = f;
    }

    public ServletFilterManager(final BundleContext context,
            final SlingServletContext servletContext) {
        super(context, SERVICE_FILTER, null);
        this.servletContext = servletContext;
        this.filterChains = new SlingFilterChainHelper[FilterChainType.values().length];
        for (final FilterChainType type : FilterChainType.values()) {
            this.filterChains[type.ordinal()] = new SlingFilterChainHelper();
        }
    }

    @Override
    public void open() {
        super.open();
        // Setup configuration printer
        printerRegistration = WebConsoleConfigPrinter.register(context, this);

    }

    @Override
    public void close() {
        if (this.printerRegistration != null) {
            WebConsoleConfigPrinter.unregister(this.printerRegistration);
            this.printerRegistration = null;
        }
        super.close();
    }

    public SlingFilterChainHelper getFilterChain(final FilterChainType chain) {
        return filterChains[chain.ordinal()];
    }

    public FilterHandle[] getFilters(final FilterChainType chain) {
        return getFilterChain(chain).getFilters();
    }

    @Override
    public Filter addingService(ServiceReference<Filter> reference) {
        Filter service = super.addingService(reference);
        if (service != null) {
            initFilter(reference, service);
        }
        return service;
    }

    @Override
    public void modifiedService(ServiceReference<Filter> reference, Filter service) {
        // only if the filter name has changed, we need to do a service re-registration
        final String newFilterName = SlingFilterConfig.getName(reference);
        if (newFilterName.equals(getUsedFilterName(reference))) {
            removeFilterFromChains((Long) reference.getProperty(Constants.SERVICE_ID));
            addFilterToChains(service, reference);
        } else {
            destroyFilter(reference, service);
            initFilter(reference, service);
        }
    }

    @Override
    public void removedService(ServiceReference<Filter> reference, Filter service) {
        if ( service != null ) {
            destroyFilter(reference, service);
            super.removedService(reference, service);
        }
    }

    private void initFilter(final ServiceReference<Filter> reference,
            final Filter filter) {
        final String filterName = SlingFilterConfig.getName(reference);
        final Long serviceId = (Long) reference.getProperty(Constants.SERVICE_ID);

        try {

            MBeanReg reg;
            try {
                final Dictionary<String, String> mbeanProps = new Hashtable<String, String>();
                mbeanProps.put(JMX_OBJECTNAME, "org.apache.sling:type=engine-filter,service=" + filterName);
                reg = new MBeanReg();
                reg.mbean = new FilterProcessorMBeanImpl();

                reg.registration = reference.getBundle().getBundleContext().registerService(FilterProcessorMBean.class,
                        reg.mbean, mbeanProps);

                mbeanMap.put(serviceId, reg);
            } catch (Throwable t) {
                log.debug("Unable to register mbean", t);
                reg = null;
            }

            // initialize the filter first
            final FilterConfig config = new SlingFilterConfig(servletContext, reference, filterName);
            filter.init(config);

            // add to chains
            addFilterToChains(filter, reference);
        } catch (ServletException ce) {
            log.error("Filter " + filterName + " failed to initialize", ce);
        } catch (Throwable t) {
            log.error("Unexpected problem initializing filter " + filterName, t);
        }
    }

    private String getUsedFilterName(final ServiceReference<Filter> reference) {
        final MBeanReg reg = mbeanMap.get(reference.getProperty(Constants.SERVICE_ID));
        if (reg != null) {
            final String objectName = (String) reg.registration.getReference().getProperty(JMX_OBJECTNAME);
            if (objectName != null) {
                final int pos = objectName.indexOf(",service=");
                if (pos != -1) {
                    return objectName.substring(pos + 9);
                }
            }
        }
        return null;
    }

    private void destroyFilter(final ServiceReference<Filter> reference,
            final Filter filter) {
        // service id
        Long serviceId = (Long) reference.getProperty(Constants.SERVICE_ID);

        final MBeanReg reg = mbeanMap.remove(serviceId);
        if (reg != null) {
            reg.registration.unregister();
        }

        // destroy it
        if (removeFilterFromChains(serviceId)) {
            try {
                filter.destroy();
            } catch (Throwable t) {
                log.error("Unexpected problem destroying Filter {}", filter, t);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void addFilterToChains(final Filter filter, final ServiceReference<Filter> reference) {
        final Long serviceId = (Long) reference.getProperty(Constants.SERVICE_ID);
        final MBeanReg mbeanReg = mbeanMap.get(serviceId);
        final FilterProcessorMBeanImpl mbean = mbeanReg == null ? null : mbeanReg.mbean;

        // get the order, Integer.MAX_VALUE by default
        final String orderSource;
        Object orderObj = reference.getProperty(Constants.SERVICE_RANKING);
        if (orderObj == null) {
            // filter order is defined as lower value has higher
            // priority while service ranking is the opposite In
            // addition we allow different types than Integer
            orderObj = reference.getProperty(EngineConstants.FILTER_ORDER);
            if (orderObj != null) {
                log.warn("Filter service {} is using deprecated property {}. Use {} instead.",
                        new Object[] { reference, EngineConstants.FILTER_ORDER, Constants.SERVICE_RANKING });
                // we can use 0 as the default as this will be applied
                // in the next step anyway if this props contains an
                // invalid value
                orderSource = EngineConstants.FILTER_ORDER + "=" + orderObj;
                orderObj = Integer.valueOf(-1 * OsgiUtil.toInteger(orderObj, 0));
            } else {
                orderSource = "none";
            }
        } else {
            orderSource = Constants.SERVICE_RANKING + "=" + orderObj;
        }
        final int order = (orderObj instanceof Integer) ? ((Integer) orderObj).intValue() : 0;

        // register by scope
        String[] scopes = OsgiUtil.toStringArray(reference.getProperty(EngineConstants.SLING_FILTER_SCOPE), null);

        FilterPredicate predicate = new FilterPredicate(reference);

        if (scopes == null) {
            scopes = OsgiUtil.toStringArray(reference.getProperty(EngineConstants.FILTER_SCOPE), null);
            log.warn("Filter service {} is using deprecated property {}. Use {} instead.",
                    new Object[] { reference, EngineConstants.FILTER_SCOPE, EngineConstants.SLING_FILTER_SCOPE });
        }
        if (scopes != null && scopes.length > 0) {
            for (String scope : scopes) {
                scope = scope.toUpperCase();
                try {
                    FilterChainType type = FilterChainType.valueOf(scope.toString());
                    getFilterChain(type).addFilter(filter, predicate, serviceId, order, orderSource, mbean);

                    if (type == FilterChainType.COMPONENT) {
                        getFilterChain(FilterChainType.INCLUDE).addFilter(filter, predicate, serviceId, order,
                                orderSource, mbean);
                        getFilterChain(FilterChainType.FORWARD).addFilter(filter, predicate, serviceId, order,
                                orderSource, mbean);
                    }

                } catch (IllegalArgumentException iae) {
                    // TODO: log ...
                }
            }
        } else {
            log.warn(String.format("A Filter (Service ID %s) has been registered without a %s property.", serviceId,
                    EngineConstants.SLING_FILTER_SCOPE));
            getFilterChain(FilterChainType.REQUEST).addFilter(filter, predicate, serviceId, order, orderSource, mbean);
        }

    }

    private boolean removeFilterFromChains(final Long serviceId) {
        boolean removed = false;
        for (SlingFilterChainHelper filterChain : filterChains) {
            removed |= filterChain.removeFilterById(serviceId);
        }
        return removed;
    }

    private static final class MBeanReg {
        FilterProcessorMBeanImpl mbean;
        ServiceRegistration<FilterProcessorMBean> registration;
    }
}
