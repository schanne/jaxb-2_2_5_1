/*
 * Copyright 2004 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.sun.tools.xjc;

import java.io.IOException;
import java.io.StringReader;

import com.sun.codemodel.JCodeModel;
import com.sun.tools.xjc.model.Model;
import com.sun.tools.xjc.reader.Const;
import com.sun.tools.xjc.reader.ExtensionBindingChecker;
import com.sun.tools.xjc.reader.dtd.TDTDReader;
import com.sun.tools.xjc.reader.internalizer.DOMForest;
import com.sun.tools.xjc.reader.internalizer.DOMForestScanner;
import com.sun.tools.xjc.reader.internalizer.InternalizationLogic;
import com.sun.tools.xjc.reader.relaxng.RELAXNGCompiler;
import com.sun.tools.xjc.reader.relaxng.RELAXNGInternalizationLogic;
import com.sun.tools.xjc.reader.xmlschema.BGMBuilder;
import com.sun.tools.xjc.reader.xmlschema.bindinfo.AnnotationParserFactoryImpl;
import com.sun.tools.xjc.reader.xmlschema.parser.CustomizationContextChecker;
import com.sun.tools.xjc.reader.xmlschema.parser.IncorrectNamespaceURIChecker;
import com.sun.tools.xjc.reader.xmlschema.parser.SchemaConstraintChecker;
import com.sun.tools.xjc.reader.xmlschema.parser.XMLSchemaInternalizationLogic;
import com.sun.tools.xjc.util.ErrorReceiverFilter;
import com.sun.xml.bind.v2.TODO;
import com.sun.xml.bind.v2.WellKnownNamespace;
import com.sun.xml.xsom.XSSchemaSet;
import com.sun.xml.xsom.parser.XMLParser;
import com.sun.xml.xsom.parser.XSOMParser;

import org.kohsuke.rngom.ast.builder.SchemaBuilder;
import org.kohsuke.rngom.ast.util.CheckingSchemaBuilder;
import org.kohsuke.rngom.digested.DPattern;
import org.kohsuke.rngom.digested.DSchemaBuilderImpl;
import org.kohsuke.rngom.parse.IllegalSchemaException;
import org.kohsuke.rngom.parse.Parseable;
import org.kohsuke.rngom.parse.compact.CompactParseable;
import org.kohsuke.rngom.parse.xml.SAXParseable;
import org.kohsuke.rngom.xml.sax.XMLReaderCreator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.ContentHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLFilter;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLFilterImpl;

/**
 * Builds a {@link Model} object.
 * 
 * This is an utility class that makes it easy to load a grammar object
 * from various sources.
 * 
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public final class ModelLoader {
    
    private final Options opt;
    private final ErrorReceiverFilter errorReceiver;
    private final JCodeModel codeModel;
    
    /**
     * A convenience method to load schemas into a BGM.
     */
    public static Model load( Options opt, JCodeModel codeModel, ErrorReceiver er ) throws IOException {
        return new ModelLoader(opt,codeModel,er).load();
    }
    
    
    public ModelLoader(Options _opt, JCodeModel _codeModel, ErrorReceiver er) {
        this.opt = _opt;
        this.codeModel = _codeModel;
        this.errorReceiver = new ErrorReceiverFilter(er);
    }

    private Model load() throws IOException {
        Model grammar;

        if(!sanityCheck())
            return null;
        
        
        try {
            switch (opt.getSchemaLanguage()) {
            case DTD :
                // TODO: make sure that bindFiles,size()<=1
                InputSource bindFile = null;
                if (opt.getBindFiles().length > 0)
                    bindFile = opt.getBindFiles()[0];
                // if there is no binding file, make a dummy one.
                if (bindFile == null) {
                    // if no binding information is specified, provide a default
                    bindFile =
                        new InputSource(
                            new StringReader(
                                "<?xml version='1.0'?><xml-java-binding-schema><options package='"
                                    + (opt.defaultPackage==null?"generated":opt.defaultPackage)
                                    + "'/></xml-java-binding-schema>"));
                }

                checkTooManySchemaErrors();
                grammar = loadDTD(opt.getGrammars()[0], bindFile );
                break;

            case RELAXNG :
                checkTooManySchemaErrors();
                grammar = loadRELAXNG();
                break;

            case RELAXNG_COMPACT :
                checkTooManySchemaErrors();
                grammar = loadRELAXNGCompact();
                break;

            case WSDL:
                checkTooManySchemaErrors();
                grammar = annotateXMLSchema( loadWSDL() );
                break;

            case XMLSCHEMA:
                grammar = annotateXMLSchema( loadXMLSchema() );
                break;
            
            default :
                throw new AssertionError(); // assertion failed
            }

            if (errorReceiver.hadError())
                grammar = null;
            return grammar;

        } catch (SAXException e) {
            // parsing error in the input document.
            // this error must have been reported to the user vis error handler
            // so don't print it again.
            if (opt.debugMode) {
                // however, a bug in XJC might throw unexpected SAXException.
                // thus when one is debugging, it is useful to print what went
                // wrong.
                if (e.getException() != null)
                    e.getException().printStackTrace();
                else
                    e.printStackTrace();
            }
            return null;
        }
    }



    /**
     * Do some extra checking and return false if the compilation
     * should abort.
     */
    private boolean sanityCheck() {
//        if( opt.getSchemaLanguage()==Language.DTD ) {
//            // DTD compilation requires dom4j. dom4j is not a part of JWSDP,
//            // so let's check the existance of it and if not, ask the user
//            // to download it manually
//            try {
//                new org.dom4j.DocumentFactory();
//            } catch( NoClassDefFoundError e ) {
//                errorReceiver.error(null,Messages.format(Messages.MISSING_DOM4J));
//                return false;
//            }
//        }
        if( opt.getSchemaLanguage()==Language.XMLSCHEMA ) {
            Language guess = opt.guessSchemaLanguage();
            
            String[] msg = null;
            switch(guess) {
            case DTD:
                msg = new String[]{"DTD","-dtd"};
                break;
            case RELAXNG:
                msg = new String[]{"RELAX NG","-relaxng"};
                break;
            case RELAXNG_COMPACT:
                msg = new String[]{"RELAX NG compact syntax","-relaxng-compact"};
                break;
            }
            if( msg!=null )
                errorReceiver.warning( null,
                    Messages.format(
                    Messages.EXPERIMENTAL_LANGUAGE_WARNING,
                    msg[0], msg[1] ));
        }
        return true;
    }


    /**
     * {@link XMLParser} implementation that reads from {@link DOMForest}
     * instead of its original source.
     * 
     * <p>
     * This parser will parse a DOM forest as:
     * DOMForestParser -->
     *   ExtensionBindingChecker -->
     *     ProhibitedFeatureFilter -->
     *       XSOMParser
     */
    private class XMLSchemaForestParser implements XMLParser {
        private final XMLParser forestParser;
        
        private XMLSchemaForestParser(DOMForest forest) {
            super();
            forestParser = forest.createParser();
        }
        
        public void parse(InputSource source, ContentHandler handler,
            ErrorHandler errorHandler, EntityResolver entityResolver ) throws SAXException, IOException {
            // set up the chain of handlers.
            handler = wrapBy( new ExtensionBindingChecker(WellKnownNamespace.XML_SCHEMA,opt,errorReceiver), handler );
            handler = wrapBy( new IncorrectNamespaceURIChecker(errorReceiver), handler );
            handler = wrapBy( new CustomizationContextChecker(errorReceiver), handler );
//          handler = wrapBy( new VersionChecker(controller), handler );
            
            forestParser.parse( source, handler, errorHandler, entityResolver );
        }
        /**
         * Wraps the specified content handler by a filter.
         * It is little awkward to use a helper implementation class like XMLFilterImpl
         * as the method parameter, but this simplifies the code.
         */
        private ContentHandler wrapBy( XMLFilterImpl filter, ContentHandler handler ) {
            filter.setContentHandler(handler);
            return filter;
        }
    }
    




    private void checkTooManySchemaErrors() {
        if( opt.getGrammars().length!=1 )
            errorReceiver.error(null,Messages.format(Messages.ERR_TOO_MANY_SCHEMA));
    }
    
    /**
     * Parses a DTD file into an annotated grammar.
     * 
     * @param   source
     *      DTD file
     * @param   bindFile
     *      External binding file.
     */
    private Model loadDTD( InputSource source, InputSource bindFile) {

        // parse the schema as a DTD.
        return TDTDReader.parse(
            source,
            bindFile,
            errorReceiver,
            opt);
    }

    /**
     * Builds DOMForest and performs the internalization.
     */
    public DOMForest buildDOMForest( InternalizationLogic logic ) 
        throws SAXException, IOException {
    
        // parse into DOM forest
        DOMForest forest = new DOMForest(logic);
        
        forest.setErrorHandler(errorReceiver);
        if(opt.entityResolver!=null)
        forest.setEntityResolver(opt.entityResolver);
        
        // parse source grammars
        for (InputSource value : opt.getGrammars())
            forest.parse(value, true);
        
        // parse external binding files
        for (InputSource value : opt.getBindFiles()) {
            Element root = forest.parse(value, true).getDocumentElement();
            // TODO: it somehow doesn't feel right to do a validation in the Driver class.
            // think about moving it to somewhere else.
            if (!root.getNamespaceURI().equals(Const.JAXB_NSURI)
                    || !root.getLocalName().equals("bindings"))
                errorReceiver.error(new SAXParseException(Messages.format(Messages.ERR_NOT_A_BINDING_FILE,
                        root.getNamespaceURI(),
                        root.getLocalName()),
                        null,
                        value.getSystemId(),
                        -1, -1));
        }

        forest.transform();
        
        return forest;
    }
    
    /**
     * Parses a set of XML Schema files into an annotated grammar.
     */
    private XSSchemaSet loadXMLSchema()
        throws SAXException, IOException {
        
        if( opt.strictCheck && !SchemaConstraintChecker.check(opt.getGrammars(),errorReceiver,opt.entityResolver)) {
            // schema error. error should have been reported
            return null;
        }

        DOMForest forest = buildDOMForest( new XMLSchemaInternalizationLogic() );
        
        // load XML Schema from DOMForest instead of loading from its original source.
        // so that we can take external annotations into account.

        return createXSOM(forest);
    }
    
    /**
     * Parses a set of schemas inside a WSDL file.
     * 
     * A WSDL file may contain multiple &lt;xsd:schema> elements.
     */
    private XSSchemaSet loadWSDL()
        throws SAXException, IOException {

        
        // build DOMForest just like we handle XML Schema
        DOMForest forest = buildDOMForest( new XMLSchemaInternalizationLogic() );
        
        DOMForestScanner scanner = new DOMForestScanner(forest);
        
        XSOMParser xsomParser = createXSOMParser( forest );
        
        // find <xsd:schema>s and parse them individually
        InputSource[] grammars = opt.getGrammars();
        Document wsdlDom = forest.get( grammars[0].getSystemId() );
        
        NodeList schemas = wsdlDom.getElementsByTagNameNS(WellKnownNamespace.XML_SCHEMA,"schema");
        for( int i=0; i<schemas.getLength(); i++ )
            scanner.scan( (Element)schemas.item(i), xsomParser.getParserHandler() );
        
        return xsomParser.getResult();
    }
    
    /**
     * Annotates the obtained schema set.
     * 
     * @return
     *      null if an error happens. In that case, the error messages
     *      will be properly reported to the controller by this method.
     */
    public Model annotateXMLSchema(XSSchemaSet xs) {
        if (xs == null)
            return null;

        // TODO: implement this method later
        TODO.prototype("disabling XML Schema support");
        return BGMBuilder.build(xs, codeModel, errorReceiver, opt);
    }
    
    public XSOMParser createXSOMParser(DOMForest forest) {
        // set up other parameters to XSOMParser
        XSOMParser reader = new XSOMParser(new XMLSchemaForestParser(forest));
        reader.setAnnotationParser(new AnnotationParserFactoryImpl(codeModel,opt));
        reader.setErrorHandler(errorReceiver);
        
        return reader;
    }
    
    /**
     * Parses a {@link DOMForest} into a {@link XSSchemaSet}.
     */
    public XSSchemaSet createXSOM(DOMForest forest) throws SAXException {
        // set up other parameters to XSOMParser
        XSOMParser reader = createXSOMParser(forest);

        // re-parse the transformed schemas
        for (String systemId : forest.getRootDocuments()) {
            Document dom = forest.get(systemId);
            if (!dom.getDocumentElement().getNamespaceURI().equals(Const.JAXB_NSURI))
                reader.parse(systemId);
        }
        
        return reader.getResult();
    }
    
    /**
     * Parses a RELAX NG grammar into an annotated grammar.
     */
    private Model loadRELAXNG() throws IOException, SAXException {

        // build DOM forest
        final DOMForest forest = buildDOMForest( new RELAXNGInternalizationLogic() );

        // use JAXP masquerading to validate the input document.
        // DOMForest -> ExtensionBindingChecker -> RNGOM

        XMLReaderCreator xrc = new XMLReaderCreator() {
            public XMLReader createXMLReader() {

                // foreset parser cannot change the receivers while it's working,
                // so we need to have one XMLFilter that works as a buffer
                XMLFilter buffer = new XMLFilterImpl() {
                    public void parse(InputSource source) throws IOException, SAXException {
                        forest.createParser().parse( source, this, this, this );
                    }
                };

                XMLFilter f = new ExtensionBindingChecker(Const.RELAXNG_URI,opt,errorReceiver);
                f.setParent(buffer);

                f.setEntityResolver(opt.entityResolver);

                return f;
            }
        };

        Parseable p = new SAXParseable( opt.getGrammars()[0], errorReceiver, xrc );

        return loadRELAXNG(p);

    }

    /**
     * Loads RELAX NG compact syntax
     */
    private Model loadRELAXNGCompact() {
        if(opt.getBindFiles().length>0)
            errorReceiver.error(new SAXParseException(
                Messages.format(Messages.ERR_BINDING_FILE_NOT_SUPPORTED_FOR_RNC),null));

        // TODO: entity resolver?
        Parseable p = new CompactParseable( opt.getGrammars()[0], errorReceiver );

        return loadRELAXNG(p);

    }

    /**
     * Common part between the XML syntax and the compact syntax.
     */
    private Model loadRELAXNG(Parseable p) {
        SchemaBuilder sb = new CheckingSchemaBuilder(new DSchemaBuilderImpl(),errorReceiver);

        try {
            DPattern out = (DPattern)p.parse(sb);
            return RELAXNGCompiler.build(out,codeModel,opt);
        } catch (IllegalSchemaException e) {
            errorReceiver.error(e.getMessage(),e);
            return null;
        }
    }
}
