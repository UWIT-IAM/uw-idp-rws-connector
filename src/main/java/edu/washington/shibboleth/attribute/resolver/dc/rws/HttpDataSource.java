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

package edu.washington.shibboleth.attribute.resolver.dc.rws;

import java.lang.IllegalArgumentException;
import java.lang.ThreadLocal;
import java.io.File;
import java.util.List;
import java.io.FileInputStream;
import java.io.IOException;

import java.util.Date;
import java.util.Iterator;
import java.util.List;

import java.net.URL;
import java.net.MalformedURLException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Key;
import java.security.KeyStoreException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import javax.net.ssl.HostnameVerifier;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.protocol.HttpClientContext;

import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;

import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.params.BasicHttpParams;

import org.apache.http.conn.socket.ConnectionSocketFactory;

import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.util.EntityUtils;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * <code>HttpDataSource</code> provides a webservice data source.
 */
public class HttpDataSource {

    /** Class logger. */
    private static Logger log = LoggerFactory.getLogger(HttpDataSource.class);

    private SSLConnectionSocketFactory socketFactory;

    /** Username if basic auth */
    private String username = null;

    /** Password if basic auth */
    private String password = null;

    /** Cred provider if basic auth */
    private CredentialsProvider credsProvider;

    /** Time, in milliseconds, to wait for a search to return. */
    private int searchTimeLimit;

    /** Accept header. */
    private String acceptHeader = null;

    /** Connection manager */
    private PoolingHttpClientConnectionManager connectionManager;

    /** Http client **/
    private CloseableHttpClient httpClient;

    /** max connections */
    private int maxConnections = 10;

    private String caCertificateFile;
    private String certificateFile;
    private String keyFile;

    /** authn type **/
    private boolean isBasicAuthn = false;
    private boolean isCertAuthn = false;

    /** 
    /**
     * Constructor
     */
    public HttpDataSource() {
    }

    /**
     * Initializes the connector and prepares it for use.
     */
    public void initialize() {
       log.info("HttpDataSource: initialize");
       
       SSLConnectionSocketFactory sf = getSocketFactory();
       Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory> create().register("https", sf).build();

       connectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
       connectionManager.setMaxTotal(maxConnections);
       connectionManager.setDefaultMaxPerRoute(maxConnections);

       /*
        * Create our client 
        */

       // HttpClientBuilder cb = HttpClients.custom().setConnectionManager(connectionManager);
       HttpClientBuilder cb = HttpClientBuilder.create().setConnectionManager(connectionManager);
       // requires lib 4.x
       // cb = cb.setConnectionManagerShared(true);

       if (username!=null && password!=null) {
          CredentialsProvider credsProvider = new BasicCredentialsProvider();
          UsernamePasswordCredentials usernamePasswordCredentials = new UsernamePasswordCredentials(username, password);
          credsProvider.setCredentials(AuthScope.ANY, usernamePasswordCredentials);
          cb = cb.setDefaultCredentialsProvider(credsProvider);
          log.info("HttpDataSource: added basic creds ");
       }
       httpClient = cb.build(); 
    }

    /**
     * Each thread gets a context
     */
    private static final ThreadLocal<HttpClientContext> clientContext = new ThreadLocal<HttpClientContext>() {
        @Override
        protected HttpClientContext initialValue() {
            return new HttpClientContext();
        }
    };

    /**
     * Retrieve a resource
     */
    public String getResource(String url) {
       String content = null;
       log.info("rws get: " + url);
       HttpGet httpget = new HttpGet(url);
       // parameterize this ( by this request? )
       log.info(acceptHeader);
       if (acceptHeader != null) httpget.setHeader("Accept", acceptHeader);
       try {
          CloseableHttpResponse response = httpClient.execute(httpget, clientContext.get());
          try {
              int sc = response.getStatusLine().getStatusCode();
              log.info("status: " + sc);
              HttpEntity entity = response.getEntity();
              if (entity != null) {
                  content = EntityUtils.toString(entity);
                  log.trace("content dump:");
                  log.trace(content);
              }
          } finally {
              response.close();
          }
       }  catch (Exception e) {
           log.error("rws get error: " + e);
       }
       return content;
    }


