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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

import ddf.catalog.service.ConfiguredService;
import ddf.catalog.source.Source;
import ddf.catalog.source.opensearch.OpenSearchSource;

public class AdminSourcePollerTest {

    public static final String CONFIG_PID = "OpenSearchSourceFederated";

    public static final String FPID = "OpenSearchSource";

    public static MockedAdminSourcePoller poller;

    @BeforeClass
    public static void setup() {
        poller = new AdminSourcePollerTest().new MockedAdminSourcePoller(null);
    }

    @Test
    public void testAllSourceInfo() {
        List<Map<String, Object>> sources = poller.allSourceInfo();
        assertNotNull(sources);
        assertEquals(1, sources.size());
        assertTrue(sources.get(0).containsKey("configurations"));
    }

    @Test
    public void testSourceStatus() {
        assertTrue(poller.sourceStatus(CONFIG_PID));
        assertFalse(poller.sourceStatus("FAKE SOURCE"));
    }

    private class MockedAdminSourcePoller extends AdminSourcePollerServiceBean {
        public MockedAdminSourcePoller(ConfigurationAdmin configAdmin) {
            super(configAdmin);
        }

        @Override
        protected OsgiHelper getHelper() {
            OsgiHelper helper = mock(OsgiHelper.class);
            try {
                Configuration config = mock(Configuration.class);
                when(config.getPid()).thenReturn(CONFIG_PID);
                when(config.getFactoryPid()).thenReturn(FPID);
                when(config.getBundleLocation()).thenReturn(
                        "mvn:ddf.catalog.opensearch/catalog-opensearch-source/2.8.0-SNAPSHOT");
                Dictionary<String, Object> dict = new Hashtable<>();
                dict.put("endpointUrl", "https://localhost:8993/services/catalog/query");
                dict.put("service.pid", CONFIG_PID);
                dict.put("service.factoryPid", FPID);
                when(config.getProperties()).thenReturn(dict);

                OpenSearchSource source = mock(OpenSearchSource.class);
                when(source.isAvailable()).thenReturn(true);

                ArrayList<ServiceReference<? extends Source>> serviceReferences = new ArrayList<>();
                serviceReferences.add(null);
                when(helper.getServiceReferences()).thenReturn(serviceReferences);

                when(helper.getService(any(ServiceReference.class))).thenReturn(source);
                when(helper.getConfiguration(any(ConfiguredService.class))).thenReturn(config);

                Map<String, String> names = new HashMap<>();
                names.put(FPID, "Name of something for source");
                when(helper.getNameMap()).thenReturn(names);

                ArrayList<Map<String, Object>> metatypes = new ArrayList<>();
                Map<String, Object> metatype = new HashMap<>();
                metatype.put("id", "OpenSearchSource");
                metatype.put("name", "Catalog OpenSearch Federated Source");
                metatype.put("metatype", new ArrayList<Map<String, Object>>());
                metatypes.add(metatype);
                when(helper.getMetatypes()).thenReturn(metatypes);

                Configuration[] configs = new Configuration[1];
                configs[0] = config;
                when(helper.getConfigurations(anyMap())).thenReturn(configs);

                when(helper.getBundleName(any(Configuration.class)))
                        .thenReturn("DDF :: Catalog :: OpenSearch :: Source");
                when(helper.getBundleId(any(Configuration.class))).thenReturn((long) 12345);

                when(helper.getName(any(Configuration.class)))
                        .thenReturn("Name of something for source");
            } catch (Exception e) {

            }

            return helper;
        }
    }
}
