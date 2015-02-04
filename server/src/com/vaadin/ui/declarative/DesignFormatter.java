/*
 * Copyright 2000-2014 Vaadin Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.ui.declarative;

import java.io.Serializable;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

import com.vaadin.data.util.converter.Converter;
import com.vaadin.event.ShortcutAction;
import com.vaadin.server.Resource;
import com.vaadin.ui.declarative.converters.DesignDateConverter;
import com.vaadin.ui.declarative.converters.DesignEnumConverter;
import com.vaadin.ui.declarative.converters.DesignFormatConverter;
import com.vaadin.ui.declarative.converters.DesignResourceConverter;
import com.vaadin.ui.declarative.converters.DesignShortcutActionConverter;
import com.vaadin.ui.declarative.converters.DesignTimeZoneConverter;
import com.vaadin.ui.declarative.converters.DesignToStringConverter;

/**
 * Class focused on flexible and consistent formatting and parsing of different
 * values throughout reading and writing {@link Design}. An instance of this
 * class is used by {@link DesignAttributeHandler}.
 * 
 * @since 7.4
 * @author Vaadin Ltd
 */
public class DesignFormatter implements Serializable {

    private final Map<Class<?>, Converter<String, ?>> converterMap = new ConcurrentHashMap<Class<?>, Converter<String, ?>>();
    private final Converter<String, Enum> stringEnumConverter = new DesignEnumConverter();

    /**
     * Creates the formatter with default types already mapped.
     */
    public DesignFormatter() {
        mapDefaultTypes();
    }

    /**
     * Maps default types to their converters.
     * 
     */
    protected void mapDefaultTypes() {
        // numbers use standard toString/valueOf approach
        for (Class<?> c : new Class<?>[] { Integer.class, Byte.class,
                Short.class, Long.class, BigDecimal.class }) {
            DesignToStringConverter<?> conv = new DesignToStringConverter(c);
            converterMap.put(c, conv);
            try {
                converterMap.put((Class<?>) c.getField("TYPE").get(null), conv);
            } catch (Exception e) {
                ; // this will never happen
            }
        }
        // booleans use a bit different converter than the standard one
        // "false" is boolean false, everything else is boolean true
        Converter<String, Boolean> booleanConverter = new Converter<String, Boolean>() {

            @Override
            public Boolean convertToModel(String value,
                    Class<? extends Boolean> targetType, Locale locale)
                    throws Converter.ConversionException {
                return !value.equalsIgnoreCase("false");
            }

            @Override
            public String convertToPresentation(Boolean value,
                    Class<? extends String> targetType, Locale locale)
                    throws Converter.ConversionException {
                return String.valueOf(value.booleanValue());
            }

            @Override
            public Class<Boolean> getModelType() {
                return Boolean.class;
            }

            @Override
            public Class<String> getPresentationType() {
                return String.class;
            }

        };
        converterMap.put(Boolean.class, booleanConverter);
        converterMap.put(boolean.class, booleanConverter);

        // floats and doubles use formatters
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale(
                "en_US"));
        DecimalFormat fmt = new DecimalFormat("0.###", symbols);
        fmt.setGroupingUsed(false);
        converterMap.put(Float.class, new DesignFormatConverter<Float>(
                Float.class, fmt));
        converterMap.put(Float.TYPE, new DesignFormatConverter<Float>(
                Float.class, fmt));
        converterMap.put(Double.class, new DesignFormatConverter<Double>(
                Double.class, fmt));
        converterMap.put(Double.TYPE, new DesignFormatConverter<Double>(
                Double.class, fmt));

        // strings do nothing
        converterMap.put(String.class, new Converter<String, String>() {

            @Override
            public String convertToModel(String value,
                    Class<? extends String> targetType, Locale locale)
                    throws Converter.ConversionException {
                return value;
            }

            @Override
            public String convertToPresentation(String value,
                    Class<? extends String> targetType, Locale locale)
                    throws Converter.ConversionException {
                return value;
            }

            @Override
            public Class<String> getModelType() {
                return String.class;
            }

            @Override
            public Class<String> getPresentationType() {
                return String.class;
            }

        });

        // char takes the first character from the string
        Converter<String, Character> charConverter = new DesignToStringConverter<Character>(
                Character.class) {

            @Override
            public Character convertToModel(String value,
                    Class<? extends Character> targetType, Locale locale)
                    throws Converter.ConversionException {
                return value.charAt(0);
            }

        };
        converterMap.put(Character.class, charConverter);
        converterMap.put(Character.TYPE, charConverter);

