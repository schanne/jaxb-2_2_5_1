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

package com.sun.tools.xjc.addon.episode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.tools.xjc.BadCommandLineException;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;
import com.sun.tools.xjc.outline.EnumOutline;
import com.sun.tools.xjc.reader.Const;
import com.sun.xml.txw2.TXW;
import com.sun.xml.txw2.output.StreamSerializer;
import com.sun.xml.xsom.XSAnnotation;
import com.sun.xml.xsom.XSAttGroupDecl;
import com.sun.xml.xsom.XSAttributeDecl;
import com.sun.xml.xsom.XSAttributeUse;
import com.sun.xml.xsom.XSComplexType;
import com.sun.xml.xsom.XSComponent;
import com.sun.xml.xsom.XSContentType;
import com.sun.xml.xsom.XSDeclaration;
import com.sun.xml.xsom.XSElementDecl;
import com.sun.xml.xsom.XSFacet;
import com.sun.xml.xsom.XSIdentityConstraint;
import com.sun.xml.xsom.XSModelGroup;
import com.sun.xml.xsom.XSModelGroupDecl;
import com.sun.xml.xsom.XSNotation;
import com.sun.xml.xsom.XSParticle;
import com.sun.xml.xsom.XSSchema;
import com.sun.xml.xsom.XSSimpleType;
import com.sun.xml.xsom.XSWildcard;
import com.sun.xml.xsom.XSXPath;
import com.sun.xml.xsom.visitor.XSFunction;
import com.sun.xml.bind.v2.schemagen.episode.Bindings;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Creates the episode file,
 *
 * @author Kohsuke Kawaguchi
 * @author Ben Tomasini (ben.tomasini@gmail.com)
 */
public class PluginImpl extends Plugin {

    private File episodeFile;

    public String getOptionName() {
        return "episode";
    }

    public String getUsage() {
        return "  -episode <FILE>    :  generate the episode file for separate compilation";
    }

    public int parseArgument(Options opt, String[] args, int i) throws BadCommandLineException, IOException {
        if(args[i].equals("-episode")) {
            episodeFile = new File(opt.requireArgument("-episode",args,++i));
            return 2;
        }
        return 0;
    }

    /**
     * Capture all the generated classes from global schema components
     * and generate them in an episode file.
     */
    public boolean run(Outline model, Options opt, ErrorHandler errorHandler) throws SAXException {
        try {
            // reorganize qualifying components by their namespaces to
            // generate the list nicely
            Map<XSSchema, List<OutlineAdaptor>> perSchema = new HashMap<XSSchema,List<OutlineAdaptor>>();
            boolean hasComponentInNoNamespace = false;

            // Combine classes and enums into a single list
            List<OutlineAdaptor> outlines = new ArrayList<OutlineAdaptor>();

            for (ClassOutline co : model.getClasses()) {
                XSComponent sc = co.target.getSchemaComponent();
                String fullName = co.implClass.fullName();
                OutlineAdaptor adaptor = new OutlineAdaptor(sc,
                        OutlineAdaptor.OutlineType.CLASS, fullName);
                outlines.add(adaptor);
            }

            for (EnumOutline eo : model.getEnums()) {
                XSComponent sc = eo.target.getSchemaComponent();
                String fullName = eo.clazz.fullName();
                OutlineAdaptor adaptor = new OutlineAdaptor(sc,
                        OutlineAdaptor.OutlineType.ENUM, fullName);
                outlines.add(adaptor);
            }

            for (OutlineAdaptor oa : outlines) {
                XSComponent sc = oa.schemaComponent;

                if (sc == null) continue;
                if (!(sc instanceof XSDeclaration))
                    continue;
                XSDeclaration decl = (XSDeclaration) sc;
                if (decl.isLocal())
                    continue;   // local components cannot be referenced from outside, so no need to list.

                List<OutlineAdaptor> list = perSchema.get(decl.getOwnerSchema());
                if (list == null) {
                    list = new ArrayList<OutlineAdaptor>();
                    perSchema.put(decl.getOwnerSchema(), list);
                }

                list.add(oa);

                if (decl.getTargetNamespace().equals(""))
                    hasComponentInNoNamespace = true;
            }

            OutputStream os = new FileOutputStream(episodeFile);
            Bindings bindings = TXW.create(Bindings.class, new StreamSerializer(os, "UTF-8"));
            if(hasComponentInNoNamespace) // otherwise jaxb binding NS should be the default namespace
                bindings._namespace(Const.JAXB_NSURI,"jaxb");
            else
                bindings._namespace(Const.JAXB_NSURI,"");
            bindings.version("2.1");
            bindings._comment("\n\n"+opt.getPrologComment()+"\n  ");

            // generate listing per schema
            for (Map.Entry<XSSchema,List<OutlineAdaptor>> e : perSchema.entrySet()) {
                Bindings group = bindings.bindings();
                String tns = e.getKey().getTargetNamespace();
                if(!tns.equals(""))
                    group._namespace(tns,"tns");

                group.scd("x-schema::"+(tns.equals("")?"":"tns"));
                group.schemaBindings().map(false);

                for (OutlineAdaptor oa : e.getValue()) {
                    Bindings child = group.bindings();
                    oa.buildBindings(child);
                }
                group.commit(true);
            }

            bindings.commit();

            return true;
        } catch (IOException e) {
            errorHandler.error(new SAXParseException("Failed to write to "+episodeFile,null,e));
            return false;
        }
    }

