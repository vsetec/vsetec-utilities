/*
 * Copyright 2019 fedd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.vsetec.utilities.camel;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultProducer;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.apache.directory.api.ldap.model.cursor.EntryCursor;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.ldap.client.api.DefaultLdapConnectionFactory;
import org.apache.directory.ldap.client.api.DefaultPoolableLdapConnectionFactory;
import org.apache.directory.ldap.client.api.LdapConnectionConfig;
import org.apache.directory.ldap.client.api.LdapConnectionPool;
import org.apache.directory.ldap.client.api.LdapNetworkConnection;

/**
 *
 * @author fedd
 */
public class LdapComponent extends DefaultComponent {

    private final LdapConnectionConfig _cfg;
    private final GenericObjectPool.Config _poolCfg;

    public LdapComponent(LdapConnectionConfig config, GenericObjectPool.Config poolConfig) {
        _cfg = config == null ? new LdapConnectionConfig() : config;
        _poolCfg = poolConfig;
    }

    private final HashMap<String, LdapConnectionPool> _pools = new HashMap<>(); // host:port - pool

    private synchronized LdapConnectionPool _getPool(String host, int port) {
        String hostPort = host + ":" + port;
        LdapConnectionPool ret = _pools.get(hostPort);
        if (ret == null) {
            LdapConnectionConfig config = new LdapConnectionConfig();

            // TODO: is there a more beautiful way to avoid this dumb copy
            config.setBinaryAttributeDetector(_cfg.getBinaryAttributeDetector());
            config.setEnabledCipherSuites(_cfg.getEnabledCipherSuites());
            config.setEnabledProtocols(_cfg.getEnabledProtocols());
            config.setKeyManagers(_cfg.getKeyManagers());
            config.setLdapApiService(_cfg.getLdapApiService());
            config.setSecureRandom(_cfg.getSecureRandom());
            config.setSslProtocol(_cfg.getSslProtocol());
            config.setTimeout(_cfg.getTimeout());
            config.setTrustManagers(_cfg.getTrustManagers());
            config.setUseSsl(_cfg.isUseSsl());
            config.setUseTls(_cfg.isUseTls());

            // now the endpoint specific
            config.setLdapHost(host);
            config.setLdapPort(port);

            DefaultLdapConnectionFactory factory = new DefaultLdapConnectionFactory(config);
            factory.setTimeOut(60);

            ret = new LdapConnectionPool(new DefaultPoolableLdapConnectionFactory(factory), _poolCfg);
            _pools.put(hostPort, ret);

        }
        return ret;
    }

    @Override
    protected void doStop() throws Exception {
        for (LdapConnectionPool pool : _pools.values()) {
            pool.close();
        }
        _pools.clear();
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        return new DefaultEndpoint() {

            final String _host;
            final int _port;
            final String _baseDn;
            final String _userDn;
            final String _userPassword;
            final SearchScope _scope;
            final boolean _startTls;

            {
                URI uriBack = new URI(uri);

                String host = uriBack.getHost();
                _host = host == null ? "localhost" : host;
                int port = uriBack.getPort();
                _port = port <= 0 ? 389 : port;
                String baseDn = (String) parameters.get("baseDn");
                _baseDn = baseDn == null ? "ou=system" : baseDn;
                String userInfo = uriBack.getUserInfo();
                if (userInfo != null) {
                    String[] userAndPassword = userInfo.split(":");
                    _userDn = userAndPassword[0].length() > 0 ? "cn=" + userAndPassword[0] + "," + _baseDn : null;
                    _userPassword = userAndPassword.length > 0 && userAndPassword[1].length() > 0 ? userAndPassword[1] : null;
                } else {
                    _userDn = null;
                    _userPassword = null;
                }

                String searchScope = (String) parameters.get("scope");
                if (searchScope == null) {
                    _scope = SearchScope.SUBTREE;
                } else {
                    _scope = SearchScope.valueOf(searchScope);
                }

                _startTls = _cfg.isUseTls();

            }

            final LdapConnectionPool _pool = _getPool(_host, _port);

            @Override
            public Producer createProducer() throws Exception {
                return new DefaultProducer(this) {
                    @Override
                    public void process(Exchange exchange) throws Exception {

                        LdapNetworkConnection conn = (LdapNetworkConnection) _pool.getConnection();

                        if (_startTls) {
                            conn.startTls();
                        }

                        if (_userDn != null) {
                            if (_userPassword != null) {
                                conn.bind(_userDn, _userPassword);
                            } else {
                                conn.bind(_userDn);
                            }
                        } else {
                            conn.bind();
                        }

                        String searchString = (String) parameters.get("search");
                        if (searchString == null) {
                            exchange.getIn().setBody(conn.isAuthenticated());
                        } else {
                            if (conn.isAuthenticated()) {
                                EntryCursor cursor = conn.search(_baseDn, searchString, _scope);
                                ArrayList<Entry> searchResult = new ArrayList<>();
                                for (Entry entry : cursor) {
                                    searchResult.add(entry); // TODO: consider creating a detachable cursor that will close when not needed and unbind the connection
                                }
                                cursor.close();
                                exchange.getIn().setBody(searchResult);
                            } else {
                                exchange.getIn().setBody(Boolean.FALSE);
                            }
                        }

                        conn.unBind();

                    }
                };
            }

            @Override
            public Consumer createConsumer(Processor processor) throws Exception {
                throw new UnsupportedOperationException("Not supported.");
            }

            @Override
            public boolean isSingleton() {
                return true;
            }
        };

    }

}
