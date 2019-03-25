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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.Format;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.TimeZone;
import java.util.TreeMap;
import org.apache.commons.text.StringEscapeUtils;

/**
 *
 * @author Fyodor Kravchenko <fedd@vsetec.com>
 */
public class Formatter {

    private final ThreadLocal<Map<String, Map<String, Map<String, Parser>>>> _formatCache
            = new ThreadLocal<Map<String, Map<String, Map<String, Parser>>>>() {
        @Override
        protected Map<String, Map<String, Map<String, Parser>>> initialValue() {
            return new HashMap<>();
        }
    };
    private final TreeMap<Integer, Map<String, List<TimeZone>>> _offsetCountryTimezone = new TreeMap<>();
    private final Map<Locale, ResourceBundle[]> _resBnd;

    public Formatter(Map<Locale, ResourceBundle[]> resourceBundles) {
        try {
            _resBnd = resourceBundles;

            // TODO: make it reload periodically, like once a half hour, in a separate thread? synchronze?
            long today = System.currentTimeMillis();
            BufferedReader reader = new BufferedReader(new InputStreamReader(Formatter.class.getResourceAsStream("zone1970.tab"), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) {
                    continue;
                }

                String[] entry = line.split("\t");
                if (entry.length < 3) {
                    continue;
                }

                String[] countryCodes = entry[0].split(",");
                String tzName = entry[2];

                TimeZone tz = TimeZone.getTimeZone(tzName);
                Integer offset = tz.getOffset(today);

                Map<String, List<TimeZone>> countryTimezones = _offsetCountryTimezone.get(offset);
                if (countryTimezones == null) {
                    countryTimezones = new HashMap<>();
                    _offsetCountryTimezone.put(offset, countryTimezones);
                }

                for (String country : countryCodes) {
                    List<TimeZone> timezones = countryTimezones.get(country);
                    if (timezones == null) {
                        timezones = new ArrayList<>();
                        countryTimezones.put(country, timezones);
                    }
                    timezones.add(tz);
                }
            }

            reader.close();
        } catch (IOException ee) {
            throw new RuntimeException("Couldn't read zone1970.tab", ee);
        }
    }

    private Parser _getPattern(String pattern, Locale locale, String timeZone) {

        assert (locale != null);

        Map<String, Map<String, Map<String, Parser>>> formats = _formatCache.get();

        Map<String, Map<String, Parser>> localeFormats;
        String localeString = locale.toLanguageTag();
        localeFormats = formats.get(localeString);
        if (localeFormats == null) {
            localeFormats = new HashMap<>();
            formats.put(localeString, localeFormats);
        }

        Map<String, Parser> timezoneFormats;
        timezoneFormats = localeFormats.get(timeZone);
        if (timezoneFormats == null) {
            timezoneFormats = new HashMap<>();
            localeFormats.put(timeZone, timezoneFormats);
        }

        Parser format;
        format = timezoneFormats.get(pattern);
        if (format == null) {
            if (timeZone != null) {

                final DateFormat df;
                final String trimmedPattern;
                if (pattern == null) {
                    trimmedPattern = "mn";
                } else {
                    trimmedPattern = pattern.trim();
                }

                if (trimmedPattern.length() <= 2) {
                    char[] formatChars = trimmedPattern.toCharArray();
                    if (formatChars.length == 1) {
                        formatChars = new char[]{formatChars[0], 'n'};
                    } else if (formatChars.length == 0) {
                        formatChars = new char[]{'m', 'n'};
                    }
                    int dateFormat, timeFormat;

                    switch (formatChars[0]) { // date
                        case 'f':
                            dateFormat = DateFormat.FULL;
                            break;
                        case 'l':
                            dateFormat = DateFormat.LONG;
                            break;
                        case 'm':
                            dateFormat = DateFormat.MEDIUM;
                            break;
                        case 's':
                            dateFormat = DateFormat.SHORT;
                            break;
                        default:
                            dateFormat = DateFormat.DEFAULT;
                    }
                    switch (formatChars[1]) { // time
                        case 'f':
                            timeFormat = DateFormat.FULL;
                            break;
                        case 'l':
                            timeFormat = DateFormat.LONG;
                            break;
                        case 'm':
                            timeFormat = DateFormat.MEDIUM;
                            break;
                        case 's':
                            timeFormat = DateFormat.SHORT;
                            break;
                        default:
                            timeFormat = DateFormat.DEFAULT;
                    }

                    if (formatChars[0] == 'n') { // no date
                        if (formatChars[1] == 'n') { // no time
                            // bs
                            df = DateFormat.getInstance();
                        } else { // time only
                            df = DateFormat.getTimeInstance(timeFormat, locale);
                        }
                    } else {
                        if (formatChars[1] == 'n') { // date only
                            // bs
                            df = DateFormat.getDateInstance(dateFormat, locale);
                        } else { // time only
                            df = DateFormat.getDateTimeInstance(dateFormat, timeFormat, locale);
                        }
                    }
                } else {
                    df = new SimpleDateFormat(pattern, locale);
                }

                if (!timeZone.equals("server")) {
                    df.setTimeZone(TimeZone.getTimeZone(timeZone));
                }

                format = new Parser(df, pattern);

            } else {
                DecimalFormat nf;
                DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(locale);
                if (pattern == null) {
                    nf = new DecimalFormat();
                    nf.setDecimalFormatSymbols(symbols);
                } else {
                    nf = new DecimalFormat(pattern, symbols);
                }
                nf.setParseBigDecimal(true);
                format = new Parser(nf, pattern);
            }
            timezoneFormats.put(pattern, format);
        }
        return format;
    }

