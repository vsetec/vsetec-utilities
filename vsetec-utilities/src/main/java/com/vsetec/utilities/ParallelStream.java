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

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

/**
 *
 * @author fedd
 */
public class ParallelStream extends InputStream {

    private final Provider _provider;
    private final int _myNumber;

    public ParallelStream(InputStream parentStream) {
        if (parentStream instanceof ParallelStream) {
            _provider = ((ParallelStream) parentStream)._provider;
            _myNumber = _provider._attachAndGetYourNumber(this);
        } else {
            _provider = new Provider(parentStream);
            _myNumber = _provider._attachAndGetYourNumber(this);
            Thread thread = new Thread(_provider, "ParallelStream Provider for " + parentStream.toString());
            thread.start();
        }
    }

    @Override
    public int read() throws IOException {
        return _provider.read(_myNumber);
    }

    @Override
    public void close() throws IOException {
        _provider._detachAndClose(_myNumber);
    }

    private class Provider implements Runnable {

        private final InputStream _inputStream;
        private ParallelStream[] _readers = new ParallelStream[0];
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

        private synchronized void _detachAndClose(int whosClosing) throws IOException {
            int ret = _readers.length;

            ParallelStream[] newReaderList = new ParallelStream[ret - 1];
            System.arraycopy(_readers, 0, newReaderList, 0, whosClosing);
            System.arraycopy(_readers, whosClosing + 1, newReaderList, whosClosing, newReaderList.length);
            _readers = newReaderList;

            boolean[] newReadList = new boolean[ret - 1];
            System.arraycopy(_hasRead, 0, newReadList, 0, whosClosing);
            System.arraycopy(_hasRead, whosClosing + 1, newReadList, whosClosing, newReadList.length);
            _hasRead = newReadList;

            if (newReadList.length == 0) {
                _inputStream.close();
            }
        }

        private synchronized int _attachAndGetYourNumber(ParallelStream ms) {
            int ret = _readers.length;

            ParallelStream[] newReaderList = new ParallelStream[ret + 1];
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
            while (_hasRead[theirNumber]) {
                try {
                    wait(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Unexpected interruption", e);
                }
            }
            _hasRead[theirNumber] = true;
            notifyAll();
            return _currentChar;
        }

    }

    /**
     * A quick test. Creates two identical files 
     *
     * @param arguments
     * @throws UnsupportedEncodingException
     * @throws FileNotFoundException
     */
    public static void main(String[] arguments) throws UnsupportedEncodingException, FileNotFoundException {
        String test = "A quick brown fox jumped over a lazy dog\n";
        InputStream is = new ByteArrayInputStream(test.getBytes("UTF-8"));

        FileOutputStream fos1 = new FileOutputStream("testfile1", true);
        FileOutputStream fos2 = new FileOutputStream("testfile2", true);

        ParallelStream ms1 = new ParallelStream(is);
        ParallelStream ms2 = new ParallelStream(ms1);

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int byt;
                    while ((byt = ms1.read()) != -1) {
                        fos1.write(byt);
                    }
                    fos1.close();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }, "ParallelStream test");
        thread.start();

        try {
            int byt;
            while ((byt = ms2.read()) != -1) {
                fos2.write(byt);
            }
            fos2.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

}