        converterMap.put(Date.class, new DesignDateConverter());
        converterMap.put(ShortcutAction.class,
                new DesignShortcutActionConverter());
        converterMap.put(Resource.class, new DesignResourceConverter());
        converterMap.put(TimeZone.class, new DesignTimeZoneConverter());
    }

    /**
     * Adds a converter for a new type.
     * 
     * @param converter
     *            Converter to add.
     */
    protected <T> void addConverter(Converter<String, T> converter) {
        converterMap.put(converter.getModelType(), converter);
    }

    /**
     * Adds a converter for a given type.
     * 
     * @param type
     *            Type to convert to/from.
     * @param converter
     *            Converter.
     */
    protected <T> void addConverter(Class<?> type,
            Converter<String, ?> converter) {
        converterMap.put(type, converter);
    }

    /**
     * Removes the converter for given type, if it was present.
     * 
     * @param type
     *            Type to remove converter for.
     */
    protected void removeConverter(Class<?> type) {
        converterMap.remove(type);
    }

    /**
     * Returns a set of classes that have a converter registered. This is <b>not
     * the same</b> as the list of supported classes - subclasses of classes in
     * this set are also supported.
     * 
     * @return An unmodifiable set of classes that have a converter registered.
     */
    protected Set<Class<?>> getRegisteredClasses() {
        return Collections.unmodifiableSet(converterMap.keySet());
    }

    /**
     * Parses a given string as a value of given type
     * 
     * @param value
     *            String value to convert.
     * @param type
     *            Expected result type.
     * @return String converted to the expected result type using a registered
     *         converter for that type.
     */
    public <T> T parse(String value, Class<? extends T> type) {
        Converter<String, T> converter = findConverterFor(type);
        if (converter != null) {
            return converter.convertToModel(value, type, null);
        } else {
            return null;
        }
    }

    /**
     * Finds a formatter for a given object and attempts to format it.
     * 
     * @param object
     *            Object to format.
     * @return String representation of the object, as returned by the
     *         registered converter.
     */
    public String format(Object object) {
        return format(object, object == null ? Object.class : object.getClass());
    }

    /**
     * Formats an object according to a converter suitable for a given type.
     * 
     * @param object
     *            Object to format.
     * @param type
     *            Type of the object.
     * @return String representation of the object, as returned by the
     *         registered converter.
     */
    public <T> String format(T object, Class<? extends T> type) {
        if (object == null) {
            return null;
        } else {
            return findConverterFor(object.getClass()).convertToPresentation(
                    object, String.class, null);
        }
    }

    /**
     * Checks whether or not a value of a given type can be converted. If a
     * converter for a superclass is found, this will return true.
     * 
     * @param type
     *            Type to check.
     * @return <b>true</b> when either a given type or its supertype has a
     *         converter, <b>false</b> otherwise.
     */
    public boolean canConvert(Class<?> type) {
        return findConverterFor(type) != null;
    }

    /**
     * Finds a converter for a given type. May return a converter for a
     * superclass instead, if one is found and {@code strict} is false.
     * 
     * @param sourceType
     *            Type to find a converter for.
     * @param strict
     *            Whether or not search should be strict. When this is
     *            <b>false</b>, a converter for a superclass of given type may
     *            be returned.
     * @return A valid converter for a given type or its supertype, <b>null</b>
     *         if it was not found.
     */
    @SuppressWarnings("unchecked")
    protected <T> Converter<String, T> findConverterFor(
            Class<? extends T> sourceType, boolean strict) {
        if (sourceType.isEnum()) {
            return (Converter<String, T>) stringEnumConverter;
        } else if (converterMap.containsKey(sourceType)) {
            return ((Converter<String, T>) converterMap.get(sourceType));
        } else if (!strict) {
            for (Class<?> supported : converterMap.keySet()) {
                if (supported.isAssignableFrom(sourceType)) {
                    return ((Converter<String, T>) converterMap.get(supported));
                }
            }
        }
        return null;
    }

    /**
     * Finds a converter for a given type. May return a converter for a
     * superclass instead, if one is found.
     * 
     * @param sourceType
     *            Type to find a converter for.
     * @return A valid converter for a given type or its subtype, <b>null</b> if
     *         it was not found.
     */
    protected <T> Converter<String, T> findConverterFor(
            Class<? extends T> sourceType) {
        return findConverterFor(sourceType, false);
    }

}