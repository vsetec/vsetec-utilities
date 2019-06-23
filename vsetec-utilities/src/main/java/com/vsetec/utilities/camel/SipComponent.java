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

import com.vsetec.sip.MessageReceived;
import com.vsetec.sip.Received;
import com.vsetec.sip.Sendable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.TypeConversionException;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author fedd
 */
public class SipComponent extends DefaultComponent {

    private final SipRequestParser _requstParser;

    public SipComponent(CamelContext camelContext) {

        super(camelContext);

        _requstParser = new SipRequestParser();

    }

    @Override
    protected void doStart() throws Exception {
        TypeConverterRegistry tcr = getCamelContext().getTypeConverterRegistry();

        tcr.addTypeConverter(Received.class, String.class, _requstParser);
        tcr.addTypeConverter(Received.class, Reader.class, _requstParser);
        tcr.addTypeConverter(Received.class, InputStream.class, _requstParser);

        tcr.addTypeConverter(String.class, Sendable.class, _requstParser);
        tcr.addTypeConverter(Reader.class, Sendable.class, _requstParser);
        tcr.addTypeConverter(InputStream.class, Sendable.class, _requstParser);

        super.doStart(); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        final Endpoint wrappedEndpoint;
        {
            int myComponentPrefixEnd = uri.indexOf(remaining);
            String wrappedUri = uri.substring(myComponentPrefixEnd);
            wrappedEndpoint = getCamelContext().getEndpoint(wrappedUri);
        }

        Endpoint camelEndpoint = new DefaultEndpoint(uri, this) {

            @Override
            public Producer createProducer() throws Exception {

                Producer wrappedProducer = wrappedEndpoint.createProducer();

                return new DefaultProducer(this) {
                    @Override
                    public void process(Exchange exchange) throws Exception {

                        wrappedProducer.process(exchange);

                    }
                };
            }

            @Override
            public Consumer createConsumer(Processor processor) throws Exception {

                Processor wrappingProcessor = new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {

                        processor.process(exchange);
                        Message in = exchange.getIn();
                        InputStream stream = in.getBody(InputStream.class);
                        MessageReceived msg = MessageReceived.parse(stream);
                        in.setBody(msg);

                    }
                };

                Consumer wrappedConsumer = wrappedEndpoint.createConsumer(wrappingProcessor);

                return wrappedConsumer;

            }

            @Override
            public boolean isSingleton() {
                return true;
            }

        };

        return camelEndpoint;
    }

    public class SipRequestParser implements TypeConverter {

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

            if (!type.isAssignableFrom(Received.class)) {

                if (value instanceof Sendable) {

                    Sendable msg = (Sendable) value;

                    InputStream stream = msg.getAsStream();

                    if (type.isAssignableFrom(InputStream.class)) {
                        return (T) stream;
                    } else if (type.isAssignableFrom(Reader.class)) {
                        return (T) new InputStreamReader(stream);
                    } else if (type.isAssignableFrom(String.class)) {
                        try {
                            return (T) IOUtils.toString(stream, StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            throw new TypeConversionException(value, type, e);
                        }
                    }

                    throw new NoTypeConversionAvailableException(value, type);

                } else {
                    throw new NoTypeConversionAvailableException(value, type);
                }
            } else {

                final InputStream source;

                if (value instanceof String) {
                    source = new ByteArrayInputStream(((String) value).getBytes(StandardCharsets.UTF_8));
                } else if (value instanceof InputStream) {
                    Reader reader = (Reader) value;
                    try {
                        source = new ByteArrayInputStream(IOUtils.toString(reader).getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        throw new TypeConversionException(value, type, e);
                    }
                } else {
                    source = (InputStream) value;
                }

                try {
                    return (T) MessageReceived.parse(source);
                } catch (IOException e) {
                    throw new TypeConversionException(value, type, e);
                }
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
}