    /**
     * Computes SCD.
     * This is fairly limited as JAXB can only map a certain kind of components to classes.
     */
    private static final XSFunction<String> SCD = new XSFunction<String>() {
        private String name(XSDeclaration decl) {
            if(decl.getTargetNamespace().equals(""))
                return decl.getName();
            else
                return "tns:"+decl.getName();
        }

        public String complexType(XSComplexType type) {
            return "~"+name(type);
        }

        public String simpleType(XSSimpleType simpleType) {
            return "~"+name(simpleType);
        }

        public String elementDecl(XSElementDecl decl) {
            return name(decl);
        }

        // the rest is doing nothing
        public String annotation(XSAnnotation ann) {
            throw new UnsupportedOperationException();
        }

        public String attGroupDecl(XSAttGroupDecl decl) {
            throw new UnsupportedOperationException();
        }

        public String attributeDecl(XSAttributeDecl decl) {
            throw new UnsupportedOperationException();
        }

        public String attributeUse(XSAttributeUse use) {
            throw new UnsupportedOperationException();
        }

        public String schema(XSSchema schema) {
            throw new UnsupportedOperationException();
        }

        public String facet(XSFacet facet) {
            throw new UnsupportedOperationException();
        }

        public String notation(XSNotation notation) {
            throw new UnsupportedOperationException();
        }

        public String identityConstraint(XSIdentityConstraint decl) {
            throw new UnsupportedOperationException();
        }

        public String xpath(XSXPath xpath) {
            throw new UnsupportedOperationException();
        }

        public String particle(XSParticle particle) {
            throw new UnsupportedOperationException();
        }

        public String empty(XSContentType empty) {
            throw new UnsupportedOperationException();
        }

        public String wildcard(XSWildcard wc) {
            throw new UnsupportedOperationException();
        }

        public String modelGroupDecl(XSModelGroupDecl decl) {
            throw new UnsupportedOperationException();
        }

        public String modelGroup(XSModelGroup group) {
            throw new UnsupportedOperationException();
        }
    };

    private final static class OutlineAdaptor {

        private enum OutlineType {

            CLASS(new BindingsBuilder() {
                public void build(OutlineAdaptor adaptor, Bindings bindings) {
                    bindings.klass().ref(adaptor.implName);

                }
            }),
            ENUM(new BindingsBuilder() {
                public void build(OutlineAdaptor adaptor, Bindings bindings) {
                    bindings.typesafeEnumClass().ref(adaptor.implName);
                }
            });

            private final BindingsBuilder bindingsBuilder;

            private OutlineType(BindingsBuilder bindingsBuilder) {
                this.bindingsBuilder = bindingsBuilder;
            }

            private interface BindingsBuilder {
                void build(OutlineAdaptor adaptor, Bindings bindings);
            }

        };

        private final XSComponent schemaComponent;
        private final OutlineType outlineType;
        private final String implName;

        public OutlineAdaptor(XSComponent schemaComponent, OutlineType outlineType,
                              String implName) {
            this.schemaComponent = schemaComponent;
            this.outlineType = outlineType;
            this.implName = implName;
        }

        private void buildBindings(Bindings bindings) {
            bindings.scd(schemaComponent.apply(SCD));
            outlineType.bindingsBuilder.build(this, bindings);
        }
    }
}
