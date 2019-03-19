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
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

/**
 *
 * @author fedd
 */
public class ParallelStream extends InputStream {

    //TODO: probably rewrite with a dedicated provider thread
    private final Provider _provider;
    private int _myNumber;
    private boolean _askedForNext = false;
    private boolean _isClosed = false;
    private final byte[] _buffer = new byte[50];
    private int _bufferEnd = -1;
    private int _curPos = 0;

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
        if(_curPos<_bufferEnd){
            int ret = Byte.toUnsignedInt(_buffer[_curPos]);
            _curPos++;
            return ret;
        }
        
        synchronized(this){
            _askedForNext = true;
            if (_myNumber == 0) {
                _provider._loadNext();
            } else {
                notify();
            }
            while (_askedForNext) {
                try {
                    wait(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Unexpected interruption", e);
                }
            }
            if(_bufferEnd<0){
                return -1;
            }else{
                int ret = Byte.toUnsignedInt(_buffer[0]);
                _curPos = 1;
                return ret;
            }
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
        private final byte[] _bufferP = new byte[50];
        private int _bufferEndP = -1;
        

        private Provider(InputStream inputStream) {
            _inputStream = inputStream;
        }

        private synchronized void _loadNext() throws IOException {
            _bufferEndP = _inputStream.read(_bufferP);

            for (ParallelStream sibling : _readers) {
                synchronized (sibling) {
                    while (!sibling._askedForNext) {
                        try {
                            sibling.wait(1000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException("Unexpected interruption", e);
                        }
                    }
                    sibling._askedForNext = false;
                    sibling._bufferEnd = _bufferEndP;
                    if(_bufferEndP>=0){
                        System.arraycopy(_bufferP, 0, sibling._buffer, 0, _bufferEndP);
                    }
                    sibling.notify();
                }
            }
            // all asked
        }

        private synchronized void _detachAndClose(ParallelStream whosClosing) throws IOException {
            // TODO: check if it's safe to detach one of the readers while others are working. What if another reader becomes first thus acquiring a special position
            synchronized (whosClosing) {
                synchronized (_readers[0]) {
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
            }
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
     * A quick test. Creates several identical files.
     *
     * plain copy - 20.000.000 bytes per second
     *
     * 2,3 files - 2 - 5.000.000 bytes per second
     *
     * 1 file - 25.000.000 bytes per second
     *
     * @param arguments
     * @throws UnsupportedEncodingException
     * @throws FileNotFoundException
     */
    public static void main(String[] arguments) throws UnsupportedEncodingException, FileNotFoundException {
        InputStream is = new BufferedInputStream(new FileInputStream("/home/fedd/Videos/stgal.mp4"));
        //InputStream is = new ByteArrayInputStream("A quick brown fox jumped over a lazy dog\n".getBytes("UTF-8"));

        OutputStream fos1 = new BufferedOutputStream(new FileOutputStream("testfile1.mp4", true));
        OutputStream fos2 = new BufferedOutputStream(new FileOutputStream("testfile2.mp4", true));
        OutputStream fos3 = new BufferedOutputStream(new FileOutputStream("testfile3.mp4", true));

        InputStream ms1 = new ParallelStream(is);
        InputStream ms2 = new ParallelStream(ms1);
        InputStream ms3 = new ParallelStream(ms2);

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
            int i = 0;
            long time = System.currentTimeMillis();
            while ((byt = ms2.read()) != -1) {
                fos2.write(byt);
                i++;
                if (i > 100000) {
                    i = 0;
                    long took = System.currentTimeMillis() - time;
                    time = System.currentTimeMillis();
                    System.out.println("100000 bytes took " + took + " milliseconds which means " + (100000000 / took) + " bytes per second");
                }
            }
            fos2.close();
            ms2.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

}
