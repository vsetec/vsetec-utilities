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

import java.io.IOException;
import java.io.Reader;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TooManyListenersException;
import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.InvalidArgumentException;
import javax.sip.ListeningPoint;
import javax.sip.ObjectInUseException;
import javax.sip.PeerUnavailableException;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipFactory;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.TimeoutEvent;
import javax.sip.Transaction;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.TransportNotSupportedException;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.HeaderFactory;
import javax.sip.header.RecordRouteHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Message;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.TypeConversionException;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultMessage;
import org.apache.camel.impl.DefaultProducer;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author fedd
 */
public class SipComponent extends DefaultComponent {

    private final SipFactory _sipFactory = SipFactory.getInstance();
    private final SipStack _sipStack;
    private final Map<String, SipProvider> _sipProvidersByPortAndTransport = new HashMap<>(3);
    private final String _ourHost;
    private final HeaderFactory _headerFactory;
    private final AddressFactory _addressFactory;
    private final MessageFactory _messageFactory;
    private final Object _security;

    public SipComponent(CamelContext camelContext, String hostIp, String implementationPackage, Map<String, Object> stackParameters, Object security) { // TODO: implement sips - change "security to the appropriate type

        super(camelContext);

        _ourHost = hostIp;
        _security = security;
        _sipFactory.setPathName(implementationPackage);

        try {
            _headerFactory = _sipFactory.createHeaderFactory();
            _addressFactory = _sipFactory.createAddressFactory();
            _messageFactory = _sipFactory.createMessageFactory();

        } catch (PeerUnavailableException e) {
            throw new RuntimeException(e);
        }

        Properties properties = new Properties();
        properties.setProperty("javax.sip.STACK_NAME", "delaSipStack");
        properties.putAll(stackParameters);
        properties.remove("javax.sip.IP_ADDRESS");
        try {
            _sipStack = _sipFactory.createSipStack(properties);
        } catch (PeerUnavailableException e) {
            throw new RuntimeException(e);
        }

    }

    public HeaderFactory getHeaderFactory() {
        return _headerFactory;
    }

    public AddressFactory getAddressFactory() {
        return _addressFactory;
    }

    public MessageFactory getMessageFactory() {
        return _messageFactory;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> endpointParams) throws Exception {

        return new CamelSipEndpoint(uri, this, remaining, endpointParams);

    }

    private class CamelSipEndpoint extends DefaultEndpoint {

        // sip:ws:0.0.0.0:9393  -- source
        // sip:udp:0.0.0.0:5060   -- source
        // sip:udp:10.10.2.3:5060   -- destination. this is a remote host
        // sip:  --- empty sender-receiver
        private final Map<String, Object> _endpointParams;
        private final String _receivingHost;
        private final String _receivingTransport;
        private final Integer _receivingPort;
        private final CamelSipListener _listener = new CamelSipListener(this);

