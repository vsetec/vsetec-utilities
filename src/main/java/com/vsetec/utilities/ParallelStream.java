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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.HashSet;

/**
 *
 * @author fedd
 */
public class ParallelStream extends InputStream {

    private static final HashMap<InputStream, Provider> _providers = new HashMap<>();
    private static final int CHUNKSIZE = 50;

    private final Provider _provider;
    private boolean _isClosed = false;
    private int _curPos = 0;

    public ParallelStream(InputStream parentStream) {
        synchronized (_providers) {
            if (parentStream instanceof ParallelStream) {
                _provider = ((ParallelStream) parentStream)._provider;
            } else {
                Provider provider = _providers.get(parentStream);
                if (provider != null) {
                    _provider = provider;
                } else {
                    _provider = new Provider(parentStream);
                    _providers.put(parentStream, _provider);
                }
            }
            _provider._attach(this);
        }
    }

    @Override
    public int read() throws IOException {
        if (_curPos < _provider._bufferEnd) {
            int ret = Byte.toUnsignedInt(_provider._buffer[_curPos]);
            _curPos++;
            return ret;
        }

        _provider._loadNext();

        if (_provider._bufferEnd < 0) {
            return -1;
        }
        _curPos = 1;
        return Byte.toUnsignedInt(_provider._buffer[0]);
    }

    @Override
    public void close() throws IOException {
        if (!_isClosed) {
            _isClosed = true;
            _provider._detachAndClose(this);
        }
    }

    public InputStream getSource() {
        return _provider._inputStream;
    }

    private static class Provider {

        private final InputStream _inputStream;
        private final HashSet<ParallelStream> _readers = new HashSet<>(4);
        private final byte[] _buffer = new byte[CHUNKSIZE];
        private int _bufferEnd = -1;
        private Object _lock = new Object();
        private int _howManyHaveAsked = 0;
        //private int _stuck = 0;

        private Provider(InputStream inputStream) {
            _inputStream = inputStream;
        }

        private void _loadNext() throws IOException {

            final Object lock = _lock; // TODO: potential loophole when adding/removing readers while reading
            synchronized (lock) {

                _howManyHaveAsked++;

                while (_howManyHaveAsked < _readers.size()) {
                    try {
                        lock.wait(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    if (lock != _lock) { // switched to next
                        return;
                    }
                }

                _howManyHaveAsked = 0;
                _bufferEnd = _inputStream.read(_buffer);
                lock.notifyAll();
                _lock = new Object();

            }

        }

        private void _detachAndClose(ParallelStream par) throws IOException {
            synchronized (_providers) {
                synchronized (_lock) {
                    _readers.remove(par);
                    _lock.notifyAll();
                    if (_readers.isEmpty()) {
                        _inputStream.close();
                        _providers.remove(_inputStream);
                    }
                }
            }
        }

        private void _attach(ParallelStream par) {
            synchronized (_lock) {
                _readers.add(par);
                _lock.notifyAll();
            }
        }

    }

    /**
     * A quick test. Creates several identical files.
     *
     * plain copy - 20.000.000 bytes per second
     *
     * 2,3 files - 2 - 2.500.000 bytes per second
     *
     * 1 file - 25.000.000 bytes per second
     *
     * @param arguments
     * @throws UnsupportedEncodingException
     * @throws FileNotFoundException
     */
    public static void main(String[] arguments) throws UnsupportedEncodingException, FileNotFoundException {
        InputStream is = new BufferedInputStream(new FileInputStream("/home/fedd/Videos/Ivan.Vasilyevich.1973.720p.mkv"));
        //InputStream is = new ByteArrayInputStream("A quick brown fox jumped over a lazy dog\n".getBytes("UTF-8"));

        int number = 4; // no less than 3 to test
        OutputStream[] fos = new OutputStream[number];
        InputStream[] ms = new ParallelStream[number];
        for (int i = 0; i < number; i++) {
            fos[i] = new BufferedOutputStream(new FileOutputStream("testfile" + i + ".mkv", true));
            ms[i] = new ParallelStream(is);
        }

        for (int i = 2; i < number; i++) {
            final int y = i;
            Thread thread = new Thread(new Runnable() {
                final int ii = y;

                @Override
                public void run() {
                    try {
                        int byt;
                        while ((byt = ms[ii].read()) != -1) {
                            fos[ii].write(byt);
                        }
                        fos[ii].close();
                        ms[ii].close();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }, "ParallelStream test " + i);
            thread.start();
        }

        if (number >= 2) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {

                        Thread.sleep(3000);

                        int byt;
                        while ((byt = ms[1].read()) != -1) {
                            fos[1].write(byt);
                        }
                        fos[1].close();
                        ms[1].close();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }, "ParallelStream test 1 with delay");
            thread.start();
        }

        try {
            int byt;
            int i = 0;
            long time = System.currentTimeMillis();
            while ((byt = ms[0].read()) != -1) {
                fos[0].write(byt);
                i++;
                if (i > 100000) {
                    i = 0;
                    long took = System.currentTimeMillis() - time;
                    time = System.currentTimeMillis();
                    System.out.println("100000 bytes took " + took + " milliseconds which means " + (100000000 / took) + " bytes per second");
                }
            }
            fos[0].close();
            ms[0].close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

}
