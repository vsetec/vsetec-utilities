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
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author Alex R, fedd
 * https://stackoverflow.com/questions/9987019/how-to-know-when-a-completionservice-is-finished-delivering-results
 * @param <T>
 */
public class CompletionIterator<T> implements Iterable<T> {

    private final AtomicInteger _count = new AtomicInteger(0);

    private final CompletionService<T> _completer;

    public CompletionIterator(ExecutorService executor) {
        this._completer = new ExecutorCompletionService<>(executor);
    }

    public void submit(Callable<T> task) {
        _completer.submit(task);
        _count.incrementAndGet();
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            @Override
            public boolean hasNext() {
                return _count.decrementAndGet() > 0;
            }

            @Override
            public T next() {
                try {
                    return _completer.take().get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

}
