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

package edu.washington.shibboleth.attribute.resolver.spring.dc.rws;

import java.util.HashMap;
import java.util.List;
import java.util.Vector;
import java.util.Map;
import java.util.StringTokenizer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.namespace.QName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;

import net.shibboleth.ext.spring.util.SpringSupport;

import net.shibboleth.idp.attribute.resolver.spring.dc.AbstractDataConnectorParser;
import net.shibboleth.idp.attribute.resolver.spring.dc.CacheConfigParser;
import net.shibboleth.idp.attribute.resolver.spring.dc.DataConnectorNamespaceHandler;
import net.shibboleth.utilities.java.support.xml.ElementSupport;
import net.shibboleth.utilities.java.support.logic.Constraint;
import net.shibboleth.utilities.java.support.primitive.StringSupport;
import net.shibboleth.utilities.java.support.xml.AttributeSupport;

import edu.washington.shibboleth.attribute.resolver.spring.dc.UWDataConnectorNamespaceHandler;
import edu.washington.shibboleth.attribute.resolver.dc.rws.impl.RwsDataConnector;
import edu.washington.shibboleth.attribute.resolver.dc.rws.impl.RwsDataConnector.AUTHENTICATION_TYPE;
import edu.washington.shibboleth.attribute.resolver.dc.rws.impl.RwsAttribute;
import edu.washington.shibboleth.attribute.resolver.dc.rws.impl.TemplatedQueryStringBuilder;


/** Spring bean definition parser for configuring a RWS data connector. */
public class RwsDataConnectorParser extends AbstractDataConnectorParser {

    /** data connector type name. */
    public static final QName TYPE_NAME = new QName(UWDataConnectorNamespaceHandler.NAMESPACE, "WebService");

    /** Class logger. */
    private final Logger log = LoggerFactory.getLogger(RwsDataConnectorParser.class);

    /** Name of resolution plug-in attribute. */
    public static final QName ATTRIBUTE_ELEMENT_NAME = new QName(UWDataConnectorNamespaceHandler.NAMESPACE,
            "Attribute");

    /** {@inheritDoc} */
    @Override protected Class<RwsDataConnector> getNativeBeanClass() {
        return RwsDataConnector.class;
    }

    /** {@inheritDoc} */
    @Override protected void doV2Parse(@Nonnull final Element config, @Nonnull final ParserContext parserContext,
            @Nonnull final BeanDefinitionBuilder builder) {

        log.debug("parsing v2 configuration {}", config);
        String pluginId = "placeholder";

        String httpDataSourceId = StringSupport.trimOrNull(config.getAttribute( "httpDataSourceRef"));
        Constraint.isNotNull(config, "httpDataSourceRef parameter is required");
        builder.addPropertyReference("httpDataSource", httpDataSourceId);

        builder.addPropertyValue("queryStringBuilder", createTemplatedQueryStringBuilder(config));

        final List<Element> attrElements = ElementSupport.getChildElements(config, ATTRIBUTE_ELEMENT_NAME);
        List<RwsAttribute> attributes = parseAttributes(attrElements);
        log.debug("Setting the following attributes for plugin {}: {}", pluginId, attributes);
        builder.addPropertyValue("rwsAttributes", attributes);

        String baseURL = StringSupport.trimOrNull(config.getAttribute( "baseURL"));
        log.debug("Data connector {} base URL: {}", pluginId, baseURL);
        builder.addPropertyValue("baseUrl", baseURL);

        AUTHENTICATION_TYPE authnType = AUTHENTICATION_TYPE.NONE;
        if (AttributeSupport.hasAttribute(config, new QName("authenticationType"))) {
            authnType = AUTHENTICATION_TYPE.valueOf(StringSupport.trimOrNull(config.getAttribute( "authenticationType")));
        }
        log.debug("Data connector {} authentication type: {}", pluginId, authnType);
        builder.addPropertyValue("authenticationType", authnType);

        String username = StringSupport.trimOrNull(config.getAttribute( "username"));
        if (username!=null) log.debug("Data connector {} username: {}", pluginId, username);
        builder.addPropertyValue("username", username);

        String password = StringSupport.trimOrNull(config.getAttribute( "password"));
        builder.addPropertyValue("password", password);


/***
        int maxConnections = 0;
        if (StringSupport.trimOrNull(config.getAttribute( "maxConnections")) {
            maxConnections = Integer.parseInt(StringSupport.trimOrNull(config.getAttribute( "maxConnections"));
        }
        log.debug("Data connector {} max connections: {}", pluginId, maxConnections);
        builder.addPropertyValue("maxConnections", maxConnections);

        int searchTimeLimit = 20000;
        if (StringSupport.trimOrNull(config.getAttribute( "searchTimeLimit")) {
            searchTimeLimit = Integer.parseInt(StringSupport.trimOrNull(config.getAttribute( "searchTimeLimit"));
        }
        log.debug("Data connector {} search timeout: {}ms", pluginId, searchTimeLimit);
        builder.addPropertyValue("searchTimeLimit", searchTimeLimit);

        int maxResultSize = 1;
        if (StringSupport.trimOrNull(config.getAttribute( "maxResultSize")) {
            maxResultSize = Integer.parseInt(StringSupport.trimOrNull(config.getAttribute( "maxResultSize"));
        }
        log.debug("Data connector {} max search result size: {}", pluginId, maxResultSize);
        builder.addPropertyValue("maxResultSize", maxResultSize);

        boolean cacheResults = false;
        if (StringSupport.trimOrNull(config.getAttribute( "cacheResults")) {
            cacheResults = AttributeSupport.getAttributeValueAsBoolean(StringSupport.trimOrNull(config.getAttribute( "cacheResults"));
        }
        log.debug("Data connector {} cache results: {}", pluginId, cacheResults);
        builder.addPropertyValue("cacheResults", cacheResults);

        boolean mergeResults = false;
        if (StringSupport.trimOrNull(config.getAttribute( "mergeResults")) {
            mergeResults = AttributeSupport.getAttributeValueAsBoolean(StringSupport.trimOrNull(config.getAttribute( "mergeResults"));
        }
        log.debug("Data connector{} merge results: {}", pluginId, mergeResults);
        builder.addPropertyValue("mergeResults", mergeResults);

        boolean noResultsIsError = false;
        if (StringSupport.trimOrNull(config.getAttribute( "noResultIsError")) {
            noResultsIsError = AttributeSupport.getAttributeValueAsBoolean(StringSupport.trimOrNull(config.getAttribute(
                    "noResultIsError"));
        }
        log.debug("Data connector {} no results is error: {}", pluginId, noResultsIsError);
        builder.addPropertyValue("noResultsIsError", noResultsIsError);

        int pollingFrequency = 60000;
        if (StringSupport.trimOrNull(config.getAttribute( "pollingFrequency")) {
            pollingFrequency = Integer.parseInt(StringSupport.trimOrNull(config.getAttribute( "pollingFrequency"));
        }
        log.debug("Data connector {} polling frequency: {}ms", pluginId, pollingFrequency);
        builder.addPropertyValue("pollingFrequency", pollingFrequency);

**/
    }

