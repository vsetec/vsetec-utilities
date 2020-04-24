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
package com.vsetec.utilities;

import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 *
 * @author fedd
 */
public class ReaderTokenizer {

    public Iterator<String> tokenize(final Reader reader) throws IOException {
        return new TokenIterator(reader, "\n");
    }

    public Iterator<String> tokenize(final Reader reader, String delimiter) throws IOException {
        return new TokenIterator(reader, delimiter);
    }

    public Iterator<String> tokenize(final Reader reader, int headerTokenNumber) throws IOException {
        return tokenize(reader, "\n", headerTokenNumber);
    }

    public Iterator<String> tokenize(final Reader reader, String delimiter, int headerTokenNumber) throws IOException {

        Iterator<String> ret = new TokenIterator(reader, delimiter) {

            private final String _firstLines;

            {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < headerTokenNumber; i++) {
                    if (super.hasNext()) {
                        sb.append(super.next());
                    }
                }
                _firstLines = sb.toString();
            }

            @Override
            public String next() {
                return _firstLines + super.next();
            }

        };

        return ret;

    }

    public static class TokenIterator implements Iterator<String> {

        private String _curString = null;
        private boolean _endReached = false;
        private final Reader _reader;
        private char[] _token;

        public TokenIterator(Reader reader, String delimiter) {
            setToken(delimiter);
            _reader = reader;
        }

        public final void setToken(String token) {
            _token = token.toCharArray();
            if (_token.length == 0) {
                throw new IllegalArgumentException("Can't tokenize with the empty string");
            }
        }

        private void _readNextToken() throws IOException {

            int curCharInt;
            char previousChar = (char) -1;
            int tokenPos = 0;
            StringBuilder sb = new StringBuilder(255);

            while (true) {
                curCharInt = _reader.read();
                if (curCharInt == -1) {
                    _endReached = true;
                    _reader.close();
                    break;
                }
                if (curCharInt == _token[tokenPos]) {

                    if (tokenPos != 0 || !Character.isHighSurrogate(previousChar)) {
                        tokenPos++;

                        if (tokenPos >= _token.length) {
                            tokenPos = 0;
                            previousChar = (char) curCharInt;
                            sb.append(previousChar);
                            break;
                        }
                    }
                }

                previousChar = (char) curCharInt;
                sb.append(previousChar);
            }
            _curString = sb.toString();
        }

        @Override
        public boolean hasNext() {
            if (_curString == null) {
                if (_endReached) {
                    return false;
                }
                try {
                    _readNextToken();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }

                if (_curString != null) {
                    return true;
                }

                if (_endReached) {
                    return false;
                }

                throw new RuntimeException("Someting wrong");

            } else {
                return true;
            }
        }

        @Override
        public String next() {
            if (_curString != null) {
                String ret = _curString;
                _curString = null;
                return ret;
            }
            if (_endReached) {
                throw new NoSuchElementException();
            }

            try {
                _readNextToken();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }

            if (_curString != null) {
                String ret = _curString;
                _curString = null;
                return ret;
            }

            throw new RuntimeException("Someting wrong");
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Not supported.");
        }

    }

}
