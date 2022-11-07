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

    private final Provider _provider;
    private boolean _isClosed = false;
    private int _curPos = 0;

    public ParallelStream(InputStream parentStream) {
        if (parentStream instanceof ParallelStream) {
            _provider = ((ParallelStream) parentStream)._provider;
        } else {
            synchronized (_providers) {
                Provider provider = _providers.get(parentStream);
                if (provider != null) {
                    _provider = provider;
                } else {
                    _provider = new Provider(parentStream);
                    _providers.put(parentStream, provider);
                }
            }
        }
        _provider._attach(this);
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

    private class Provider {

        private final InputStream _inputStream;
        private final HashSet<ParallelStream> _readers = new HashSet<>(4);
        private final byte[] _buffer = new byte[50];
        private int _bufferEnd = -1;
        private int _howManyHaveAsked = 0;
        private int _stuck = 0;

        private Provider(InputStream inputStream) {
            _inputStream = inputStream;
        }

        private synchronized void _loadNext() throws IOException {
            //TODO: too slow. rework!
            while (_stuck > 0 && _howManyHaveAsked == 0) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            _howManyHaveAsked++;
            _stuck++;

            while (_howManyHaveAsked < _readers.size()) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                if (_howManyHaveAsked == 0) {
                    _stuck--;
                    if (_stuck <= 0) {
                        notifyAll();
                    }
                    return;
                }
            }

            _howManyHaveAsked = 0;
            _stuck--;
            _bufferEnd = _inputStream.read(_buffer);
            notifyAll();

        }

        private synchronized void _detachAndClose(ParallelStream whosClosing) throws IOException {
            _readers.remove(whosClosing);
            notifyAll();
            if (_readers.isEmpty()) {
                _inputStream.close();
                _providers.remove(_inputStream);
            }
        }

        private synchronized void _attach(ParallelStream ms) {
            _readers.add(ms);
            notifyAll();
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
        InputStream is = new BufferedInputStream(new FileInputStream("/home/fedd/Videos/DASH_720.mp4"));
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

                    Thread.sleep(3000);

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
