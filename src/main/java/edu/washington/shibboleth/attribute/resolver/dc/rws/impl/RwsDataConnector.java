/* ========================================================================
 * Copyright (c) 2015 The University of Washington
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

package edu.washington.shibboleth.attribute.resolver.dc.rws.impl;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.ArrayList;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.StringTokenizer;
import java.lang.IllegalArgumentException;

import java.net.URL;
import java.net.MalformedURLException;
import javax.xml.parsers.ParserConfigurationException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.namespace.QName;

import javax.naming.NamingException;
import javax.naming.directory.SearchResult;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import javax.xml.transform.dom.DOMSource;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;

import org.opensaml.security.x509.X509Credential;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

import net.shibboleth.idp.attribute.IdPAttribute;
import net.shibboleth.idp.attribute.IdPAttributeValue;
import net.shibboleth.idp.attribute.StringAttributeValue;
import net.shibboleth.idp.attribute.resolver.AbstractDataConnector;
import net.shibboleth.idp.attribute.resolver.PluginDependencySupport;
import net.shibboleth.idp.attribute.resolver.ResolutionException;

import net.shibboleth.idp.attribute.resolver.context.AttributeResolutionContext;
import net.shibboleth.idp.attribute.resolver.context.AttributeResolverWorkContext;


// import net.shibboleth.idp.attribute.resolver.dc.impl.AbstractSearchDataConnector;
// import net.shibboleth.idp.attribute.resolver.dc.impl.ValidationException;
// import net.shibboleth.idp.attribute.resolver.dc.impl.Validator;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.component.ComponentSupport;
import net.shibboleth.utilities.java.support.logic.Constraint;
import net.shibboleth.utilities.java.support.primitive.StringSupport;


import edu.washington.shibboleth.attribute.resolver.dc.rws.HttpDataSource;




/**
 * <code>RwsDataConnector</code> provides a plugin to retrieve attributes from a restful webservice.
 */
public class RwsDataConnector extends AbstractDataConnector {

    /** Authentication type values. */
    public static enum AUTHENTICATION_TYPE {
        /** No authentication type. */
        NONE,
        /** Basic authentication type. */
        BASIC,
        /** Client cretificate authentication type. */
        CLIENT_CERT
    };

    /** Class logger. */
    private static Logger log = LoggerFactory.getLogger(RwsDataConnector.class);

    /** values returned by this connector. */
    private Map<String, IdPAttribute> attributes;

    /** HTTP data source **/
    private HttpDataSource httpDataSource;

    /** builder for query string **/
    private TemplatedQueryStringBuilder queryStringBuilder;

    /** Authentication type */
    private AUTHENTICATION_TYPE authenticationType;

    /** Username if basic auth */
    private String username;

    /** Password if basic auth */
    private String password;

    /** Cred provider if basic auth */
    private CredentialsProvider credsProvider;

    /** Whether an empty result set is an error. */
    private boolean noResultsIsError;

    /** Whether to cache search results for the duration of the session. */
    private boolean cacheResults;

    /** Time, in milliseconds, to wait for a search to return. */
    private int searchTimeLimit;

    /** Base url of the webservice */
    private String baseUrl;
    private URL baseURL;
    private int basePort;

    /** Data cache. */
    // private Map<String, Map<String, Map<String, IdPAttribute>>> cache;

    /** Whether this data connector has been initialized. */
    private boolean initialized;

    /** Filter value escaping strategy. */
    // private final URLValueEscapingStrategy escapingStrategy = null;

    /** max results. */
    private int maxResults;

    /** parser for ws response. */
    private DocumentBuilder documentBuilder;

    /** xpath evaluator */
    XPathExpression xpathExpression;

    /** Attributes to fetch */
    private List<RwsAttribute> rwsAttributes;

    /**
     * This creates a new data connector with the supplied properties.
     * 
     */
    public RwsDataConnector() {
        super();

    }

    /**
     * Initializes the connector
     */
    @Override protected void doInitialize() throws ComponentInitializationException {
        super.doInitialize();

        if (httpDataSource == null) {
            throw new ComponentInitializationException(getLogPrefix() + " no http data source was configured");
        }

        try {
           DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
           domFactory.setNamespaceAware(false);  // parameter
           domFactory.setValidating(false);
           String feature = "http://apache.org/xml/features/nonvalidating/load-external-dtd";
           domFactory.setFeature(feature, false);
           feature = "http://apache.org/xml/features/nonvalidating/load-dtd-grammar";
           domFactory.setFeature(feature, false);
           documentBuilder = domFactory.newDocumentBuilder();

         } catch (ParserConfigurationException e) {
           log.error("javax.xml.parsers.ParserConfigurationException: " + e);
         }

         for (int i=0; i<rwsAttributes.size(); i++) {
             RwsAttribute attr = rwsAttributes.get(i);
             try {
                XPath xpath = XPathFactory.newInstance().newXPath();
                log.debug("xpath for {} is {}", attr.name, attr.xPath);
                attr.xpathExpression = xpath.compile(attr.xPath);
             } catch (XPathExpressionException e) {
                log.error("xpath expr: " + e);
             }
         }

         // initializeCache();

    }


