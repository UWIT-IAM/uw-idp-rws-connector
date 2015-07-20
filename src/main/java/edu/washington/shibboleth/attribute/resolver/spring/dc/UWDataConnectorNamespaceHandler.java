/* ========================================================================
 * Copyright (c) 2010 The University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================================
 */


package edu.washington.shibboleth.attribute.resolver.spring.dc;

import net.shibboleth.ext.spring.util.BaseSpringNamespaceHandler;

import edu.washington.shibboleth.attribute.resolver.spring.dc.rws.RwsDataConnectorParser;

/**
 * Spring namespace handler for UW's Shibboleth data connector namespace.
 */
public class UWDataConnectorNamespaceHandler extends BaseSpringNamespaceHandler {

    /** Namespace for this handler. */
    public static final String NAMESPACE = "urn:mace:washington.edu:idp:resolver:dc";

    /** {@inheritDoc} */
    public void init() {
        registerBeanDefinitionParser(RwsDataConnectorParser.TYPE_NAME,
                new RwsDataConnectorParser());
    }
}
