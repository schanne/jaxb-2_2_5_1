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

package ae.com.sun.xml.bind.v2.model.impl;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import javax.xml.namespace.QName;

import ae.com.sun.xml.bind.api.AccessorException;
import ae.com.sun.xml.bind.v2.model.annotation.FieldLocatable;
import ae.com.sun.xml.bind.v2.model.annotation.Locatable;
import ae.com.sun.xml.bind.v2.model.runtime.RuntimeEnumLeafInfo;
import ae.com.sun.xml.bind.v2.model.runtime.RuntimeNonElement;
import ae.com.sun.xml.bind.v2.runtime.IllegalAnnotationException;
import ae.com.sun.xml.bind.v2.runtime.Name;
import ae.com.sun.xml.bind.v2.runtime.Transducer;
import ae.com.sun.xml.bind.v2.runtime.XMLSerializer;
import ae.javax.xml.stream.XMLStreamException;

import org.xml.sax.SAXException;

/**
 * @author Kohsuke Kawaguchi
 */
final class RuntimeEnumLeafInfoImpl<T extends Enum<T>,B> extends EnumLeafInfoImpl<Type,Class,Field,Method>
    implements RuntimeEnumLeafInfo, Transducer<T> {

    public Transducer<T> getTransducer() {
        return this;
    }

    /**
     * {@link Transducer} that knows how to convert a lexical value
     * into the Java value that we can handle.
     */
    private final Transducer<B> baseXducer;

    private final Map<B,T> parseMap = new HashMap<B,T>();
    private final Map<T,B> printMap;

    RuntimeEnumLeafInfoImpl(RuntimeModelBuilder builder, Locatable upstream, Class<T> enumType) {
        super(builder,upstream,enumType,enumType);
        this.printMap = new EnumMap<T,B>(enumType);

        baseXducer = ((RuntimeNonElement)baseType).getTransducer();
    }

    @Override
    public RuntimeEnumConstantImpl createEnumConstant(String name, String literal, Field constant, EnumConstantImpl<Type,Class,Field,Method> last) {
        T t;
        try {
            try {
                constant.setAccessible(true);
            } catch (SecurityException e) {
                // in case the constant is already accessible, swallow this error.
                // if the constant is indeed not accessible, we will get IllegalAccessException
                // in the following line, and that is not too late.
            }
            t = (T)constant.get(null);
        } catch (IllegalAccessException e) {
            // impossible, because this is an enum constant
            throw new IllegalAccessError(e.getMessage());
        }

        B b = null;
        try {
            b = baseXducer.parse(literal);
        } catch (Exception e) {
            builder.reportError(new IllegalAnnotationException(
                Messages.INVALID_XML_ENUM_VALUE.format(literal,baseType.getType().toString()), e,
                    new FieldLocatable<Field>(this,constant,nav()) ));
        }

        parseMap.put(b,t);
        printMap.put(t,b);

        return new RuntimeEnumConstantImpl(this, name, literal, last);
    }

    public QName[] getTypeNames() {
        return new QName[]{getTypeName()};
    }

    public boolean isDefault() {
        return false;
    }

    @Override
    public Class getClazz() {
        return clazz;
    }

    public boolean useNamespace() {
        return baseXducer.useNamespace();
    }

    public void declareNamespace(T t, XMLSerializer w) throws AccessorException {
        baseXducer.declareNamespace(printMap.get(t),w);
    }

    public CharSequence print(T t) throws AccessorException {
        return baseXducer.print(printMap.get(t));
    }

    public T parse(CharSequence lexical) throws AccessorException, SAXException {
        // TODO: error handling

        B b = baseXducer.parse(lexical);

        if (tokenStringType) {
            b = (B) ((String)b).trim();
        }

        return parseMap.get(b);
    }

    public void writeText(XMLSerializer w, T t, String fieldName) throws IOException, SAXException, XMLStreamException, AccessorException {
        baseXducer.writeText(w,printMap.get(t),fieldName);
    }

    public void writeLeafElement(XMLSerializer w, Name tagName, T o, String fieldName) throws IOException, SAXException, XMLStreamException, AccessorException {
        baseXducer.writeLeafElement(w,tagName,printMap.get(o),fieldName);
    }

    public QName getTypeName(T instance) {
        return null;
    }
}