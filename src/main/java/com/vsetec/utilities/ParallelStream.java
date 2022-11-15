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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;

/**
 *
 * @author fedd
 */
public class ParallelStream extends InputStream {

    private static final HashMap<InputStream, Provider> _providers = new HashMap<>();
    private static final int INITIALCHUNKSIZE = 50;

    private final Provider _provider;
    private boolean _isClosed = false;
    private boolean _detachedTemporarily = false; // TODO: temp detach not tested
    private final Deque<byte[]> _collectedWhenDetached = new ArrayDeque<>();
    private int _curPos = 0;
    private byte[] _buffer = new byte[0];
//    private String _tabs = null;
//    private int _lastLoaded = 0;

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
        }
        _provider._attach(this);
    }

    @Override
    public int read() throws IOException {
        if (_curPos < _buffer.length) {
            int ret = Byte.toUnsignedInt(_buffer[_curPos]);
            _curPos++;
            return ret;
        }

        _buffer = _collectedWhenDetached.poll();
        if (_buffer == null) {
            if (_detachedTemporarily) {
                throw new IllegalStateException("Reading from a detached ParallelStream");
            }
            //synchronized (_provider) {
            _buffer = _provider._loadNext();
//                if(_buffer!=null){
//                    _lastLoaded++;
//                    System.out.println(_tabs + "LO " + _provider._currentChunk+ "=" + _lastLoaded + (_lastLoaded != _provider._currentChunk?" *****":""));
//                }else{
//                    System.out.println(_tabs + "LO finish");
//                }
//                _lastLoaded = _provider._currentChunk;
            //}
        }
//        else{
//            _lastLoaded++;
//            System.out.println(_tabs + "CU " + _lastLoaded + "(" + _collectedWhenDetached.size() +")");
//        }

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
            _provider._detach(this, false);
        }
    }

    public InputStream getSource() {
        return _provider._inputStream;
    }

    public void detachTemporarily() {
        try {
            _provider._detach(this, true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void reattach() {
        _provider._attach(this);
    }

    private static class Provider {

        private int _chunkSize = INITIALCHUNKSIZE;
        private final InputStream _inputStream;
        private final HashSet<ParallelStream> _readers = new HashSet<>(4);
        private final HashSet<ParallelStream> _temporarilyDetachedReaders = new HashSet<>(3);
        private byte[] _bufferTmp = new byte[_chunkSize];
        private byte[][] _bufferHolder = new byte[1][0]; //poor man's mutable byte
        private int _howManyHaveAsked = 0;
//        private int _currentChunk = 0;

        private Provider(InputStream inputStream) {
            _inputStream = inputStream;
        }

        private synchronized byte[] _loadNext() throws IOException {

            //final Object lock = _lock; // TODO: potential loophole when adding/removing readers while reading
            //synchronized (this)
            {

                if (_bufferHolder[0] == null) {
                    return null;
                }

                _howManyHaveAsked++;

                long waitTime = System.currentTimeMillis();
                byte[] oldBuffer = _bufferHolder[0];
                while (_howManyHaveAsked < _readers.size()) {
                    try {
                        this.wait(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    if (oldBuffer != _bufferHolder[0]) { // switched to next
                        return _bufferHolder[0];
                    }
                }

                _howManyHaveAsked = 0;
                //_lock = new Object();

                int size = _inputStream.read(_bufferTmp);
                if (size == -1) {

                    _bufferHolder[0] = null;

                } else {

                    _bufferHolder[0] = new byte[size];
                    System.arraycopy(_bufferTmp, 0, _bufferHolder[0], 0, size);

//                    _currentChunk++;
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

                if (_bufferHolder[0] != null) {
                    for (ParallelStream par : _temporarilyDetachedReaders) {
                        par._collectedWhenDetached.add(_bufferHolder[0]);
                    }
                }

                this.notifyAll();
                return _bufferHolder[0];

            }

        }

        private void _detach(ParallelStream par, boolean temporarily) throws IOException {
            synchronized (this) {
                _readers.remove(par);

                if (temporarily) {
                    if (!par._detachedTemporarily) {
                        par._detachedTemporarily = true;
                        _temporarilyDetachedReaders.add(par);
                    }
                } else {
                    if (par._detachedTemporarily) {
                        par._detachedTemporarily = false;
                        _temporarilyDetachedReaders.remove(par);
                    }
                }

                if (!temporarily && _readers.isEmpty() && _temporarilyDetachedReaders.isEmpty()) {
                    synchronized (_providers) {
                        _inputStream.close();
                        _providers.remove(_inputStream);
                    }
                }

                this.notifyAll();
            }
        }

        private void _attach(ParallelStream par) {
            synchronized (this) {
                _readers.add(par);
                if (par._detachedTemporarily) {
                    _temporarilyDetachedReaders.remove(par);
                    par._detachedTemporarily = false;
                }
                this.notifyAll();
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

        String sourceFile = "test.ts";

        final int[] waitcount = new int[1];
        waitcount[0] = 0;

        InputStream is = new BufferedInputStream(new FileInputStream(sourceFile));
        //InputStream is = new ByteArrayInputStream("A quick brown fox jumped over a lazy dog\n".getBytes("UTF-8"));

        int number = 5; // no less than 3 to test
        OutputStream[] fos = new OutputStream[number];
        ParallelStream[] ms = new ParallelStream[number];
        for (int i = 0; i < number; i++) {
            fos[i] = new BufferedOutputStream(new FileOutputStream("testfile" + i, false));
            if (i != 2) {
                ms[i] = new ParallelStream(is);
                //char[]tabs = new char[i];
                //for(int y=0;y<i;y++){
                //    tabs[y] = '\t';
                //}
                //ms[i]._tabs = new String(tabs);
            }
        }

        for (int i = 3; i < number; i++) {
            waitcount[0]++;
            final int y = i;
            Thread thread = new Thread(new Runnable() {
                //final int ii = y;

                @Override
                public void run() {
                    try {
                        int byt;
                        long beforeSleep = (long) (Math.random() * 90000) + 10000;
                        while ((byt = ms[y].read()) != -1) {
                            fos[y].write(byt);
                            beforeSleep--;

                            if (beforeSleep <= 0) {
                                beforeSleep = (long) (Math.random() * 90000) + 10000;
                                if ((beforeSleep & 1) == 0) {
//                                    System.out.println("***** Sleeping with detach: " + y);
                                    ms[y].detachTemporarily();
                                    Thread.sleep(10);
                                    ms[y].reattach();
                                } else {
//                                    System.out.println("***** Sleeping withOUT detach: " + y);
                                    Thread.sleep(10);
                                }
                            }

                        }
                        fos[y].close();
                        ms[y].close();
                        synchronized (waitcount) {
                            waitcount[0]--;
                            waitcount.notify();
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }, "ParallelStream test " + i);
            thread.start();
        }

        if (number >= 2) {
            waitcount[0]++;

            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {

                        System.out.println("***** Sleeping before first byte: 1");
                        Thread.sleep(3000);
                        int throwawayAfter = 100000;

                        int byt;
                        while ((byt = ms[1].read()) != -1) {
                            fos[1].write(byt);
                            throwawayAfter--;
                            if (throwawayAfter <= 0) {
                                System.out.println("***** Detaching mid read: 1");
                                break;
                            }
                        }
                        fos[1].close();
                        ms[1].close();
                        synchronized (waitcount) {
                            waitcount[0]--;
                            waitcount.notify();
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }, "ParallelStream test 1 with delay");
            thread.start();
        }

        if (number >= 3) {
            waitcount[0]++;
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {

                        int i = 2;

                        Thread.sleep(3100);
                        System.out.println("***** Attaching mid read: 2");

                        ms[i] = new ParallelStream(is);
                        char[] tabs = new char[i];
                        for (int y = 0; y < i; y++) {
                            tabs[y] = '\t';
                        }
                        //ms[i]._tabs = new String(tabs);

                        int byt;
                        while ((byt = ms[i].read()) != -1) {
                            fos[i].write(byt);
                        }
                        fos[i].close();
                        ms[i].close();
                        synchronized (waitcount) {
                            waitcount[0]--;
                            waitcount.notify();
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }, "ParallelStream test 2 with delayed attach");
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

            synchronized (waitcount) {
                while (waitcount[0] > 0) {
                    waitcount.wait(); // wait all to finish
                }
            }

            // comparing
            System.out.println("\n\n\nnow comparing");
            for (i = 0; i < number; i++) {

                String[] command = new String[]{"cmp", sourceFile, "testfile" + i};

                System.out.println("compare testfile" + i);
                Process process = Runtime.getRuntime().exec(command);

                try {
                    int b;
                    while ((b = process.getErrorStream().read()) != -1) {
                        System.err.write(b);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            int b;
                            while ((b = process.getInputStream().read()) != -1) {
                                System.out.write(b);
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                }, "outreader");
                thread.start();

                process.waitFor();

            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

}
