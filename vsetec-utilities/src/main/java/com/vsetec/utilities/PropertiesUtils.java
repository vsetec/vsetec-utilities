/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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

        for (String key : map.keySet()) {

            _map2propRecur(props, key, (Map<String, Object>) map.get(key));

        }

        return props;

    }

}
