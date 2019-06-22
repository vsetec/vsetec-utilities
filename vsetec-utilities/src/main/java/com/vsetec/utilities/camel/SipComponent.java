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
import java.net.URI;
import java.text.ParseException;
import java.util.EventObject;
import java.util.Map;
import java.util.Properties;
import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.ListeningPoint;
import javax.sip.PeerUnavailableException;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.SipFactory;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.TimeoutEvent;
import javax.sip.Transaction;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.AddressFactory;
import javax.sip.header.HeaderFactory;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
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
    private final HeaderFactory _headerFactory;
    private final AddressFactory _addressFactory;
    private final MessageFactory _messageFactory;
    private final RequestParser _requestParser;
    private final Object _security;
    private final String _host;

    public SipComponent(String implementationPackage, String host, Object security, CamelContext camelContext) { // TODO: implement sips - change "security to the appropriate type

        super(camelContext);
        
        _host = host;
        _security = security;
        _sipFactory.setPathName(implementationPackage);

        try {
            _headerFactory = _sipFactory.createHeaderFactory();
            _addressFactory = _sipFactory.createAddressFactory();
            _messageFactory = _sipFactory.createMessageFactory();

        } catch (PeerUnavailableException e) {
            throw new RuntimeException(e);
        }

        _requestParser = new RequestParser();

        camelContext.getTypeConverterRegistry().addTypeConverter(Request.class, CharSequence.class, _requestParser);
        camelContext.getTypeConverterRegistry().addTypeConverter(Request.class, Reader.class, _requestParser);
    }

    public RequestParser getRequestParser() {
        return _requestParser;
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
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // sip:ws:1.2.3.4:2929?javax.sip.STACK_NAME=wsphone  -- can't get "path" from websocket uri
        // sip:udp:5.6.7.8:5060?javax.sip.STACK_NAME=udpserver
        // sips:...................

        final SipStack sipStack;
        final String transport;
        final String ip;
        final int port;
        {
            Properties properties = new Properties();
            properties.setProperty("javax.sip.STACK_NAME", uri);
            properties.putAll(parameters);
            properties.remove("javax.sip.IP_ADDRESS");
            sipStack = _sipFactory.createSipStack(properties);

            URI sipUri = new URI(remaining);
            transport = sipUri.getScheme();
            ip = sipUri.getHost();
            port = sipUri.getPort();
        }

        Endpoint camelEndpoint = new DefaultEndpoint(uri, this) {


            @Override
            public Producer createProducer() throws Exception {

                final Endpoint endpoint = this;

                return new DefaultProducer(this) {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        Message in = exchange.getIn();
                        Request request = in.getBody(Request.class);
                        
                        //sipStack.
                        
                        //request.setRequestURI(requestURI);
                        
                        ClientTransaction transaction = _sipProvider.getNewClientTransaction(request);
                        transaction.sendRequest();
                        exchange.setPattern(ExchangePattern.InOut);
                        Message out = new DefaultMessage(endpoint.getCamelContext());
                        out.setBody(transaction.getDialog());
                        exchange.setOut(out);
                    }
                };
            }

            @Override
            public Consumer createConsumer(Processor processor) throws Exception {

                final Endpoint endpoint = this;
                final ListeningPoint _listeningPoint = sipStack.createListeningPoint(ip, port, transport);
                final SipProvider _sipProvider = sipStack.createSipProvider(_listeningPoint);

                final SipListener consumerListener = new DefaultSipListener() {
                    @Override
                    public void processRequest(RequestEvent requestEvent) {
                        Exchange exchange = new DefaultExchange(endpoint);
                        
                        SipMessage in = new SipMessage(endpoint.getCamelContext(), requestEvent);
                        exchange.setIn(in);

                        try {
                            processor.process(exchange);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                };

                Consumer consumer = new Consumer() {

                    @Override
                    public void start() throws Exception {
                        _sipProvider.addSipListener(consumerListener);
                    }

                    @Override
                    public void stop() throws Exception {
                        _sipProvider.removeSipListener(consumerListener);
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

        };

        return camelEndpoint;
    }

    public class RequestParser implements TypeConverter {

        @Override
        public boolean allowNull() {
            return false;
        }

        @Override
        public <T> T convertTo(Class<T> type, Object value) throws TypeConversionException {
            try {
                return mandatoryConvertTo(type, value);
            } catch (NoTypeConversionAvailableException e) {
                return null;
            }
        }

        @Override
        public <T> T convertTo(Class<T> type, Exchange exchange, Object value) throws TypeConversionException {
            return convertTo(type, value);
        }

        @Override
        public <T> T mandatoryConvertTo(Class<T> type, Object value) throws TypeConversionException, NoTypeConversionAvailableException {

            if (!type.isAssignableFrom(Request.class)) {
                throw new NoTypeConversionAvailableException(value, type);
            }

            final String string;

            if (value instanceof String) {
                string = (String) value;
            } else if (value instanceof Reader) {
                Reader reader = (Reader) value;
                try {
                    string = IOUtils.toString(reader);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                throw new NoTypeConversionAvailableException(value, type);
            }

            try {
                return (T) _messageFactory.createRequest(string);
            } catch (ParseException e) {
                throw new TypeConversionException(value, type, e);
            }
        }

        @Override
        public <T> T mandatoryConvertTo(Class<T> type, Exchange exchange, Object value) throws TypeConversionException, NoTypeConversionAvailableException {
            return mandatoryConvertTo(type, value);
        }

        @Override
        public <T> T tryConvertTo(Class<T> type, Object value) {
            try {
                return mandatoryConvertTo(type, value);
            } catch (NoTypeConversionAvailableException | TypeConversionException e) {
                return null;
            }
        }

        @Override
        public <T> T tryConvertTo(Class<T> type, Exchange exchange, Object value) {
            return tryConvertTo(type, value);
        }

    }
    
    public class SipMessage extends DefaultMessage {
        
        private final EventObject _event;
        private final Dialog _dialog;
        private final Transaction _transaction;

        private SipMessage(CamelContext camelContext, RequestEvent requestEvent) {
            super(camelContext);
            _dialog = requestEvent.getDialog();
            _transaction = requestEvent.getServerTransaction();
            _event = requestEvent;
            setBody(requestEvent.getRequest());
        }

        private SipMessage(CamelContext camelContext, ResponseEvent responseEvent) {
            super(camelContext);
            _dialog = responseEvent.getDialog();
            _transaction = responseEvent.getClientTransaction();
            _event = responseEvent;
            setBody(responseEvent.getResponse());
        }

        

        public Dialog getDialog() {
            return _dialog;
        }

        public Transaction getTransaction() {
            return _transaction;
        }

        public EventObject getEvent() {
            return _event;
        }
        
    }

    private class DefaultSipListener implements SipListener {

        @Override
        public void processRequest(RequestEvent requestEvent) {

        }

        @Override
        public void processResponse(ResponseEvent responseEvent) {

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