        private CamelSipEndpoint(String uri, Component component, String remaining, Map<String, Object> endpointParams) {
            super(uri, component);
            _endpointParams = endpointParams;
            if (remaining == null || remaining.trim().length() == 0) {
                _receivingHost = null;
                _receivingTransport = null;
                _receivingPort = null;
            } else {
                try {
                    java.net.URI endpointAddress = new java.net.URI(remaining);
                    _receivingHost = endpointAddress.getHost();
                    _receivingTransport = endpointAddress.getScheme();
                    int destinationPortTmp = endpointAddress.getPort();
                    if (destinationPortTmp > 0) {
                        _receivingPort = destinationPortTmp;
                    } else {
                        _receivingPort = null;
                    }
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        private synchronized SipProvider _getSipProvider(int port, String transport) {
            String key = transport + ":" + port;
            SipProvider ret = _sipProvidersByPortAndTransport.get(key);
            if (ret == null) {
                try {
                    ListeningPoint lp = _sipStack.createListeningPoint(_ourHost, port, transport);
                    ret = _sipStack.createSipProvider(lp);
                    _sipProvidersByPortAndTransport.put(key, ret);
                } catch (InvalidArgumentException | ObjectInUseException | TransportNotSupportedException e) {
                    throw new RuntimeException(e);
                }
                
                try {
                    ret.addSipListener(_listener);
                } catch (TooManyListenersException e) {
                    throw new RuntimeException(e);
                }
            }
            return ret;
        }

        @Override
        public Producer createProducer() throws Exception {

            return new DefaultProducer(this) {

                @Override
                public void process(Exchange exchange) throws Exception {

                    Object toSend = exchange.getIn().getBody();
                    if (!(toSend instanceof CamelSipMessage)) {
                        //TODO: convert to sipmessage somehow and send
                        return;
                    }

                    // sending
                    CamelSipMessage message = (CamelSipMessage) toSend;

                    // we've got message prepared for us, need to send something somewhere
                    if (message.isRequest()) {

                        Request request = message.getMessage();
                        ServerTransaction serverTransaction = message.getTransaction();

                        // request. what to do with it?
                        if (_receivingHost != null) { // we have a destination like udp:10.23.2.2

                            // let's forward our request there
                            Request newRequest = (Request) request.clone();

                            // receiving = destination
                            // where? add a route there
                            SipURI destinationUri = _addressFactory.createSipURI(null, _receivingHost);
                            if (_receivingPort != null) {
                                destinationUri.setPort(_receivingPort);
                            }
                            destinationUri.setLrParam();
                            destinationUri.setTransportParam(_receivingTransport);
                            Address destinationAddress = _addressFactory.createAddress(null, destinationUri);
                            RouteHeader routeHeader = _headerFactory.createRouteHeader(destinationAddress);
                            newRequest.addFirst(routeHeader);

                            // where from? add a via and a record-route
                            SipProvider receivingProvider = message.getProvider();
                            ListeningPoint listeningPoint = receivingProvider.getListeningPoint(_receivingTransport);
                            int responseListeningPort = listeningPoint.getPort();

                            ViaHeader viaHeader = _headerFactory.createViaHeader(_ourHost, responseListeningPort, _receivingTransport, null);
                            newRequest.addFirst(viaHeader);

                            SipURI recordRouteUri = _addressFactory.createSipURI(null, _ourHost);
                            Address recordRouteAddress = _addressFactory.createAddress(null, recordRouteUri);
                            recordRouteUri.setPort(responseListeningPort);
                            recordRouteUri.setLrParam();
                            RecordRouteHeader recordRoute = _headerFactory.createRecordRouteHeader(recordRouteAddress);
                            newRequest.addHeader(recordRoute);

                            // will use the transport specified in route header to send
                            ClientTransaction clientTransaction = receivingProvider.getNewClientTransaction(newRequest);

                            // remember the server transaction
                            clientTransaction.setApplicationData(serverTransaction);

                            clientTransaction.sendRequest();
                            

                        } else { // we don't have any destination
                            // let's respond
                            Response response = _messageFactory.createResponse((int) _endpointParams.getOrDefault("statusCode", 200), request);
                            serverTransaction.sendResponse(response);
                        }
                    } else {

                        Response response = message.getMessage();
                        Response newResponse = (Response) response.clone();
                        ClientTransaction clientTransaction = message.getTransaction();

                        if (_receivingHost != null) { // we have a destination like ws:10.75.2.2
                            // let's forward our response there
                            // TODO: arbitrary response forwarding
                            return;

                        } else {
                            // we have to forward the response to the request's initial author

                            // as it is a response originated here, we can get a server transaction
                            newResponse.removeFirst(ViaHeader.NAME);
                            if (clientTransaction == null) {
                                // send to the via address
                                SipProvider sender = message.getProvider();
                                sender.sendResponse(newResponse);
                            } else {
                                ServerTransaction serverTransaction = (ServerTransaction) clientTransaction.getApplicationData();
                                serverTransaction.sendResponse(newResponse);
                            }
                        }
                    }
                }
            };
        }

        @Override
        public Consumer createConsumer(Processor processor) throws Exception {

            final Endpoint endpoint = this;

            if (_receivingTransport != null && _receivingPort != null && _receivingPort > 0) {
                _getSipProvider(_receivingPort, _receivingTransport);
            }

            Consumer consumer = new Consumer() {

                @Override
                public void start() throws Exception {
                    _listener._processors.add(processor);
                }

                @Override
                public void stop() throws Exception {
                    _listener._processors.remove(processor);
                }

                @Override
                public Endpoint getEndpoint() {
                    return endpoint;
                }
            };

            return consumer;
        }

        @Override
        public boolean isSingleton() {
            return true;
        }
    }

    private class CamelSipMessage extends DefaultMessage {

        private final EventObject _event;
        private final SipProvider _provider;
        private final Dialog _dialog;
        private final Transaction _transaction;
        private final Message _message;
        private final boolean _isRequest;

        private CamelSipMessage(CamelContext camelContext, RequestEvent requestEvent) {
            super(camelContext);
            _dialog = requestEvent.getDialog();
            _transaction = requestEvent.getServerTransaction();
            _event = requestEvent;
            _message = requestEvent.getRequest();
            _isRequest = true;
            _provider = (SipProvider) requestEvent.getSource();
            setBody(_message);
        }

        private CamelSipMessage(CamelContext camelContext, ResponseEvent responseEvent) {
            super(camelContext);
            _dialog = responseEvent.getDialog();
            _transaction = responseEvent.getClientTransaction();
            _event = responseEvent;
            _message = responseEvent.getResponse();
            _isRequest = false;
            _provider = (SipProvider) responseEvent.getSource();
            setBody(_message);
        }

        public boolean isRequest() {
            return _isRequest;
        }

        public SipProvider getProvider() {
            return _provider;
        }

        public <T extends Message> T getMessage() {
            return (T) _message;
        }

        @Override
        public final void setBody(Object body) {
            super.setBody(body); //To change body of generated methods, choose Tools | Templates.
        }

        public Dialog getDialog() {
            return _dialog;
        }

        public <T extends Transaction> T getTransaction() {
            return (T) _transaction;
        }

        public <T extends EventObject> T getEvent() {
            return (T) _event;
        }

    }

    private class CamelSipListener implements SipListener {

        private final List<Processor> _processors = new ArrayList<>();
        private final CamelSipEndpoint _endpoint;

        public CamelSipListener(CamelSipEndpoint endpoint) {
            _endpoint = endpoint;
        }

        private void _processAny(EventObject event) {
            // originate an exchange
            Exchange exchange = new DefaultExchange(_endpoint);
            CamelSipMessage in;
            if (event instanceof RequestEvent) {
                RequestEvent requestEvent = (RequestEvent) event;
                in = new CamelSipMessage(_endpoint.getCamelContext(), requestEvent);
                requestEvent.getServerTransaction().setApplicationData(new Object[]{_endpoint._receivingPort, _endpoint._receivingTransport});
            } else {
                in = new CamelSipMessage(_endpoint.getCamelContext(), (ResponseEvent) event);
            }
            exchange.setIn(in);

            for (Processor processor : _processors) {
                try {
                    processor.process(exchange);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        public void processRequest(RequestEvent requestEvent) {
            _processAny(requestEvent);
        }

        @Override
        public void processResponse(ResponseEvent responseEvent) {
            _processAny(responseEvent);
        }

        @Override
        public void processTimeout(TimeoutEvent timeoutEvent) {

        }

        @Override
        public void processIOException(IOExceptionEvent exceptionEvent) {

        }

        @Override
        public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {

        }

        @Override
        public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {

        }

    }
}
