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

import java.io.Serializable;
import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Fyodor Kravchenko <fedd@vsetec.com>
 * @param <K> key class
 * @param <V> value class
 */
public class MergedMap<K, V> implements Map<K, V>, Serializable {

    private final List<Map<K, V>> _maps = new ArrayList<>();
    private Map<K, V> _lastMap = null;

    public synchronized void add(Map<K, V> map) {
        _maps.add(map);
        _lastMap = map;
    }

    public synchronized void add(int index, Map<K, V> map) {
        _maps.add(index, map);
        _lastMap = _maps.get(_maps.size() - 1);
    }

    public synchronized MergedMap shallowCopy() {
        MergedMap ret = new MergedMap<K, V>();
        for (Map map : _maps) {
            ret.add(map);
        }
        return ret;
    }

    public synchronized List<? extends Map<K, V>> maps() {
        return _maps;
    }

    public synchronized Map<K, V> last() {
        return _maps.get(_maps.size() - 1);
    }

    public synchronized void detach(int index) {
        _maps.set(index, new HashMap<>(_maps.get(index)));
    }

    public synchronized void detachLast() {
        detach(_maps.size() - 1);
    }

    @Override
    public int size() {
        return keySet().size();
    }

    @Override
    public boolean isEmpty() {
        return keySet().isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return keySet().contains(key);
    }

    @Override
    public boolean containsValue(Object value) {
        for (Map<K, V> map : _maps) {
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
        for (Map<K, V> map : _maps) {
            map.clear();
        }
    }

    @Override
    public Set<K> keySet() {
        Set<K> ret = new HashSet<>();
        for (Map<K, V> map : _maps) {
            ret.addAll(map.keySet());
        }
        return ret;
    }

    @Override
    public Collection<V> values() {

        Iterator<K> keyIter = keySet().iterator();

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
                return keySet().size();
            }
        };
    }

    @Override
    public Set<Entry<K, V>> entrySet() {

        Iterator<K> keyIter = keySet().iterator();

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
                return keySet().size();
            }
        };

    }

}
