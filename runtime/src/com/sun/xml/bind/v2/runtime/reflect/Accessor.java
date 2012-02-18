/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.xml.bind.v2.runtime.reflect;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.List;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.adapters.XmlAdapter;

import com.sun.istack.Nullable;
import com.sun.xml.bind.Util;
import com.sun.xml.bind.api.AccessorException;
import com.sun.xml.bind.api.JAXBRIContext;
import com.sun.xml.bind.v2.model.core.Adapter;
import com.sun.xml.bind.v2.model.impl.RuntimeModelBuilder;
import com.sun.xml.bind.v2.model.nav.Navigator;
import com.sun.xml.bind.v2.runtime.JAXBContextImpl;
import com.sun.xml.bind.v2.runtime.reflect.opt.OptimizedAccessorFactory;
import com.sun.xml.bind.v2.runtime.unmarshaller.Loader;
import com.sun.xml.bind.v2.runtime.unmarshaller.Receiver;
import com.sun.xml.bind.v2.runtime.unmarshaller.UnmarshallingContext;
import com.sun.xml.bind.v2.runtime.JAXBContextImpl;
import com.sun.istack.Nullable;

import org.xml.sax.SAXException;

/**
 * Accesses a particular property of a bean.
 * <p/>
 * <p/>
 * This interface encapsulates the access to the actual data store.
 * The intention is to generate implementations for a particular bean
 * and a property to improve the performance.
 * <p/>
 * <p/>
 * Accessor can be used as a receiver. Upon receiving an object
 * it sets that to the field.
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 * @see Accessor.FieldReflection
 * @see TransducedAccessor
 */
public abstract class Accessor<BeanT, ValueT> implements Receiver {

    public final Class<ValueT> valueType;

    public Class<ValueT> getValueType() {
        return valueType;
    }

    protected Accessor(Class<ValueT> valueType) {
        this.valueType = valueType;
    }

    /**
     * Returns the optimized version of the same accessor.
     *
     * @param context The {@link JAXBContextImpl} that owns the whole thing.
     *                (See {@link RuntimeModelBuilder#context}.)
     * @return At least the implementation can return <tt>this</tt>.
     */
    public Accessor<BeanT, ValueT> optimize(@Nullable JAXBContextImpl context) {
        return this;
    }


    /**
     * Gets the value of the property of the given bean object.
     *
     * @param bean must not be null.
     * @throws AccessorException if failed to set a value. For example, the getter method
     *                           may throw an exception.
     * @since 2.0 EA1
     */
    public abstract ValueT get(BeanT bean) throws AccessorException;

    /**
     * Sets the value of the property of the given bean object.
     *
     * @param bean  must not be null.
     * @param value the value to be set. Setting value to null means resetting
     *              to the VM default value (even for primitive properties.)
     * @throws AccessorException if failed to set a value. For example, the setter method
     *                           may throw an exception.
     * @since 2.0 EA1
     */
    public abstract void set(BeanT bean, ValueT value) throws AccessorException;


    /**
     * Sets the value without adapting the value.
     * <p/>
     * This ugly entry point is only used by JAX-WS.
     * See {@link JAXBRIContext#getElementPropertyAccessor}
     */
    public Object getUnadapted(BeanT bean) throws AccessorException {
        return get(bean);
    }

    /**
     * Returns true if this accessor wraps an adapter.
     * <p/>
     * This method needs to be used with care, but it helps some optimization.
     */
    public boolean isAdapted() {
        return false;
    }

    /**
     * Sets the value without adapting the value.
     * <p/>
     * This ugly entry point is only used by JAX-WS.
     * See {@link JAXBRIContext#getElementPropertyAccessor}
     */
    public void setUnadapted(BeanT bean, Object value) throws AccessorException {
        set(bean, (ValueT) value);
    }

    public void receive(UnmarshallingContext.State state, Object o) throws SAXException {
        try {
            set((BeanT) state.target, (ValueT) o);
        } catch (AccessorException e) {
            Loader.handleGenericException(e, true);
        } catch (IllegalAccessError iae) {
            // throw UnmarshalException instead IllegalAccesssError | Issue 475
            Loader.handleGenericError(iae);
        }
    }

    private static List<Class> nonAbstractableClasses = Arrays.asList(new Class[]{
            Object.class,
            java.util.Calendar.class,
            javax.xml.datatype.Duration.class,
            javax.xml.datatype.XMLGregorianCalendar.class,
            java.awt.Image.class,
            javax.activation.DataHandler.class,
            javax.xml.transform.Source.class,
            java.util.Date.class,
            java.io.File.class,
            java.net.URI.class,
            java.net.URL.class,
            Class.class,
            String.class,
            javax.xml.transform.Source.class}
    );