    public String label(String label) {
        return label(label, null, null);
    }

    public String label(String label, String def) {
        return label(label, null, def);
    }

    public String label(String label, Locale locale, String def) {
        ResourceBundle[] bs = _resBnd.get(locale);
        for (ResourceBundle b : bs) {
            if (b.containsKey(label)) {
                return b.getString(label);
            }
        }
        if (def != null) {
            return def;
        }
        return label;
    }

    public String noHtml(String text) {
        return StringEscapeUtils.escapeHtml4(text);
    }

    public String noJson(String text) {
        return StringEscapeUtils.escapeJson(text);
    }

    public String noJs(String text) {
        return StringEscapeUtils.escapeEcmaScript(text);
    }

    public String noJava(String text) {
        return StringEscapeUtils.escapeJava(text);
    }

    public String noCsv(String text) {
        return StringEscapeUtils.escapeCsv(text);
    }

    public String noXml(String text) {
        return StringEscapeUtils.escapeXml11(text);
    }

    public Number toNumber(String source) {
        return ((Number) _getPattern(null, Locale.getDefault(), null)._parse(source));
    }

    public String number(Number source) {
        return _getPattern(null, Locale.getDefault(), null)._format(source);
    }

    public Number toNumber(String source, Locale locale) {
        return ((Number) _getPattern(null, locale, null)._parse(source));
    }

    public Number toNumber(String source, String locale) {
        return ((Number) _getPattern(null, Locale.forLanguageTag(locale), null)._parse(source));
    }

    public String number(Number source, Locale locale) {
        return _getPattern(null, locale, null)._format(source);
    }

    public String number(Number source, String locale) {
        return _getPattern(null, Locale.forLanguageTag(locale), null)._format(source);
    }

    public Long toInteger(String source) {
        return ((Number) _getPattern(null, Locale.US, null)._parse(source)).longValue();
    }

    public String integer(Number source) {
        return _getPattern(null, Locale.US, null)._format(source);
    }

    public Long toInteger(String source, Locale locale) {
        return ((Number) _getPattern(null, locale, null)._parse(source)).longValue();
    }

    public Long toInteger(String source, String locale) {
        return ((Number) _getPattern(null, Locale.forLanguageTag(locale), null)._parse(source)).longValue();
    }

    public String integer(Number source, Locale locale) {
        return _getPattern(null, locale, null)._format(source);
    }

    public String integer(Number source, String locale) {
        return _getPattern(null, Locale.forLanguageTag(locale), null)._format(source);
    }

    public Number toNumberDot(String source) {
        return (Number) _getPattern(null, Locale.US, null)._parse(source);
    }

    public String numberDot(Number source) {
        return _getPattern(null, Locale.US, null)._format(source);
    }

    public Number toNumberComma(String source) {
        return (Number) _getPattern(null, Locale.FRANCE, null)._parse(source);
    }

    public String numberComma(Number source) {
        return _getPattern(null, Locale.FRANCE, null)._format(source);
    }

    public Date toUDate(String source) {
        return (Date) _getPattern("mn", Locale.US, "GMT")._parse(source);
    }

    public String uDate(Date source) {
        return _getPattern("mn", Locale.US, "GMT")._format(source);
    }

    public Date toUDate(String source, String pattern) {
        return (Date) _getPattern(pattern, Locale.US, "GMT")._parse(source);
    }

    public String uDate(Date source, String pattern) {
        return _getPattern(pattern, Locale.US, "GMT")._format(source);
    }

    public Date toUDate(String source, String pattern, Locale locale) {
        return (Date) _getPattern(pattern, locale, "GMT")._parse(source);
    }

    public Date toUDate(String source, String pattern, String locale) {
        return (Date) _getPattern(pattern, Locale.forLanguageTag(locale), "GMT")._parse(source);
    }

    public String uDate(Date source, String pattern, Locale locale) {
        return _getPattern(pattern, locale, "GMT")._format(source);
    }

    public String uDate(Date source, String pattern, String locale) {
        return _getPattern(pattern, Locale.forLanguageTag(locale), "GMT")._format(source);
    }

    public Date toTime(String source, String timeZone) {
        return (Date) _getPattern("mm", Locale.US, timeZone)._parse(source);
    }

    public String time(Date source, String timeZone) {
        return _getPattern("mm", Locale.US, timeZone)._format(source);
    }

    public Date toTime(String source, String pattern, String timeZone) {
        return (Date) _getPattern(pattern, Locale.US, timeZone)._parse(source);
    }

