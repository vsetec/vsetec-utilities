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
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 *
 * @author fedd
 */
public class MultiStream extends InputStream {

    private final Provider _provider;
    private final int _myNumber;

    public MultiStream(InputStream parentStream) {
        if (parentStream instanceof MultiStream) {
            _provider = ((MultiStream) parentStream)._provider;
            _myNumber = _provider._attachAndGetYourNumber(this);
        } else {
            _provider = new Provider(parentStream);
            _myNumber = _provider._attachAndGetYourNumber(this);
            Thread thread = new Thread(_provider, "MultiStream Provider for " + parentStream.toString());
            thread.start();
        }
    }

    @Override
    public int read() throws IOException {
        return _provider.read(_myNumber);
    }

    private class Provider implements Runnable {

        private final InputStream _inputStream;
        private MultiStream[] _readers = new MultiStream[0];
        private boolean[] _hasRead = new boolean[0];
        private int _currentChar = -2;

        private Provider(InputStream inputStream) {
            _inputStream = inputStream;
        }

        @Override
        public void run() {
            try {
                while ((_currentChar = _inputStream.read()) != -1) {
                    _waitForAllToRead(true);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private synchronized void _waitForAllToRead(boolean markUnread) {
            loop:
            while (true) {
                for (boolean hasRead : _hasRead) {
                    if (!hasRead) {
                        try {
                            wait(1000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException("Inexpected interruption", e);
                        }
                        continue loop;
                    }
                }

                if (markUnread) {
                    for (int i = 0; i < _hasRead.length; i++) {
                        _hasRead[i] = false;
                    }
                }

                break;
            }
            notifyAll();
        }

        private synchronized int _attachAndGetYourNumber(MultiStream ms) {
            int ret = _readers.length;

            MultiStream[] newReaderList = new MultiStream[ret + 1];
            System.arraycopy(_readers, 0, newReaderList, 0, ret);
            newReaderList[ret] = ms;
            _readers = newReaderList;

            boolean[] newReadList = new boolean[ret + 1];
            System.arraycopy(_hasRead, 0, newReadList, 0, ret);
            newReadList[ret] = false;
            _hasRead = newReadList;

            notifyAll();
            return ret;
        }

        private synchronized int read(int theirNumber) throws IOException {
            while (true) {
                if (!_hasRead[theirNumber]) {
                    try {
                        wait(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException("Unexpected interruption", e);
                    }
                    continue;
                }
                _hasRead[theirNumber] = true;
                notifyAll();
                return _currentChar;
            }

        }

    }

}
