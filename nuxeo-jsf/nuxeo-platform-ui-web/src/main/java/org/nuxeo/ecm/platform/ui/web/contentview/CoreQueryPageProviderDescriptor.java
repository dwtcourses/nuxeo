/*
 * (C) Copyright 2010 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Anahide Tchertchian
 */
package org.nuxeo.ecm.platform.ui.web.contentview;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XNodeList;
import org.nuxeo.common.xmap.annotation.XNodeMap;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.ecm.core.api.SortInfo;

/**
 * Core Query page provider descriptor.
 *
 * @author Anahide Tchertchian
 */
@XObject("coreQueryPageProvider")
public class CoreQueryPageProviderDescriptor implements PageProviderDescriptor {

    private static final long serialVersionUID = 1L;

    @XNode("@enabled")
    boolean enabled = true;

    @XNodeMap(value = "property", key = "@name", type = HashMap.class, componentType = String.class)
    Map<String, String> properties = new HashMap<String, String>();

    @XNodeList(value = "parameter", type = String[].class, componentType = String.class)
    String[] queryParameters;

    @XNode("pageSize")
    long pageSize = 0;

    @XNode("sortable")
    boolean sortable = true;

    @XNodeList(value = "sort", type = ArrayList.class, componentType = SortInfoDescriptor.class)
    List<SortInfoDescriptor> sortInfos;

    protected String pattern;

    @XNode("pattern@quoteParameters")
    protected boolean quotePatternParameters = true;

    @XNode("whereClause")
    protected WhereClauseDescriptor whereClause;

    @XNode("pattern")
    public void setPattern(String pattern) {
        // remove new lines and following spaces
        if (pattern != null) {
            this.pattern = pattern.replaceAll("\r?\n\\s*", " ");
        }
    }

    public boolean getQuotePatternParameters() {
        return quotePatternParameters;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public String[] getQueryParameters() {
        return queryParameters;
    }

    public String getPattern() {
        return pattern;
    }

    public WhereClauseDescriptor getWhereClause() {
        return whereClause;
    }

    public boolean isSortable() {
        return sortable;
    }

    public List<SortInfo> getSortInfos() {
        List<SortInfo> res = new ArrayList<SortInfo>();
        if (sortInfos != null) {
            for (SortInfoDescriptor sortInfo : sortInfos) {
                res.add(sortInfo.getSortInfo());
            }
        }
        return res;
    }

    public long getPageSize() {
        return pageSize;
    }

    public boolean isEnabled() {
        return enabled;
    }

}