    public boolean isValueTypeAbstractable() {
        return !nonAbstractableClasses.contains(getValueType());
    }

    /**
     * Wraps this  {@link Accessor} into another {@link Accessor}
     * and performs the type adaption as necessary.
     */
    public final <T> Accessor<BeanT, T> adapt(Class<T> targetType, final Class<? extends XmlAdapter<T, ValueT>> adapter) {
        return new AdaptedAccessor<BeanT, ValueT, T>(targetType, this, adapter);
    }

    public final <T> Accessor<BeanT, T> adapt(Adapter<Type, Class> adapter) {
        return new AdaptedAccessor<BeanT, ValueT, T>(
                (Class<T>) Navigator.REFLECTION.erasure(adapter.defaultType),
                this,
                adapter.adapterType);
    }

    /**
     * Flag that will be set to true after issueing a warning
     * about the lack of permission to access non-public fields.
     */
    private static boolean accessWarned = false;


    /**
     * {@link Accessor} that uses Java reflection to access a field.
     */
    public static class FieldReflection<BeanT, ValueT> extends Accessor<BeanT, ValueT> {
        public final Field f;

        private static final Logger logger = Util.getClassLogger();

        public FieldReflection(Field f) {
            this(f, false);
        }

        public FieldReflection(Field f, boolean supressAccessorWarnings) {
            super((Class<ValueT>) f.getType());
            this.f = f;

            int mod = f.getModifiers();
            if (!Modifier.isPublic(mod) || Modifier.isFinal(mod) || !Modifier.isPublic(f.getDeclaringClass().getModifiers())) {
                try {
                    // attempt to make it accessible, but do so in the security context of the calling application.
                    // don't do this in the doPrivilege block, as that would create a security hole for anyone
                    // to make any field accessible.
                    f.setAccessible(true);
                } catch (SecurityException e) {
                    if ((!accessWarned) && (!supressAccessorWarnings)) {
                        // this happens when we don't have enough permission.
                        logger.log(Level.WARNING, Messages.UNABLE_TO_ACCESS_NON_PUBLIC_FIELD.format(
                                f.getDeclaringClass().getName(),
                                f.getName()),
                                e);
                    }
                    accessWarned = true;
                }
            }
        }

        public ValueT get(BeanT bean) {
            try {
                return (ValueT) f.get(bean);
            } catch (IllegalAccessException e) {
                throw new IllegalAccessError(e.getMessage());
            }
        }

        public void set(BeanT bean, ValueT value) {
            try {
                if (value == null)
                    value = (ValueT) uninitializedValues.get(valueType);
                f.set(bean, value);
            } catch (IllegalAccessException e) {
                throw new IllegalAccessError(e.getMessage());
            }
        }

        @Override
        public Accessor<BeanT, ValueT> optimize(JAXBContextImpl context) {
            if (context != null && context.fastBoot)
                // let's not waste time on doing this for the sake of faster boot.
                return this;
            Accessor<BeanT, ValueT> acc = OptimizedAccessorFactory.get(f);
            if (acc != null)
                return acc;
            else
                return this;
        }
    }

    /**
     * Read-only access to {@link Field}. Used to handle a static field.
     */
    public static final class ReadOnlyFieldReflection<BeanT, ValueT> extends FieldReflection<BeanT, ValueT> {
        public ReadOnlyFieldReflection(Field f, boolean supressAccessorWarnings) {
            super(f, supressAccessorWarnings);
        }
        public ReadOnlyFieldReflection(Field f) {
            super(f);
        }

        @Override
        public void set(BeanT bean, ValueT value) {
            // noop
        }

        @Override
        public Accessor<BeanT, ValueT> optimize(JAXBContextImpl context) {
            return this;
        }
    }


    /**
     * {@link Accessor} that uses Java reflection to access a getter and a setter.
     */
    public static class GetterSetterReflection<BeanT, ValueT> extends Accessor<BeanT, ValueT> {
        public final Method getter;
        public final Method setter;

        private static final Logger logger = Util.getClassLogger();

        public GetterSetterReflection(Method getter, Method setter) {
            super(
                    (Class<ValueT>) (getter != null ?
                            getter.getReturnType() :
                            setter.getParameterTypes()[0]));
            this.getter = getter;
            this.setter = setter;

            if (getter != null)
                makeAccessible(getter);
            if (setter != null)
                makeAccessible(setter);
        }

        private void makeAccessible(Method m) {
            if (!Modifier.isPublic(m.getModifiers()) || !Modifier.isPublic(m.getDeclaringClass().getModifiers())) {
                try {
                    m.setAccessible(true);
                } catch (SecurityException e) {
                    if (!accessWarned)
                        // this happens when we don't have enough permission.
                        logger.log(Level.WARNING, Messages.UNABLE_TO_ACCESS_NON_PUBLIC_FIELD.format(
                                m.getDeclaringClass().getName(),
                                m.getName()),
                                e);
                    accessWarned = true;
                }
            }
        }

