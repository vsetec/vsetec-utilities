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
    private byte[] _buffer = new byte[0];

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
        if (_curPos < _buffer.length) {
            int ret = Byte.toUnsignedInt(_buffer[_curPos]);
            _curPos++;
            return ret;
        }

        _buffer = _provider._loadNext();

        if (_buffer == null) {
            return -1;
        }
        _curPos = 1;
        return Byte.toUnsignedInt(_buffer[0]);
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

        private int _chunkSize = 50;
        private final InputStream _inputStream;
        private final HashSet<ParallelStream> _readers = new HashSet<>(4);
        private byte[] _bufferTmp = new byte[_chunkSize];
        private Object _lock = new Object();
        private byte[][] _bufferHolder = new byte[1][];
        private int _howManyHaveAsked = 0;

        private Provider(InputStream inputStream) {
            _inputStream = inputStream;
        }

        private byte[] _loadNext() throws IOException {

            final Object lock = _lock; // TODO: potential loophole when adding/removing readers while reading
            synchronized (lock) {

                _howManyHaveAsked++;

                long waitTime = System.currentTimeMillis();
                while (_howManyHaveAsked < _readers.size()) {
                    try {
                        lock.wait(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    if (lock != _lock) { // switched to next
                        return _bufferHolder[0];
                    }
                }

                _howManyHaveAsked = 0;
                _lock = new Object();

                int size = _inputStream.read(_bufferTmp);
                if (size == -1) {

                    _bufferHolder[0] = null;

                } else {
                    _bufferHolder[0] = new byte[size];
                    System.arraycopy(_bufferTmp, 0, _bufferHolder[0], 0, size);

                    // compute chunk size
                    waitTime = System.currentTimeMillis() - waitTime;
                    boolean resize = false;
                    if (waitTime > 100 && _chunkSize > 50) {
                        _chunkSize = (int) (_chunkSize * 0.7);
                        resize = true;
                    } else if (size == _chunkSize) {
                        _chunkSize = (int) (_chunkSize * 1.5);
                        resize = true;
                    }
                    if (resize) {
                        _bufferTmp = new byte[_chunkSize];
                    }
                }

                lock.notifyAll();
                return _bufferHolder[0];

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
     * 4 files - 15-20 Megabytes per second
     *
     * @param arguments
     * @throws UnsupportedEncodingException
     * @throws FileNotFoundException
     */
    public static void main(String[] arguments) throws UnsupportedEncodingException, FileNotFoundException {
        InputStream is = new BufferedInputStream(new FileInputStream("test.ts"));
        //InputStream is = new ByteArrayInputStream("A quick brown fox jumped over a lazy dog\n".getBytes("UTF-8"));

        int number = 4; // no less than 3 to test
        OutputStream[] fos = new OutputStream[number];
        InputStream[] ms = new ParallelStream[number];
        for (int i = 0; i < number; i++) {
            fos[i] = new BufferedOutputStream(new FileOutputStream("testfile" + i, true));
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
            int j = 0;
            long tot = 0;
            long time = System.currentTimeMillis();
            while ((byt = ms[0].read()) != -1) {
                fos[0].write(byt);
                i++;
                if (i > 1048576) {
                    i = 0;
                    long took = System.currentTimeMillis() - time;
                    time = System.currentTimeMillis();
                    long kb = 1048576000 / took / 1024;
                    tot = tot + kb;
                    j++;

                    System.out.println("1MB took " + took + " ms which means " + kb + " KB/s. Avg - " + (tot / j) + "KB/s");
                }
            }
            fos[0].close();
            ms[0].close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

}
