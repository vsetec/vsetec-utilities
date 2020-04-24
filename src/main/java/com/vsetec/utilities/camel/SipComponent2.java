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

import gov.nist.javax.sip.clientauthutils.AuthenticationHelper;
import gov.nist.javax.sip.clientauthutils.AuthenticationHelperImpl;
import gov.nist.javax.sip.header.CallID;
import java.text.ParseException;
import java.util.Map;
import javax.sip.PeerUnavailableException;
import javax.sip.SipFactory;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.AuthorizationHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.Header;
import javax.sip.header.HeaderFactory;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.ComponentConfiguration;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointConfiguration;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.impl.DefaultComponentConfiguration;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultEndpointConfiguration;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultProducer;

/**
 *
 * @author fedd
 */
public class SipComponent2 implements Component {

    private CamelContext _camelContext;
    private final SipFactory _sipFactory;
    private final HeaderFactory _headerFactory;
    private final AddressFactory _addressFactory;
    private final MessageFactory _messageFactory;

    public SipComponent2(String implementationString) {

        _sipFactory = SipFactory.getInstance();
        _sipFactory.setPathName(implementationString);
        try {
            _headerFactory = _sipFactory.createHeaderFactory();
            _addressFactory = _sipFactory.createAddressFactory();
            _messageFactory = _sipFactory.createMessageFactory();
        } catch (PeerUnavailableException e) {
            throw new RuntimeException(e);
        }

    }

    public AddressFactory getAddressFactory() {
        return _addressFactory;
    }

    public HeaderFactory getHeaderFactory() {
        return _headerFactory;
    }

    public MessageFactory getMessageFactory() {
        return _messageFactory;
    }

    @Override
    public boolean useRawUri() {
        return true;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        _camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return _camelContext;
    }

    @Override
    public Endpoint createEndpoint(String givenUri) throws Exception {
        // strip off scheme
        String wrappedUri = givenUri.substring(givenUri.indexOf(':') + 1);
        final Endpoint wrappedEndpoint = getCamelContext().getEndpoint(wrappedUri);

        Endpoint camelEndpoint = new DefaultEndpoint(givenUri, this) {

            final private Endpoint _thisEndpoint = this;

            @Override
            public Producer createProducer() throws Exception {

                Producer wrappedProducer = wrappedEndpoint.createProducer();

                return new Producer() {
                    @Override
                    public void process(Exchange exchange) throws Exception {

                        Message in = exchange.getIn();
                        Object sipMessage = in.getBody();
                        String msg = sipMessage.toString();
                        in.setBody(msg, String.class);

                        System.out.println("SIP**********Sending: \n" + msg);

                        wrappedProducer.process(exchange);

                        System.out.println("SIP**********Sent");
                        
                        in.setBody(sipMessage, javax.sip.message.Message.class);

                    }

                    @Override
                    public Exchange createExchange() {
                        return wrappedProducer.createExchange();
                    }

                    @Override
                    public Exchange createExchange(ExchangePattern pattern) {
                        return wrappedProducer.createExchange(pattern);
                    }

                    @Override
                    public Exchange createExchange(Exchange exchange) {
                        return wrappedProducer.createExchange(exchange);
                    }

                    @Override
                    public void start() throws Exception {
                        wrappedProducer.start();
                    }

                    @Override
                    public void stop() throws Exception {
                        wrappedProducer.stop();
                    }

                    @Override
                    public boolean isSingleton() {
                        return wrappedProducer.isSingleton();
                    }

                    @Override
                    public Endpoint getEndpoint() {
                        return _thisEndpoint;
                    }
                };
            }

            @Override
            public Consumer createConsumer(Processor processor) throws Exception {

                Processor wrappingProcessor = new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {

                        Message in = exchange.getIn();
                        String string = in.getBody(String.class);

                        System.out.println("SIP**********Receiving: \n" + string);

                        if (string.startsWith("SIP")) { // is a response
                            in.setBody(_messageFactory.createResponse(string));
                            in.setHeader("DelaSipIsResponse", true);
                        } else {
                            in.setBody(_messageFactory.createRequest(string));
                            in.setHeader("DelaSipIsResponse", false);
                        }

                        processor.process(exchange);

                    }
                };

                Consumer wrappedConsumer = wrappedEndpoint.createConsumer(wrappingProcessor);
                return wrappedConsumer;

            }

            @Override
            public boolean isSingleton() {
                return wrappedEndpoint.isSingleton();
            }

            @Override
            public void start() throws Exception {
                wrappedEndpoint.start();
                super.start();
            }

            @Override
            public void stop() throws Exception {
                wrappedEndpoint.stop();
                super.stop();
            }

        };

        return camelEndpoint;

    }

    @Override
    public EndpointConfiguration createConfiguration(String uri) throws Exception {
        return new DefaultEndpointConfiguration(getCamelContext(), uri) {
            @Override
            public String toUriString(EndpointConfiguration.UriFormat format) {
                return uri;
            }
        };
    }

    @Override
    public ComponentConfiguration createComponentConfiguration() {
        return new DefaultComponentConfiguration(this);
    }

}
