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

import org.codice.ddf.ui.admin.api.ConfigurationAdminExt;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.metatype.ObjectClassDefinition;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ddf.catalog.service.ConfiguredService;
import ddf.catalog.source.ConnectedSource;
import ddf.catalog.source.FederatedSource;
import ddf.catalog.source.Source;

public class AdminSourcePollerServiceBean implements AdminSourcePollerServiceBeanMBean {
    static final String META_TYPE_NAME = "org.osgi.service.metatype.MetaTypeService";

    private static final Logger LOGGER = LoggerFactory
            .getLogger(AdminSourcePollerServiceBean.class);

    private static final String MAP_ENTRY_ID = "id";

    private static final String MAP_ENTRY_NAME = "name";

    private static final String MAP_ENTRY_METATYPE = "metatype";

    private static final String MAP_ENTRY_CARDINALITY = "cardinality";

    private static final String MAP_ENTRY_DEFAULT_VALUE = "defaultValue";

    private static final String MAP_ENTRY_DESCRIPTION = "description";

    private static final String MAP_ENTRY_TYPE = "type";

    private static final String MAP_ENTRY_OPTION_LABELS = "optionLabels";

    private static final String MAP_ENTRY_OPTION_VALUES = "optionValues";

    private final Map<String, ServiceTracker> services = new HashMap<>();

    private ObjectName objectName;

    private MBeanServer mBeanServer;

    private BundleContext bundleContext;

    private ConfigurationAdmin configurationAdmin;

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
    public boolean sourceStatus(String servicePID) {
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
                            return service.isAvailable();
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

        return false;
    }

    @Override
    public List<Map<String, Object>> allSourceInfo() {
        ConfigurationAdminExt configAdminExt = new ConfigurationAdminExt(configurationAdmin);
        Map nameMap = configAdminExt.getFactoryPidObjectClasses();

        // Get list of metatypes
        List<Map<String, Object>> metatypes = configAdminExt
                .addMetaTypeNamesToMap(configAdminExt.getFactoryPidObjectClasses(), "",
                        "service.factoryPid");

        // Loop through each metatype and find its configurations
        for (Map metatype : metatypes) {
            try {
                Configuration[] configs = configurationAdmin.listConfigurations(
                        "(|(service.factoryPid=" + metatype.get("id") + ")(service.factoryPid="
                                + metatype.get("id") + "_disabled))");

                ArrayList<Map<String, Object>> configurations = new ArrayList<>();
                if (configs != null) {
                    for (Configuration config : configs) {
                        Map<String, Object> source = new HashMap<>();

                        boolean disabled = config.getPid().contains("_disabled");
                        source.put("id", config.getPid());
                        source.put("enabled", !disabled);
                        source.put("fpid", config.getFactoryPid());

                        if (!disabled) {
                            source.put("name",
                                    ((ObjectClassDefinition) nameMap.get(config.getFactoryPid()))
                                            .getName());
                            source.put("bundle_name", configAdminExt.getName(
                                    getBundleContext().getBundle(config.getBundleLocation())));
                            source.put("bundle_location", config.getBundleLocation());
                            source.put("bundle",
                                    getBundleContext().getBundle(config.getBundleLocation())
                                            .getBundleId());
                        } else {
                            source.put("name", config.getPid());
                        }

                        Dictionary<String, Object> properties = config.getProperties();
                        Map<String, Object> plist = new HashMap<>();
                        for (String key : Collections.list(properties.keys())) {
                            plist.put(key, properties.get(key));
                        }
                        source.put("properties", plist);

                        configurations.add(source);
                    }
                    metatype.put("configurations", configurations);
                }
            } catch (Exception e) {
                LOGGER.warn("Error getting source info: {}", e.getMessage());
            }
        }

        return metatypes;
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
        List<ServiceReference<? extends Source>> refs = new ArrayList<>();

        refs.addAll(bundleContext.getServiceReferences(FederatedSource.class, null));
        refs.addAll(bundleContext.getServiceReferences(ConnectedSource.class, null));
        return refs;
    }
}