    /**
     * Generate a socket factory using supplied key and trust stores 
     */
    protected SSLConnectionSocketFactory getSocketFactory() {
        KeyStore trustStore;
        KeyStore keyStore;
        TrustManager[] trustManagers = null;
        KeyManager[] keyManagers = null;
        
        try {
           /* trust managers */
           if (caCertificateFile != null) {
              log.info("setting trust in " + caCertificateFile);
              TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
              X509Certificate cert = null;
              if (caCertificateFile!=null) cert = readCertificateFile(caCertificateFile);
              log.debug("init trust mgr " + cert);
              trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
              trustStore.load(null, null);
              trustStore.setCertificateEntry("CACERT", cert);
              tmf.init(trustStore);
              trustManagers = tmf.getTrustManagers();
           } else {
              trustManagers = new TrustManager[] { new X509TrustManager() {
                 public X509Certificate[] getAcceptedIssuers() {
                     return null;
                 }

                 public void checkClientTrusted(X509Certificate[] certs, String authType) {
                     return;
                 }

                 public void checkServerTrusted(X509Certificate[] certs, String authType) {
                     return;
                 }
             }};
           }

           /* key manager */
           if (certificateFile != null && keyFile != null) {
               KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
               keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
               keyStore.load(null, null);

               X509Certificate cert = readCertificateFile(certificateFile);
               PKCS1 pkcs = new PKCS1();
               log.info("reading key file: " + keyFile);
               PrivateKey key = pkcs.readKey(keyFile);

               X509Certificate[] chain = new X509Certificate[1];
               chain[0] = cert;
               keyStore.setKeyEntry("CERT", (Key) key, "pw".toCharArray(), chain);
               kmf.init(keyStore, "pw".toCharArray());
               keyManagers = kmf.getKeyManagers();
           }

           /* socket factory */

           SSLContext ctx = SSLContext.getInstance("TLS");
           ctx.init(keyManagers, trustManagers, null);
           return new SSLConnectionSocketFactory(ctx);

        } catch (IOException e) {
              log.error("sf error: " + e);
        } catch (KeyStoreException e) {
              log.error("sf error: " + e);
        } catch (NoSuchAlgorithmException e) {
           log.error("sf error: " + e);
        } catch (KeyManagementException e) {
           log.error("sf error: " + e);
        } catch (CertificateException e) {
           log.error("sf error: " + e);
        } catch (UnrecoverableKeyException e) {
           log.error("sf error: " + e);
        }

        return null;

    }

    /* get a certificate from a PEM file */
    protected X509Certificate readCertificateFile(String filename) {
        log.info("reading cert file: " + filename);
        FileInputStream file;
        X509Certificate cert;
        try {
            file = new FileInputStream(filename);
        } catch (IOException e) {
            log.error("ldap source bad cert file: " + e);
            return null;
        }
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            cert = (X509Certificate) cf.generateCertificate(file);
        } catch (CertificateException e) {
            log.error("ldap source bad cert: " + e);
            return null;
        }
        return cert;
    }


    /** Bean property setters */

    /**
     * This sets the certificate key for x509 authn
     * 
     * @param i <code>String</code> certificate key file
     */
    public void setKeyFile(String v) {
        keyFile = v;
    }

    /**
     * This sets the certificate for x509 authn
     * 
     * @param i <code>String</code> certificate file
     */
    public void setCertificateFile(String v) {
        certificateFile = v;
    }

    /**
     * This sets the CA certificate file
     * 
     * @param i <code>String</code> CA certificate file
     */
    public void setCaCertificateFile(String v) {
        caCertificateFile = v;
    }


    /**
     * This sets the time in milliseconds that the ldap will wait for search results. A value of 0 means to wait
     * indefinitely. This method will remove any cached results.
     * 
     * @param i <code>int</code> milliseconds
     */
    public void setSearchTimeLimit(int i) {
        searchTimeLimit = i;
    }

    /**
     * This sets the maximum connections for the pool
     * 
     * @param i <code>int</code> max connections
     */
    public void setMaxConnections(int i) {
        maxConnections = i;
    }

    /**
     * This sets the accept header
     * 
     * @param s <code>String</code> acceptHeader
     */
    public void setAcceptHeader(String v) {
        acceptHeader = v;
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

    public synchronized void close() {
    }

    private void clearCache() {
    }

}
