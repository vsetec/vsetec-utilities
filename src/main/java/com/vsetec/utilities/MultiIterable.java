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

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 *
 * @author fedd
 */
public class MultiIterable<T> implements Iterable<T> {

    private final ArrayDeque<Iterable<T>> _iterables = new ArrayDeque<>(3);

    public void add(Iterable<T> iterable) {
        _iterables.add(iterable);
    }

    @Override
    public Iterator<T> iterator() {

        Iterator<T> firstIterator;
        try {
            firstIterator = _iterables.pop().iterator();
        } catch (NoSuchElementException e) {
            return Collections.emptyIterator();
        }

        return new Iterator<T>() {

            Iterator<T> _current = firstIterator;

            @Override
            public boolean hasNext() {
                if (!_current.hasNext()) {
                    try {
                        _current = _iterables.pop().iterator();
                        return hasNext();
                    } catch (NoSuchElementException e) {
                        return false;
                    }
                } else {
                    return true;
                }
            }

            @Override
            public T next() {

                try {
                    return _current.next();
                } catch (NoSuchElementException e) {
                    _current = _iterables.pop().iterator();
                    return next();
                }
            }
        };
    }

}
