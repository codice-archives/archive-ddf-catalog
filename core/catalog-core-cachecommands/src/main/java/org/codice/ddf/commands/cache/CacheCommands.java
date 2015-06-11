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
package org.codice.ddf.commands.cache;

import java.io.IOException;
import java.lang.management.ManagementFactory;

import javax.management.InstanceNotFoundException;
import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.MBeanServerInvocationHandler;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.karaf.shell.console.OsgiCommandSupport;

import ddf.catalog.cache.solr.impl.SolrCacheMBean;
import ddf.catalog.filter.FilterBuilder;

public class CacheCommands extends OsgiCommandSupport {

    protected static final String NAMESPACE = "cache";

    protected static final String WILDCARD = "*";

    @Override
    protected Object doExecute() throws Exception {
        return null;
    }

    protected SolrCacheMBean getCacheProxy()
            throws IOException, MalformedObjectNameException, InstanceNotFoundException {

        ObjectName solrCacheObjectName = new ObjectName(SolrCacheMBean.OBJECTNAME);
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();

        return MBeanServerInvocationHandler
                .newProxyInstance(mBeanServer, solrCacheObjectName, SolrCacheMBean.class, false);

    }

    protected FilterBuilder getFilterBuilder() {
        return getService(FilterBuilder.class);
    }

}
