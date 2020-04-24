/*
 * Copyright 2018 Fyodor Kravchenko <fedd@vsetec.com>.
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
import java.util.Properties;

/**
 *
 * @author fedd
 */
public class PropertiesUtils {

    public static Map<String, Object> propertiesToMapOfMaps(Properties props) {
        Map<String, Object> ret = new HashMap<>(4);

        for (String attr : props.stringPropertyNames()) {
            Map<String, Object> map = ret;
            String[] keys = attr.split("\\.");

            for (String key : keys) {
                Map<String, Object> newmap = (Map<String, Object>) map.get(key);
                if (newmap == null) {
                    newmap = new HashMap<>(4);
                    map.put(key, newmap);
                }
                map = newmap;
            }

            map.put("", props.getProperty(attr));

        }
        return ret;
    }

    private static void _map2propRecur(Properties properties, String curPrefix, Map<String, Object> curMap) {

        for (Map.Entry<String, Object> keyVal : curMap.entrySet()) {

            if (keyVal.getKey().length() == 0) {
                properties.setProperty(curPrefix, (String) keyVal.getValue());
            } else {

                _map2propRecur(properties, curPrefix + "." + keyVal.getKey(), (Map<String, Object>) keyVal.getValue());

            }

        }

    }

    public static Properties mapOfMapsToProperties(Map<String, Object> map) {
        Properties props = new Properties();

        if (map != null) {
            for (String key : map.keySet()) {

                _map2propRecur(props, key, (Map<String, Object>) map.get(key));

            }
        }

        return props;

    }

}
