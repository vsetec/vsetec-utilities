/*
 * Copyright 2022 fedd.
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

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author fedd
 * @param <K> Key
 * @param <V> Value
 */
public class TrackedHashMap<K, V> extends HashMap<K, V> {

    private final Map<K, V> _original;

    public TrackedHashMap(Map<K, V> original) {
        super(original);
        _original = original;
    }

    public boolean hasChanged(K key) {
        return get(key) != _original.get(key);
    }

    public Map<K, V> getChanged() {
        HashMap<K, V> ret = new HashMap<>(3);
        for (Entry<K, V> entry : entrySet()) {
            if (hasChanged(entry.getKey())) {
                ret.put(entry.getKey(), entry.getValue());
            }
        }
        return ret;
    }

    public Map<K, V> getChangedAndCommit() {
        HashMap<K, V> ret = new HashMap<>(3);
        for (Entry<K, V> entry : entrySet()) {
            if (hasChanged(entry.getKey())) {
                ret.put(entry.getKey(), entry.getValue());
                _original.put(entry.getKey(), entry.getValue());
            }
        }
        return ret;
    }

    public Map<K, V> getOriginal() {
        return _original;
    }

}
