/**
 * Copyright (c) Codice Foundation
 * 
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 * 
 **/
package org.codice.ddf.commands.cache;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.management.InstanceNotFoundException;
import javax.management.MalformedObjectNameException;

import org.apache.felix.gogo.commands.Command;
import org.apache.felix.gogo.commands.Option;
import org.opengis.filter.Filter;

import ddf.catalog.data.Metacard;
import ddf.catalog.data.Result;
import ddf.catalog.filter.FilterBuilder;
import ddf.catalog.operation.DeleteRequest;
import ddf.catalog.operation.SourceResponse;
import ddf.catalog.operation.impl.DeleteRequestImpl;
import ddf.catalog.operation.impl.QueryImpl;
import ddf.catalog.operation.impl.QueryRequestImpl;
import ddf.catalog.source.UnsupportedQueryException;

/**
 * 
 * Command to remove all or subset of records in the Metacard Cache.
 */

@Command(scope = CacheCommands.NAMESPACE, name = "removeall", description = "Attempts to delete all Metacards from the cache.")
public class RemoveAllCommand extends CacheCommands {

    static final String WARNING_MESSAGE = "WARNING: This will remove all records from the cache.  Do you want to proceed? (yes/no): ";

    @Option(name = "-f", required = false, aliases = {"--force"}, multiValued = false, description = "Force the removal without a confirmation message.")
    boolean force = false;

    @Override
    protected Object doExecute() throws Exception {

        List<String> ids = getCachedMetacardIds();

        DeleteRequest deleteRequest = new DeleteRequestImpl(ids.toArray(new String[ids.size()]));

        getCacheProxy().delete(deleteRequest);

        return null;

    }

    boolean isAccidentalRemoval(PrintStream console) throws IOException {
        if (!force) {
            StringBuffer buffer = new StringBuffer();
            System.err.println(String.format(WARNING_MESSAGE));
            System.err.flush();
            for (;;) {
                int byteOfData = session.getKeyboard().read();

                if (byteOfData < 0) {
                    // end of stream
                    return true;
                }
                System.err.print((char) byteOfData);
                if (byteOfData == '\r' || byteOfData == '\n') {
                    break;
                }
                buffer.append((char) byteOfData);
            }
            String str = buffer.toString();
            if (!str.equals("yes")) {
                console.println("No action taken.");
                return true;
            }
        }

        return false;
    }

    private List<String> getCachedMetacardIds()
            throws MalformedObjectNameException, UnsupportedQueryException, IOException,
            InstanceNotFoundException {
        List<String> ids = new ArrayList<>();
        FilterBuilder filterBuilder = getFilterBuilder();

        Filter filter = filterBuilder.attribute(Metacard.ID).is().like().text(WILDCARD);

        QueryImpl query = new QueryImpl(filter);

        query.setRequestsTotalResultsCount(true);

        Map<String, Serializable> properties = new HashMap<>();
        properties.put("mode", "native");

        SourceResponse response = null;

        response = getCacheProxy().query(new QueryRequestImpl(query, properties));

        while (response.getResults().size() > 0) {
            for (Result result : response.getResults()) {
                if (result != null && result.getMetacard() != null) {
                    ids.add(result.getMetacard().getId());
                }
            }
        }

        return ids;

    }

}
