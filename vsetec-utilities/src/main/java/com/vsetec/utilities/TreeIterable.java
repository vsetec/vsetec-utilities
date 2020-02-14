/*
 * Copyright 2020 fedd.
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

/**
 *
 * @author fedd
 */
public abstract class TreeIterable<T> implements Iterable<T> {

    abstract public Iterable<T> getChildIterable(T key);

    abstract public Iterable<T> getInitialIterable();

    @Override
    public Iterator<T> iterator() {

        MultiIterable<T> multiIter = new MultiIterable<>();
        multiIter.add(getInitialIterable());
        Iterator<T> i = multiIter.iterator();

        return new Iterator<T>() {
            @Override
            public boolean hasNext() {
                return i.hasNext();
            }

            @Override
            public T next() {
                T ret = i.next();
                Iterable<T> child = getChildIterable(ret);
                if (child != null) {
                    multiIter.add(child);
                }
                return ret;
            }
        };
    }

}