        /**
         * Construct the definition of the template driven search builder.
         *
         * @return the bean definition for the template search builder.
         */
        @Nonnull public BeanDefinition createTemplatedQueryStringBuilder(final Element config) {
            final BeanDefinitionBuilder templateBuilder =
                    BeanDefinitionBuilder.genericBeanDefinition(TemplatedQueryStringBuilder.class);
            templateBuilder.setInitMethodName("initialize");

            String velocityEngineRef = StringSupport.trimOrNull(config.getAttribute("templateEngine"));
            if (null == velocityEngineRef) {
                velocityEngineRef = "shibboleth.VelocityEngine";
            }
            templateBuilder.addPropertyReference("velocityEngine", velocityEngineRef);

            templateBuilder.addPropertyValue("v2Compatibility", true);

            String filter = null;
            final Element filterElement =
                    ElementSupport.getFirstChildElement(config, new QName(
                            UWDataConnectorNamespaceHandler.NAMESPACE, "QueryTemplate"));
            if (filterElement != null) {
                filter = StringSupport.trimOrNull(filterElement.getTextContent().trim());
            }

            templateBuilder.addPropertyValue("templateText", filter);
            System.out.println("adding templateText: " + filter);

            return templateBuilder.getBeanDefinition();
        }



    /**
     * Parse attribute requirements
     *
     * @param elements DOM elements of type <code>Attribute</code>
     *
     * @return the attributes
     */
    protected List<RwsAttribute> parseAttributes(List<Element> elements) {
        if (elements == null || elements.size() == 0) {
            return null;
        }
        List<RwsAttribute> rwsAttributes = new Vector<RwsAttribute>();
        for (Element ele : elements) {
            RwsAttribute rwsAttribute = new RwsAttribute();
            rwsAttribute.name = StringSupport.trimOrNull(ele.getAttributeNS(null, "name"));
    log.debug("parseattribute: " + rwsAttribute.name);
    System.out.println("parseattribute: " + rwsAttribute.name);
            rwsAttribute.xPath = StringSupport.trimOrNull(ele.getAttributeNS(null, "xPath"));
            rwsAttribute.maxResultSize = 1;
            if (ele.hasAttributeNS(null, "maxResultSize")) {
                   rwsAttribute.maxResultSize = Integer.parseInt(ele.getAttributeNS(null, "maxResultSize"));
            }
            boolean noResultsIsError = false;
            if (ele.hasAttributeNS(null, "noResultIsError")) {
                   rwsAttribute.noResultIsError = AttributeSupport.getAttributeValueAsBoolean(ele.getAttributeNodeNS(null, "noResultIsError"));
            }
            rwsAttributes.add(rwsAttribute);
        }
        return rwsAttributes;
    }




}
