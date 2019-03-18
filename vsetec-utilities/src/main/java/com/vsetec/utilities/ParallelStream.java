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

import java.io.BufferedInputStream;
import java.io.FileInputStream;
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
    private int _myNumber;
    private boolean _askedForNext = false;
    private boolean _isClosed = false;
    private int _nextChar = -2;

    public ParallelStream(InputStream parentStream) {
        if (parentStream instanceof ParallelStream) {
            _provider = ((ParallelStream) parentStream)._provider;
            synchronized (_provider) {
                _provider._attachAndGetYourNumber(this);
            }
        } else {
            _provider = new Provider(parentStream);
            _provider._attachAndGetYourNumber(this);
        }
    }

    @Override
    public int read() throws IOException {
        synchronized (_provider) {
            _askedForNext = true;
            if (!_provider._alreadyCalled) {
                _provider._loadNext();
            }
            _provider.notifyAll();
            while (_askedForNext) {
                try {
                    _provider.wait(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Unexpected interruption", e);
                }
            }
            return _nextChar;
        }
    }

    @Override
    public void close() throws IOException {
        if (!_isClosed) {
            _provider._detachAndClose(this);
            _isClosed = true;
        }
    }

    public int getSequenceNumber() {
        return _myNumber;
    }

    private class Provider {

        private final InputStream _inputStream;
        private ParallelStream[] _readers = new ParallelStream[0];
        private boolean _alreadyCalled = false;

        private Provider(InputStream inputStream) {
            _inputStream = inputStream;
        }

        private void _loadNext() throws IOException {
            _alreadyCalled = true;

            int nextChar = _inputStream.read();

            for (ParallelStream sibling : _readers) {
                while (!sibling._askedForNext) {
                    try {
                        wait(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException("Unexpected interruption", e);
                    }
                }
                sibling._askedForNext = false;
                sibling._nextChar = nextChar;
            }
            // all asked

            _alreadyCalled = false;
        }

        private synchronized void _detachAndClose(ParallelStream whosClosing) throws IOException {
            int ret = _readers.length;
            int nextGood = whosClosing._myNumber + 1;

            ParallelStream[] newReaderList = new ParallelStream[ret - 1];
            System.arraycopy(_readers, 0, newReaderList, 0, whosClosing._myNumber);
            if (nextGood < _readers.length) {
                System.arraycopy(_readers, nextGood, newReaderList, whosClosing._myNumber, newReaderList.length - whosClosing._myNumber);
            }
            _readers = newReaderList;

            if (newReaderList.length == 0) {
                _inputStream.close();
            } else {
                for (int i = 0; i < newReaderList.length; i++) {
                    newReaderList[i]._myNumber = i;
                }
            }
            notifyAll();
        }

        private synchronized void _attachAndGetYourNumber(ParallelStream ms) {
            int ret = _readers.length;

            ParallelStream[] newReaderList = new ParallelStream[ret + 1];
            System.arraycopy(_readers, 0, newReaderList, 0, ret);
            newReaderList[ret] = ms;
            _readers = newReaderList;

            ms._myNumber = ret;
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
        InputStream is = new BufferedInputStream(new FileInputStream("/home/fedd/Videos/stgal.mp4"));
        //InputStream is = new ByteArrayInputStream(test.getBytes("UTF-8"));

        FileOutputStream fos1 = new FileOutputStream("testfile1.mp4", true);
        FileOutputStream fos2 = new FileOutputStream("testfile2.mp4", true);
        FileOutputStream fos3 = new FileOutputStream("testfile3.mp4", true);

        ParallelStream ms1 = new ParallelStream(is);
        ParallelStream ms2 = new ParallelStream(ms1);
        ParallelStream ms3 = new ParallelStream(ms2);

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int byt;
                    while ((byt = ms1.read()) != -1) {
                        fos1.write(byt);
                    }
                    fos1.close();
                    ms1.close();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }, "ParallelStream test");
        thread.start();

        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int byt;
                    while ((byt = ms3.read()) != -1) {
                        fos3.write(byt);
                    }
                    fos3.close();
                    ms3.close();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }, "ParallelStream test 3");
        thread.start();

        try {
            int byt;
            while ((byt = ms2.read()) != -1) {
                fos2.write(byt);
            }
            fos2.close();
            ms2.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

}
