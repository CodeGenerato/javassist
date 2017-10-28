/*
 * Javassist, a Java-bytecode translator toolkit.
 * Copyright (C) 1999- Shigeru Chiba. All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License.  Alternatively, the contents of this file may be used under
 * the terms of the GNU Lesser General Public License Version 2.1 or later,
 * or the Apache License Version 2.0.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 */

package javassist.util.proxy;

import java.lang.reflect.Method;
import java.io.Serializable;

/**
 * Runtime support routines that the classes generated by ProxyFactory use.
 *
 * @see ProxyFactory
 */
public class RuntimeSupport {
    /**
     * A method handler that only executes a method.
     */
    public static MethodHandler default_interceptor = new DefaultMethodHandler();

    static class DefaultMethodHandler implements MethodHandler, Serializable {
        /** default serialVersionUID */
        private static final long serialVersionUID = 1L;

        public Object invoke(Object self, Method m,
                             Method proceed, Object[] args)
            throws Exception
        {
            return proceed.invoke(self, args);
        }
    };

    /**
     * Finds two methods specified by the parameters and stores them
     * into the given array.
     *
     * @throws RuntimeException     if the methods are not found.
     * @see javassist.util.proxy.ProxyFactory
     */
    public static void find2Methods(Class clazz, String superMethod,
                                    String thisMethod, int index,
                                    String desc, java.lang.reflect.Method[] methods)
    {
        methods[index + 1] = thisMethod == null ? null
                                                : findMethod(clazz, thisMethod, desc);
        methods[index] = findSuperClassMethod(clazz, superMethod, desc);
    }

    /**
     * Finds two methods specified by the parameters and stores them
     * into the given array.
     *
     * <p>Added back for JBoss Seam.  See JASSIST-206.</p>
     *
     * @throws RuntimeException     if the methods are not found.
     * @see javassist.util.proxy.ProxyFactory
     * @deprecated replaced by {@link #find2Methods(Class, String, String, int, String, Method[])}
     */
    public static void find2Methods(Object self, String superMethod,
                                    String thisMethod, int index,
                                    String desc, java.lang.reflect.Method[] methods)
    {
        methods[index + 1] = thisMethod == null ? null
                                                : findMethod(self, thisMethod, desc);
        methods[index] = findSuperMethod(self, superMethod, desc);
    }

    /**
     * Finds a method with the given name and descriptor.
     * It searches only the class of self.
     *
     * <p>Added back for JBoss Seam.  See JASSIST-206.</p>
     *
     * @throws RuntimeException     if the method is not found.
     * @deprecated replaced by {@link #findMethod(Class, String, String)}
     */
    public static Method findMethod(Object self, String name, String desc) {
        Method m = findMethod2(self.getClass(), name, desc);
        if (m == null)
            error(self.getClass(), name, desc);

        return m;
    }

    /**
     * Finds a method with the given name and descriptor.
     * It searches only the class of self.
     *
     * @throws RuntimeException     if the method is not found.
     */
    public static Method findMethod(Class clazz, String name, String desc) {
        Method m = findMethod2(clazz, name, desc);
        if (m == null)
            error(clazz, name, desc);

        return m;
    }

    /**
     * Finds a method that has the given name and descriptor and is declared
     * in the super class.
     *
     * @throws RuntimeException     if the method is not found.
     */
    public static Method findSuperMethod(Object self, String name, String desc) {
    	// for JBoss Seam.  See JASSIST-183.
        Class clazz = self.getClass();
        return findSuperClassMethod(clazz, name, desc);
    }

    /**
     * Finds a method that has the given name and descriptor and is declared
     * in the super class.
     *
     * @throws RuntimeException     if the method is not found.
     */
    public static Method findSuperClassMethod(Class clazz, String name, String desc) {
        Method m = findSuperMethod2(clazz.getSuperclass(), name, desc);
        if (m == null)
            m = searchInterfaces(clazz, name, desc);

        if (m == null)
            error(clazz, name, desc);

        return m;
    }

