/**
 * Copyright (c) Codice Foundation
 * <p/>
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */

package org.codice.ddf.catalog.admin.plugin;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ddf.catalog.service.ConfiguredService;
import ddf.catalog.source.ConnectedSource;
import ddf.catalog.source.FederatedSource;
import ddf.catalog.source.Source;

public class AdminSourcePollerServiceBean implements AdminSourcePollerServiceBeanMBean {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(AdminSourcePollerServiceBean.class);

    private ObjectName objectName;

    private MBeanServer mBeanServer;

    private BundleContext bundleContext;

    private ConfigurationAdmin configurationAdmin;

    private Map<String, Object> configurationDataMap;

    public AdminSourcePollerServiceBean(ConfigurationAdmin configurationAdmin) {
        bundleContext = getBundleContext();
        this.configurationAdmin = configurationAdmin;

        try {
            objectName = new ObjectName(AdminSourcePollerServiceBean.class.getName()
                    + ":service=admin-source-poller-service");
            mBeanServer = ManagementFactory.getPlatformMBeanServer();
        } catch (MalformedObjectNameException e) {
            LOGGER.error("Unable to create Insecure Defaults Service MBean with name [{}].",
                    objectName.toString(), e);
        }
    }

    public void init() {
        try {
            try {
                mBeanServer.registerMBean(this, objectName);
                LOGGER.info("Registered Insecure Defaults Service MBean under object name: {}",
                        objectName.toString());
            } catch (InstanceAlreadyExistsException e) {
                // Try to remove and re-register
                mBeanServer.unregisterMBean(objectName);
                mBeanServer.registerMBean(this, objectName);
                LOGGER.info("Re-registered Insecure Defaults Service MBean");
            }
        } catch (Exception e) {
            LOGGER.error("Could not register MBean [{}].", objectName.toString(), e);
        }
    }

    public void destroy() {
        try {
            if (objectName != null && mBeanServer != null) {
                mBeanServer.unregisterMBean(objectName);
                LOGGER.info("Unregistered Insecure Defaults Service MBean");
            }
        } catch (Exception e) {
            LOGGER.error("Exception unregistering MBean [{}].", objectName.toString(), e);
        }
    }

    @Override
    public String sourceStatus(String servicePID) {
        try {
            List<ServiceReference<? extends Source>> sourceReferences = getServiceReferences();

            for (ServiceReference<? extends Source> ref : sourceReferences) {
                Source service = bundleContext.getService(ref);
                if (service instanceof ConfiguredService) {
                    ConfiguredService cs = (ConfiguredService) service;
                    try {
                        Configuration config = configurationAdmin
                                .getConfiguration(cs.getConfigurationPid());
                        if (config.getProperties().get("service.pid").equals(servicePID)) {
                            boolean isAvailable = service.isAvailable();
                            if (isAvailable) {
                                return "true";
                            }
                        }

                    } catch (IOException e) {
                        LOGGER.warn("Couldn't find configuration for source '{}'", service.getId());
                    }
                } else {
                    LOGGER.warn("Source '{}' not a configured service", service.getId());
                }
            }
        } catch (InvalidSyntaxException e) {
            LOGGER.error("Could not get service reference list");
        }

        return "false";
    }

    @Override
    public List<Map<String, Object>> allSourceInfo() {
        ArrayList<Map<String, Object>> result = new ArrayList<>();

        try {
            List<ServiceReference<? extends Source>> sourceReferences = getServiceReferences();

            for (ServiceReference<? extends Source> ref : sourceReferences) {
                Source service = bundleContext.getService(ref);
                if (service instanceof ConfiguredService) {
                    ConfiguredService cs = (ConfiguredService) service;
                    try {
                        Configuration config = configurationAdmin
                                .getConfiguration(cs.getConfigurationPid());

                        Map<String, Object> source = new HashMap<>();
                        Dictionary<String, Object> properties = config.getProperties();
                        for (String key : Collections.list(properties.keys())) {
                            source.put(key, properties.get(key));
                        }
                        result.add(source);
                    } catch (IOException e) {
                        LOGGER.warn("Couldn't find configuration for source '{}'", service.getId());
                    }
                } else {
                    LOGGER.warn("Source '{}' not a configured service", service.getId());
                }
            }
        } catch (InvalidSyntaxException e) {
            LOGGER.error("Could not get service reference list");
        }

        return result;
    }

    BundleContext getBundleContext() {
        Bundle bundle = FrameworkUtil.getBundle(AdminSourcePollerServiceBean.class);
        if (bundle != null) {
            return bundle.getBundleContext();
        }
        return null;
    }

    private List<ServiceReference<? extends Source>> getServiceReferences()
            throws org.osgi.framework.InvalidSyntaxException {
        List<ServiceReference<? extends Source>> refs = new ArrayList<ServiceReference<? extends Source>>();

        refs.addAll(bundleContext.getServiceReferences(FederatedSource.class, null));
        refs.addAll(bundleContext.getServiceReferences(ConnectedSource.class, null));
        return refs;
    }
}
