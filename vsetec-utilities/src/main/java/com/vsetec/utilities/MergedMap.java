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
import java.util.Collections;
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

    private final Set<K> _keySet = new HashSet<>();
    private final List<HiddenMap> _maps = new ArrayList<>();
    private HiddenMap _lastMap = null;

    public synchronized void add(Map<K, V> map) {
        HiddenMap hiddenMap = new HiddenMap(map);
        _maps.add(hiddenMap);
        _keySet.addAll(map.keySet());
        _lastMap = hiddenMap;
    }

    public synchronized void add(int index, Map<K, V> map) {
        HiddenMap hiddenMap = new HiddenMap(map);
        _maps.add(index, hiddenMap);
        _keySet.addAll(map.keySet());
        _lastMap = _maps.get(_maps.size() - 1);
    }

    public synchronized MergedMap shallowCopy() {
        MergedMap ret = new MergedMap<K, V>();
        for (HiddenMap map : _maps) {
            ret.add(map._hidden);
        }
        return ret;
    }

    public synchronized List<? extends Map<K, V>> maps() {
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

    private class HiddenMap implements Map<K, V>, Serializable {

        private final Map<K, V> _hidden;

        public HiddenMap(Map<K, V> _hidden) {
            this._hidden = _hidden;
        }

        @Override
        public int size() {
            return _hidden.size();
        }

        @Override
        public boolean isEmpty() {
            return _hidden.isEmpty();
        }

        @Override
        public boolean containsKey(Object key) {
            return _hidden.containsKey(key);
        }

        @Override
        public boolean containsValue(Object value) {
            return _hidden.containsValue(value);
        }

        @Override
        public V get(Object key) {
            return _hidden.get(key);
        }

        @Override
        public V put(K key, V value) {
            synchronized (MergedMap.this) {
                _keySet.add(key);
                return _hidden.put(key, value);
            }
        }

        @Override
        public V remove(Object key) {
            synchronized (MergedMap.this) {
                V ret = _hidden.remove(key);
                boolean keyDisappeared = true;
                for (HiddenMap map : _maps) {
                    if (map._hidden.containsKey(key)) {
                        keyDisappeared = false;
                        break;
                    }
                }
                if (keyDisappeared) {
                    _keySet.remove(key);
                }
                return ret;
            }
        }

        @Override
        public void putAll(Map<? extends K, ? extends V> m) {
            synchronized (MergedMap.this) {
                _hidden.putAll(m);
                _keySet.addAll(m.keySet());
            }
        }

        @Override
        public void clear() {
            synchronized (MergedMap.this) {
                _hidden.clear();
                _keySet.clear();
                for (HiddenMap map : _maps) {
                    _keySet.addAll(map._hidden.keySet());
                }
            }
        }

        @Override
        public Set<K> keySet() {
            return _hidden.keySet();
        }

        @Override
        public Collection<V> values() {
            return _hidden.values();
        }

        @Override
        public Set<Entry<K, V>> entrySet() {
            return new Set<Entry<K, V>>() {
                @Override
                public int size() {
                    return _hidden.size();
                }

                @Override
                public boolean isEmpty() {
                    return _hidden.isEmpty();
                }

                @Override
                public boolean contains(Object o) {
                    return _hidden.entrySet().contains(o);
                }

                @Override
                public Iterator<Entry<K, V>> iterator() {
                    return _hidden.entrySet().iterator();
                }

                @Override
                public Object[] toArray() {
                    return _hidden.entrySet().toArray();
                }

                @Override
                public <T> T[] toArray(T[] arg0) {
                    return _hidden.entrySet().toArray(arg0);
                }

                @Override
                public boolean add(Entry<K, V> e) {
                    synchronized (MergedMap.this) {
                        boolean ret = _hidden.entrySet().add(e);
                        if (ret) {
                            _keySet.add(e.getKey());
                        }
                        return ret;
                    }
                }

                @Override
                public boolean remove(Object o) {
                    synchronized (MergedMap.this) {
                        if (o instanceof Entry) {
                            Entry e = (Entry) o;
                            boolean ret = HiddenMap.this.containsKey(e.getKey());
                            if (ret) {
                                HiddenMap.this.remove(e.getKey());
                            }
                            return ret;
                        } else {
                            return false;
                        }
                    }
                }

                @Override
                public boolean containsAll(Collection<?> c) {
                    return _hidden.entrySet().containsAll(c);
                }

                @Override
                public boolean addAll(Collection<? extends Entry<K, V>> c) {
                    synchronized (MergedMap.this) {
                        boolean ret = false;
                        for (Entry<K, V> entry : c) {
                            ret = HiddenMap.this.put(entry.getKey(), entry.getValue()) != null;
                        }
                        return ret;
                    }
                }

                @Override
                public boolean retainAll(Collection<?> c) {
                    throw new UnsupportedOperationException("Not supported yet."); //TODO: is it really needed at any time?
                }

                @Override
                public boolean removeAll(Collection<?> c) {
                    synchronized (MergedMap.this) {
                        boolean ret = false;
                        for (Object obj : c) {
                            if (obj instanceof Entry) {
                                Entry<K, V> entry = (Entry<K, V>) obj;
                                boolean thisTime = HiddenMap.this.remove(entry.getKey(), entry.getValue());
                                if (thisTime) {
                                    ret = true;
                                }
                            }
                        }
                        return ret;
                    }
                }

                @Override
                public void clear() {
                    HiddenMap.this.clear();
                }
            };
        }

    }

}