    private static void error(Class clazz, String name, String desc) {
        throw new RuntimeException("not found " + name + ":" + desc
                + " in " + clazz.getName());
    }

    private static Method findSuperMethod2(Class clazz, String name, String desc) {
        Method m = findMethod2(clazz, name, desc);
        if (m != null)
            return m; 

        Class superClass = clazz.getSuperclass();
        if (superClass != null) {
            m = findSuperMethod2(superClass, name, desc);
            if (m != null)
                return m;
        }

        return searchInterfaces(clazz, name, desc);
    }

    private static Method searchInterfaces(Class clazz, String name, String desc) {
        Method m = null;
        Class[] interfaces = clazz.getInterfaces();
        for (int i = 0; i < interfaces.length; i++) {
            m = findSuperMethod2(interfaces[i], name, desc);
            if (m != null)
                return m;
        }

        return m;
    }

    private static Method findMethod2(Class clazz, String name, String desc) {
        Method[] methods = SecurityActions.getDeclaredMethods(clazz);
        int n = methods.length;
        for (int i = 0; i < n; i++)
            if (methods[i].getName().equals(name)
                && makeDescriptor(methods[i]).equals(desc))
            return methods[i];

        return null;
    }

    /**
     * Makes a descriptor for a given method.
     */
    public static String makeDescriptor(Method m) {
        Class[] params = m.getParameterTypes();
        return makeDescriptor(params, m.getReturnType());
    }

    /**
     * Makes a descriptor for a given method.
     *
     * @param params    parameter types.
     * @param retType   return type.
     */
    public static String makeDescriptor(Class[] params, Class retType) {
        StringBuffer sbuf = new StringBuffer();
        sbuf.append('(');
        for (int i = 0; i < params.length; i++)
            makeDesc(sbuf, params[i]);

        sbuf.append(')');
        if (retType != null)
            makeDesc(sbuf, retType);

        return sbuf.toString();
    }

    /**
     * Makes a descriptor for a given method.
     *
     * @param params    the descriptor of parameter types.
     * @param retType   return type.
     */
    public static String makeDescriptor(String params, Class retType) {
        StringBuffer sbuf = new StringBuffer(params);
        makeDesc(sbuf, retType);
        return sbuf.toString();
    }

    private static void makeDesc(StringBuffer sbuf, Class type) {
        if (type.isArray()) {
            sbuf.append('[');
            makeDesc(sbuf, type.getComponentType());
        }
        else if (type.isPrimitive()) {
            if (type == Void.TYPE)
                sbuf.append('V');
            else if (type == Integer.TYPE)
                sbuf.append('I');
            else if (type == Byte.TYPE)
                sbuf.append('B');
            else if (type == Long.TYPE)
                sbuf.append('J');
            else if (type == Double.TYPE)
                sbuf.append('D');
            else if (type == Float.TYPE)
                sbuf.append('F');
            else if (type == Character.TYPE)
                sbuf.append('C');
            else if (type == Short.TYPE)
                sbuf.append('S');
            else if (type == Boolean.TYPE)
                sbuf.append('Z');
            else
                throw new RuntimeException("bad type: " + type.getName());
        }
        else
            sbuf.append('L').append(type.getName().replace('.', '/'))
                .append(';');
    }

    /**
     * Converts a proxy object to an object that is writable to an
     * object stream.  This method is called by <code>writeReplace()</code>
     * in a proxy class.
     *
     * @since 3.4
     */
    public static SerializedProxy makeSerializedProxy(Object proxy)
        throws java.io.InvalidClassException
    {
        Class clazz = proxy.getClass();

        MethodHandler methodHandler = null;
        if (proxy instanceof ProxyObject)
            methodHandler = ((ProxyObject)proxy).getHandler();
        else if (proxy instanceof Proxy)
            methodHandler = ProxyFactory.getHandler((Proxy)proxy);

        return new SerializedProxy(clazz, ProxyFactory.getFilterSignature(clazz), methodHandler);
    }
}
