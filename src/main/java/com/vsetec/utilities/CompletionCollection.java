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

import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 *
 * @author Alex R, fedd
 * https://stackoverflow.com/questions/9987019/how-to-know-when-a-completionservice-is-finished-delivering-results
 * @param <T>
 */
public class CompletionCollection<T> extends AbstractCollection<T> {

    private int _count = 0;
    private final CompletionService<T> _completer;
    private final long _timeout;

    public CompletionCollection(ExecutorService executor) {
        this._completer = new ExecutorCompletionService<>(executor);
        _timeout = Long.MAX_VALUE;
    }

    public CompletionCollection(ExecutorService executor, long timeout) {
        this._completer = new ExecutorCompletionService<>(executor);
        _timeout = timeout;
    }

    public void submit(Callable<T> task) {
        _completer.submit(task);
        _count++;
    }

    @Override
    public int size() {
        return _count;
    }

    @Override
    public Iterator<T> iterator() {

        if (_timeout == Long.MAX_VALUE) {

            return new Iterator<T>() {

                @Override
                public boolean hasNext() {
                    return _count > 0;
                }

                @Override
                public T next() {
                    try {
                        T ret = _completer.take().get();
                        _count--;
                        return ret;
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException(e);
                    }
                }

            };

        } else {

            return new Iterator<T>() {

                @Override
                public boolean hasNext() {
                    return _count > 0;
                }

                @Override
                public T next() {
                    try {
                        T ret = _completer.take().get(_timeout, TimeUnit.MILLISECONDS);
                        _count--;
                        return ret;
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException(e);
                    } catch (TimeoutException e) {
                        _count--;
                        return null;
                    }
                }

            };

        }

    }

}
