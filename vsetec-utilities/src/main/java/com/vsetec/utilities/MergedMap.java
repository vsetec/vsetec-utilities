/*
 * Copyright 2019 Fyodor Kravchenko <fedd@vsetec.com>.
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
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Fyodor Kravchenko <fedd@vsetec.com>
 */
public class MergedMap<K, V> implements Map<K, V> {

    private final Set<K> _keySet = new HashSet<>();
    private final List<Map<K, V>> _maps = new ArrayList<>();
    private Map<K, V> _lastMap = null;

    public synchronized void add(Map<K, V> map) {
        _maps.add(map);
        _keySet.addAll(map.keySet());
        _lastMap = map;
    }

    public synchronized List<Map<K, V>> maps() {
        return _maps;
    }

    public synchronized Map<K, V> last() {
        return _maps.get(_maps.size() - 1);
    }

    @Override
    public int size() {
        return _keySet.size();
    }

    @Override
    public boolean isEmpty() {
        return _keySet.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return _keySet.contains(key);
    }

    @Override
    public boolean containsValue(Object value) {
        for (Map map : _maps) {
            if (map.containsValue(value)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public V get(Object key) {
        V ret = null;
        for (Map<K, V> map : _maps) {
            ret = map.get(key);
            if (ret != null) {
                break;
            }
        }
        return ret;
    }

    @Override
    public synchronized V put(K key, V value) {
        Map<K, V> neededMap = _lastMap;
        for (Map<K, V> map : _maps) {
            if (map.containsKey(key)) {
                neededMap = map;
                break;
            }
        }
        V ret = neededMap.put(key, value);
        _keySet.add(key);
        return ret;
    }

    @Override
    public synchronized V remove(Object key) {
        Map<K, V> neededMap = _lastMap;
        int containCount = 0;
        for (Map<K, V> map : _maps) {
            if (map.containsKey(key)) {
                neededMap = map;
                containCount++;
            }
        }
        V ret = neededMap.remove(key);
        if (containCount <= 1) {
            _keySet.remove(key);
        }
        return ret;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        for (Map.Entry<? extends K, ? extends V> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public synchronized void clear() {
        _keySet.clear();
        for (Map<K, V> map : _maps) {
            map.clear();
        }
    }

    @Override
    public Set<K> keySet() {
        return Collections.unmodifiableSet(_keySet);
    }

    @Override
    public Collection<V> values() {

        Iterator<K> keyIter = _keySet.iterator();

        return new AbstractCollection<V>() {
            @Override
            public Iterator<V> iterator() {
                return new Iterator<V>() {
                    @Override
                    public boolean hasNext() {
                        return keyIter.hasNext();
                    }

                    @Override
                    public V next() {
                        return MergedMap.this.get(keyIter.next());
                    }
                };
            }

            @Override
            public int size() {
                return _keySet.size();
            }
        };
    }

    @Override
    public Set<Entry<K, V>> entrySet() {

        Iterator<K> keyIter = _keySet.iterator();

        return new AbstractSet<Map.Entry<K, V>>() {
            @Override
            public Iterator<Entry<K, V>> iterator() {
                return new Iterator<Entry<K, V>>() {
                    @Override
                    public boolean hasNext() {
                        return keyIter.hasNext();
                    }

                    @Override
                    public Entry<K, V> next() {

                        K key = keyIter.next();

                        return new Entry<K, V>() {
                            @Override
                            public K getKey() {
                                return key;
                            }

                            @Override
                            public V getValue() {
                                return MergedMap.this.get(key);
                            }

                            @Override
                            public V setValue(V value) {
                                return MergedMap.this.put(key, value);
                            }
                        };
                    }
                };
            }

            @Override
            public int size() {
                return _keySet.size();
            }
        };

    }

}