    /** {@inheritDoc} */
    @Override
    @Nonnull protected Map<String, IdPAttribute> doDataConnectorResolve(
            @Nonnull final AttributeResolutionContext resolutionContext,
            @Nonnull final AttributeResolverWorkContext workContext) throws ResolutionException {
        ComponentSupport.ifNotInitializedThrowUninitializedComponentException(this);
        ComponentSupport.ifDestroyedThrowDestroyedComponentException(this);

        final Map<String, List<IdPAttributeValue<?>>> dependsAttributes =
                PluginDependencySupport.getAllAttributeValues(workContext, getDependencies());

        String queryString = queryStringBuilder.getQueryString(resolutionContext, dependsAttributes);
        queryString = queryString.trim();
        log.debug("RWS query filter: {}", queryString);

        // create Attribute objects to return
        Map<String, IdPAttribute> attributes = null;

        // not using cache for now 

        if (attributes == null) {
            log.debug("Retrieving attributes from GWS");
            attributes = getRwsAttributes(queryString);
            // if (cacheResults && attributes != null) {
                // setCachedAttributes(resolutionContext, queryString, attributes);
                // log.debug("Stored results in the cache");
            // }
        }


        log.trace("{} Resolved attributes: {}", getLogPrefix(), attributes);
        return attributes;
    }

    /**
     * This queries the web service and return the resolved attributes.
     *
     * @param queryString <code>String</code> the queryString for the rest get
     * @return <code>List</code> of results
     * @throws ResolutionException if an error occurs performing the search
     */
    protected Map<String, IdPAttribute> getRwsAttributes(String queryString) throws ResolutionException {
      try {
        String xml = httpDataSource.getResource(baseUrl + queryString);

        /** Java 7 documentation removed the thread warnings from DocumentBuilderFactory and DocumentBuilder.
            If these are still not thread safe this parsing should be synchronized. */

        Document doc = documentBuilder.parse(new InputSource(new StringReader(xml)));

        Map<String, IdPAttribute> attributes = new HashMap<String, IdPAttribute>();

        /* look for the requested attributes */
        for (int i=0; i<rwsAttributes.size(); i++) {
           RwsAttribute attr = rwsAttributes.get(i);

           Object result = attr.xpathExpression.evaluate(doc, XPathConstants.NODESET);
           NodeList nodes = (NodeList) result;
           log.debug("got {} matches to the xpath for {}", nodes.getLength(), attr.name);

           List<String> results = new Vector<String>();
           if (nodes.getLength()==0 && attr.noResultIsError) {
              log.error("got no attributes for {}, which required attriubtes", attr.name);
              throw new ResolutionException("no attributes for " + attr.name);
           }
           for (int j = 0; j < nodes.getLength(); j++) {
              if (maxResults>0 && maxResults<j) {
                  log.error("too many results for {}", attr.name);
                  break;
              }
              results.add((String)nodes.item(j).getTextContent());
           }
           addIdPAttributes(attributes, attr.name, results);
        }
        return attributes;

      } catch (IOException e) {
          log.error("get rws io excpet: " + e);
          throw new ResolutionException();
      } catch (SAXException e) {
          log.error("get rws sax excpet: " + e);
          throw new ResolutionException();
      } catch (IllegalArgumentException e) {
          log.error("get rws arg excpet: " + e);
          throw new ResolutionException();
      } catch (XPathExpressionException e) {
          log.error("get rws xpath excpet: " + e);
          throw new ResolutionException();
      }

    }

    /**
     * This adds an attribute name and value to the IdP's list of attributes
     *
     */
    protected void addIdPAttributes(Map<String, IdPAttribute> attributes, String name, List<String> results) {

        if (results.size()>0) {

        IdPAttribute attribute = new IdPAttribute(name);

        List<IdPAttributeValue<?>> values = new ArrayList<>(results.size());
        for(String result : results){
            values.add(new StringAttributeValue(result));
        }
        attribute.setValues(values);
        attributes.put(name, attribute);
        }
    }



    /** Property setters */


    /**
     * This sets the http data source bean
     */
    public void setHttpDataSource(HttpDataSource v) {
        httpDataSource = v;
    }

    /**
     * This sets whether this connector will throw an exception if no search results are found.
     * 
     * @param b <code>boolean</code>
     */
    public void setNoResultsIsError(boolean b) {
        noResultsIsError = b;
    }


    /**
     * This sets the base URL for this connector
     *
     */
    public void setBaseUrl(String v) {
        baseUrl = v;
    }


    /**
     * Sets the authentication type
     * 
     * @param type typr 
     */
    public void setAuthenticationType(AUTHENTICATION_TYPE type) {
        authenticationType = type;
    }

    /**
     * This sets the time in milliseconds that the ldap will wait for search results. A value of 0 means to wait
     * indefinitely. This method will remove any cached results.
     * 
     * @see #clearCache()
     * 
     * @param i <code>int</code> milliseconds
     */
    public void setSearchTimeLimit(int i) {
        searchTimeLimit = i;
        clearCache();
    }

    /**
     * This sets the maximum number of search results to return. A value of 0 all entries will be returned.
     * This method will remove any cached results.
     * 
     * @see #clearCache()
     * 
     * @param l <code>long</code> maximum number of search results
     */
    public void setMaxResults(int max) {
        maxResults = max;
        clearCache();
    }

    /**
     * This sets the basic auth username
     * 
     * @param s <code>String</code> username
     */
    public void setUsername(String u) {
        username = u;
    }

    /**
     * This sets the basic auth password
     * 
     * @param s <code>String</code> password
     */
    public void setPassword(String p) {
        password = p;
    }

    /**
     * This sets the attributes to query
     */
    public void setRwsAttributes(List<RwsAttribute> list) {
       rwsAttributes = list;
    }

    /**
     * Sets the builder used to create the executable searches.
     *
     * @param builder builder used to create the executable searches
     */
    public void setQueryStringBuilder(@Nonnull final TemplatedQueryStringBuilder builder) {
        ComponentSupport.ifInitializedThrowUnmodifiabledComponentException(this);
        ComponentSupport.ifDestroyedThrowDestroyedComponentException(this);

        queryStringBuilder = Constraint.isNotNull(builder, "TemplatedQueryStringBuilder can not be null");
    }

   private void clearCache() {
   }

}
