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

package com.sun.tools.xjc.generator.bean;

import javax.xml.bind.JAXBContext;

import com.sun.codemodel.JClass;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.fmt.JPropertyFile;
import com.sun.tools.xjc.model.CElementInfo;
import com.sun.tools.xjc.model.Model;
import com.sun.tools.xjc.outline.Aspect;
import com.sun.tools.xjc.runtime.JAXBContextFactory;

/**
 * Generates private ObjectFactory.
 *
 * <p>
 * This class also puts a copy of {@link JAXBContextFactory}
 * to the impl package.
 *
 * @author Kohsuke Kawaguchi
 */
final class PrivateObjectFactoryGenerator extends ObjectFactoryGeneratorImpl {
    public PrivateObjectFactoryGenerator(BeanGenerator outline, Model model, JPackage targetPackage) {
        super(outline, model, targetPackage.subPackage("impl"));

        JPackage implPkg = targetPackage.subPackage("impl");

        // put JAXBContextFactory into the impl package
        JClass factory = outline.generateStaticClass(JAXBContextFactory.class,implPkg);

        // and then put jaxb.properties to point to it
        JPropertyFile jaxbProperties = new JPropertyFile("jaxb.properties");
        targetPackage.addResourceFile(jaxbProperties);
        jaxbProperties.add(
            JAXBContext.JAXB_CONTEXT_FACTORY,
            factory.fullName());
    }

    void populate(CElementInfo ei) {
        populate(ei,Aspect.IMPLEMENTATION,Aspect.IMPLEMENTATION);
    }

    void populate(ClassOutlineImpl cc) {
        populate(cc,cc.implRef);
    }
}