    public String time(Date source, String pattern, String timeZone) {
        return _getPattern(pattern, Locale.US, timeZone)._format(source);
    }

    public Date toTime(String source, String pattern, String timeZone, Locale locale) {
        return (Date) _getPattern(pattern, locale, timeZone)._parse(source);
    }

    public Date toTime(String source, String pattern, String timeZone, String locale) {
        return (Date) _getPattern(pattern, Locale.forLanguageTag(locale), timeZone)._parse(source);
    }

    public String time(Date source, String pattern, String timeZone, String locale) {
        return _getPattern(pattern, Locale.forLanguageTag(locale), timeZone)._format(source);
    }

    public String time(Date source, String pattern, String timeZone, Locale locale) {
        return _getPattern(pattern, locale, timeZone)._format(source);
    }

    private class Parser {

        private final Format _format;
        private final String _pattern;

        private Parser(Format format, String pattern) {
            _format = format;
            _pattern = pattern;
        }

        private String _format(Object object) {
            return _format.format(object);
        }

        private Object _parse(String source) {
            if (source == null || source.trim().length() == 0) {
                return null;
            }
            Object ret;
            try {
                ret = _format.parseObject(source);
            } catch (ParseException e) {
                throw new RuntimeException("The " + _format.getClass().getSimpleName()
                        + (_pattern != null ? " (" + _pattern + ")" : "") + " failed to parse the following: " + source, e);
            }
            return ret;
        }
    }

    public TimeZone getTimezoneByOffset(int offset, Locale country) {

        String countryCode = country.getCountry();

        Map.Entry<Integer, Map<String, List<TimeZone>>> low = _offsetCountryTimezone.floorEntry(offset);
        Map.Entry<Integer, Map<String, List<TimeZone>>> high = _offsetCountryTimezone.ceilingEntry(offset);

        //final Set<TimeZone>timezones;
        final TimeZone randomSuitableTimezone;

        long distanceFromLow;
        long distanceFromHigh;
        List<TimeZone> highTzs;
        List<TimeZone> lowTzs;
        if (low != null) {
            distanceFromLow = Math.abs(offset - low.getKey());
            lowTzs = low.getValue().get(countryCode);
        } else {
            distanceFromLow = Long.MAX_VALUE;
            lowTzs = null;
        }
        if (high != null) {
            distanceFromHigh = Math.abs(offset - high.getKey());
            highTzs = high.getValue().get(countryCode);
        } else {
            distanceFromHigh = Long.MAX_VALUE;
            highTzs = null;
        }

        if (distanceFromLow < 15 * 60 * 1000) { // if it is closer than 15 minutes
            distanceFromLow = 0;
        }
        if (distanceFromHigh < 15 * 60 * 1000) { // if it is closer than 15 minutes
            distanceFromHigh = 0;
        }

        if (distanceFromHigh < distanceFromLow && highTzs != null && highTzs.size() > 0) {
            // do we have our country in high?
            randomSuitableTimezone = highTzs.get(0);
        } else if (distanceFromHigh > distanceFromLow && lowTzs != null && lowTzs.size() > 0) {
            // do we have our country in lo?
            randomSuitableTimezone = lowTzs.get(0);
        } else {

            if (distanceFromHigh == 0 && distanceFromLow == 0) { // both are closer than 15 minutes
                if (lowTzs != null && lowTzs.size() > 0) {
                    if (highTzs != null && highTzs.size() > 0) { // both have our country
                        //take which is even closer
                        distanceFromLow = Math.abs(offset - low.getKey());
                        distanceFromHigh = Math.abs(offset - high.getKey());

                        if (distanceFromHigh < distanceFromLow) {
                            randomSuitableTimezone = highTzs.get(0);
                        } else {
                            randomSuitableTimezone = lowTzs.get(0);
                        }
                    } else {  // only low has our country
                        randomSuitableTimezone = lowTzs.get(0);
                    }
                } else {
                    if (highTzs != null && highTzs.size() > 0) { // only high has our country
                        randomSuitableTimezone = highTzs.get(0);
                    } else {  // none has our country. both are closer thatn 15 minutes. take any country from which is closer
                        distanceFromLow = Math.abs(offset - low.getKey());
                        distanceFromHigh = Math.abs(offset - high.getKey());

                        if (distanceFromHigh < distanceFromLow) {
                            Map<String, List<TimeZone>> tzsm = high.getValue();
                            String anyKey = tzsm.keySet().iterator().next();
                            List<TimeZone> tzs = tzsm.get(anyKey);
                            randomSuitableTimezone = tzs.get(0);
                        } else {
                            Map<String, List<TimeZone>> tzsm = low.getValue();
                            String anyKey = tzsm.keySet().iterator().next();
                            List<TimeZone> tzs = tzsm.get(anyKey);
                            randomSuitableTimezone = tzs.get(0);
                        }
                    }
                }
            } else { // both are too far
                randomSuitableTimezone = null;
            }
        }

        return randomSuitableTimezone;
    }
}
