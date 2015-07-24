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
import java.util.Comparator;
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

    private static final String MAP_ENTRY_ENABLED = "enabled";

    private static final String MAP_ENTRY_FPID = "fpid";

    private static final String MAP_ENTRY_NAME = "name";

    private static final String MAP_ENTRY_BUNDLE_NAME = "bundle_name";

    private static final String MAP_ENTRY_BUNDLE_LOCATION = "bundle_location";

    private static final String MAP_ENTRY_BUNDLE = "bundle";

    private static final String MAP_ENTRY_PROPERTIES = "properties";

    private static final String MAP_ENTRY_CONFIGURATIONS = "configurations";

    private static final String DISABLED = "_disabled";

    private static final String SERVICE_NAME = ":service=admin-source-poller-service";

    private final ObjectName objectName;

    private final MBeanServer mBeanServer;

    private final BundleContext bundleContext;

    private final ConfigurationAdmin configurationAdmin;

    public AdminSourcePollerServiceBean(ConfigurationAdmin configurationAdmin) {
        bundleContext = getBundleContext();
        this.configurationAdmin = configurationAdmin;
        mBeanServer = ManagementFactory.getPlatformMBeanServer();
        ObjectName objName = null;
        try {
            objName = new ObjectName(AdminSourcePollerServiceBean.class.getName() + SERVICE_NAME);
        } catch (MalformedObjectNameException e) {
            LOGGER.error("Unable to create Admin Source Poller Service MBean with name [{}].",
                    AdminSourcePollerServiceBean.class.getName() + SERVICE_NAME, e);
        }
        objectName = objName;
    }

    public void init() {
        try {
            try {
                mBeanServer.registerMBean(this, objectName);
                LOGGER.info(
                        "Registered Admin Source Poller Service Service MBean under object name: {}",
                        objectName.toString());
            } catch (InstanceAlreadyExistsException e) {
                // Try to remove and re-register
                mBeanServer.unregisterMBean(objectName);
                mBeanServer.registerMBean(this, objectName);
                LOGGER.info("Re-registered Admin Source Poller Service Service MBean");
            }
        } catch (Exception e) {
            LOGGER.error("Could not register MBean [{}].", objectName.toString(), e);
        }
    }

    public void destroy() {
        try {
            if (objectName != null && mBeanServer != null) {
                mBeanServer.unregisterMBean(objectName);
                LOGGER.info("Unregistered Admin Source Poller Service Service MBean");
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
                .addMetaTypeNamesToMap(configAdminExt.getFactoryPidObjectClasses(),
                        "(|(service.factoryPid=*source)(service.factoryPid=*Source)(service.factoryPid=*service)(service.factoryPid=*Service))",
                        "service.factoryPid");

        // Loop through each metatype and find its configurations
        for (Map metatype : metatypes) {
            try {
                Configuration[] configs = configurationAdmin.listConfigurations(
                        "(|(service.factoryPid=" + metatype.get(MAP_ENTRY_ID)
                                + ")(service.factoryPid=" + metatype.get(MAP_ENTRY_ID) + DISABLED
                                + "))");

                ArrayList<Map<String, Object>> configurations = new ArrayList<>();
                if (configs != null) {
                    for (Configuration config : configs) {
                        Map<String, Object> source = new HashMap<>();

                        boolean disabled = config.getPid().contains(DISABLED);
                        source.put(MAP_ENTRY_ID, config.getPid());
                        source.put(MAP_ENTRY_ENABLED, !disabled);
                        source.put(MAP_ENTRY_FPID, config.getFactoryPid());

                        if (!disabled) {
                            source.put(MAP_ENTRY_NAME,
                                    ((ObjectClassDefinition) nameMap.get(config.getFactoryPid()))
                                            .getName());
                            source.put(MAP_ENTRY_BUNDLE_NAME, configAdminExt
                                    .getName(bundleContext.getBundle(config.getBundleLocation())));
                            source.put(MAP_ENTRY_BUNDLE_LOCATION, config.getBundleLocation());
                            source.put(MAP_ENTRY_BUNDLE,
                                    bundleContext.getBundle(config.getBundleLocation())
                                            .getBundleId());
                        } else {
                            source.put(MAP_ENTRY_NAME, config.getPid());
                        }

                        Dictionary<String, Object> properties = config.getProperties();
                        Map<String, Object> plist = new HashMap<>();
                        for (String key : Collections.list(properties.keys())) {
                            plist.put(key, properties.get(key));
                        }
                        source.put(MAP_ENTRY_PROPERTIES, plist);

                        configurations.add(source);
                    }
                    metatype.put(MAP_ENTRY_CONFIGURATIONS, configurations);
                }
            } catch (Exception e) {
                LOGGER.warn("Error getting source info: {}", e.getMessage());
            }
        }

        Collections.sort(metatypes, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> o1, Map<String, Object> o2) {
                return ((String) o1.get("id")).compareToIgnoreCase((String) o2.get("id"));
            }
        });
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