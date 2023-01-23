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

import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 *
 * @author Alex R, fedd
 * https://stackoverflow.com/questions/9987019/how-to-know-when-a-completionservice-is-finished-delivering-results
 * @param <T>
 */
public class CompletionIterator<T> implements Iterator<T> {

    private int _count = 0;
    private final CompletionService<T> _completer;
    private final long _timeout;

    public CompletionIterator(ExecutorService executor) {
        this._completer = new ExecutorCompletionService<>(executor);
        _timeout = Long.MAX_VALUE;
    }

    public CompletionIterator(ExecutorService executor, long timeout) {
        this._completer = new ExecutorCompletionService<>(executor);
        _timeout = timeout;
    }

    public Future<T> submit(Callable<T> task) {
        Future<T> ret = _completer.submit(task);
        _count++;
        return ret;
    }

    @Override
    public boolean hasNext() {
        return _count > 0;
    }

    @Override
    public T next() {
        final T ret;
        try {
            if (_timeout == Long.MAX_VALUE) {
                ret = _completer.take().get();
            } else {
                try {
                    ret = _completer.take().get(_timeout, TimeUnit.MILLISECONDS);
                } catch (TimeoutException te) {
                    return null;
                }
            }
            _count--;
            return ret;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

}