        public ValueT get(BeanT bean) throws AccessorException {
            try {
                return (ValueT) getter.invoke(bean);
            } catch (IllegalAccessException e) {
                throw new IllegalAccessError(e.getMessage());
            } catch (InvocationTargetException e) {
                throw handleInvocationTargetException(e);
            }
        }

        public void set(BeanT bean, ValueT value) throws AccessorException {
            try {
                if (value == null)
                    value = (ValueT) uninitializedValues.get(valueType);
                setter.invoke(bean, value);
            } catch (IllegalAccessException e) {
                throw new IllegalAccessError(e.getMessage());
            } catch (InvocationTargetException e) {
                throw handleInvocationTargetException(e);
            }
        }

        private AccessorException handleInvocationTargetException(InvocationTargetException e) {
            // don't block a problem in the user code
            Throwable t = e.getTargetException();
            if (t instanceof RuntimeException)
                throw (RuntimeException) t;
            if (t instanceof Error)
                throw (Error) t;

            // otherwise it's a checked exception.
            // I'm not sure how to handle this.
            // we can throw a checked exception from here,
            // but because get/set would be called from so many different places,
            // the handling would be tedious.
            return new AccessorException(t);
        }

        @Override
        public Accessor<BeanT, ValueT> optimize(JAXBContextImpl context) {
            if (getter == null || setter == null)
                // if we aren't complete, OptimizedAccessor won't always work
                return this;
            if (context != null && context.fastBoot)
                // let's not waste time on doing this for the sake of faster boot.
                return this;

            Accessor<BeanT, ValueT> acc = OptimizedAccessorFactory.get(getter, setter);
            if (acc != null)
                return acc;
            else
                return this;
        }
    }

    /**
     * A version of {@link GetterSetterReflection} that doesn't have any setter.
     * <p/>
     * <p/>
     * This provides a user-friendly error message.
     */
    public static class GetterOnlyReflection<BeanT, ValueT> extends GetterSetterReflection<BeanT, ValueT> {
        public GetterOnlyReflection(Method getter) {
            super(getter, null);
        }

        @Override
        public void set(BeanT bean, ValueT value) throws AccessorException {
            throw new AccessorException(Messages.NO_SETTER.format(getter.toString()));
        }
    }

    /**
     * A version of {@link GetterSetterReflection} thaat doesn't have any getter.
     * <p/>
     * <p/>
     * This provides a user-friendly error message.
     */
    public static class SetterOnlyReflection<BeanT, ValueT> extends GetterSetterReflection<BeanT, ValueT> {
        public SetterOnlyReflection(Method setter) {
            super(null, setter);
        }

        @Override
        public ValueT get(BeanT bean) throws AccessorException {
            throw new AccessorException(Messages.NO_GETTER.format(setter.toString()));
        }
    }

    /**
     * Gets the special {@link Accessor} used to recover from errors.
     */
    @SuppressWarnings("unchecked")
    public static <A, B> Accessor<A, B> getErrorInstance() {
        return ERROR;
    }

    private static final Accessor ERROR = new Accessor<Object, Object>(Object.class) {
        public Object get(Object o) {
            return null;
        }

        public void set(Object o, Object o1) {
        }
    };

    /**
     * {@link Accessor} for {@link JAXBElement#getValue()}.
     */
    public static final Accessor<JAXBElement, Object> JAXB_ELEMENT_VALUE = new Accessor<JAXBElement, Object>(Object.class) {
        public Object get(JAXBElement jaxbElement) {
            return jaxbElement.getValue();
        }

        public void set(JAXBElement jaxbElement, Object o) {
            jaxbElement.setValue(o);
        }
    };

    /**
     * Uninitialized map keyed by their classes.
     */
    private static final Map<Class, Object> uninitializedValues = new HashMap<Class, Object>();

    static {
/*
    static byte default_value_byte = 0;
    static boolean default_value_boolean = false;
    static char default_value_char = 0;
    static float default_value_float = 0;
    static double default_value_double = 0;
    static int default_value_int = 0;
    static long default_value_long = 0;
    static short default_value_short = 0;
*/
        uninitializedValues.put(byte.class, Byte.valueOf((byte) 0));
        uninitializedValues.put(boolean.class, false);
        uninitializedValues.put(char.class, Character.valueOf((char) 0));
        uninitializedValues.put(float.class, Float.valueOf(0));
        uninitializedValues.put(double.class, Double.valueOf(0));
        uninitializedValues.put(int.class, Integer.valueOf(0));
        uninitializedValues.put(long.class, Long.valueOf(0));
        uninitializedValues.put(short.class, Short.valueOf((short) 0));
    }

}