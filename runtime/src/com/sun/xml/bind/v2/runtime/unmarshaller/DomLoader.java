package com.sun.xml.bind.v2.runtime.unmarshaller;

import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.Result;
import javax.xml.bind.annotation.*;

import com.sun.xml.bind.v2.runtime.JAXBContextImpl;

import org.xml.sax.SAXException;

/**
 * Loads a DOM.
 *
 * @author Kohsuke Kawaguchi
 */
public class DomLoader<ResultT extends Result> extends Loader {

    private final DomHandler<?,ResultT> dom;

    /**
     * Used to capture the state.
     *
     * This instance is created for each unmarshalling episode.
     */
    private final class State {
        /** This handler will receive SAX events. */
        private final TransformerHandler handler = JAXBContextImpl.createTransformerHandler();

        /** {@link #handler} will produce this result. */
        private final ResultT result;

        // nest level of elements.
        int depth = 1;

        public State( UnmarshallingContext context ) throws SAXException {
            result = dom.createUnmarshaller(context);

            handler.setResult(result);

            // emulate the start of documents
            try {
                handler.setDocumentLocator(context.getLocator());
                handler.startDocument();
                declarePrefixes( context, context.getAllDeclaredPrefixes() );
            } catch( SAXException e ) {
                context.handleError(e);
                throw e;
            }
        }

        public Object getElement() {
            return dom.getElement(result);
        }

        private void declarePrefixes( UnmarshallingContext context, String[] prefixes ) throws SAXException {
            for( int i=prefixes.length-1; i>=0; i-- )
                handler.startPrefixMapping(
                    prefixes[i],
                    context.getNamespaceURI(prefixes[i]) );
        }

        private void undeclarePrefixes( String[] prefixes ) throws SAXException {
            for( int i=prefixes.length-1; i>=0; i-- )
                handler.endPrefixMapping( prefixes[i] );
        }
    }

    public DomLoader(DomHandler<?, ResultT> dom) {
        super(true);
        this.dom = dom;
    }

    public void startElement(UnmarshallingContext.State state, EventArg ea) throws SAXException {
        UnmarshallingContext context = state.getContext();
        if (state.target == null)
            state.target = new State(context);

        State s = ((State) state.target);
        try {
            s.declarePrefixes(context, context.getNewlyDeclaredPrefixes());
            s.handler.startElement(ea.uri, ea.local, ea.qname, ea.atts);
        } catch (SAXException e) {
            context.handleError(e);
            throw e;
        }
    }


    public void childElement(UnmarshallingContext.State state, EventArg ea) throws SAXException {
        state.loader = this;
        State s = (State) state.prev.target;
        s.depth++;
        state.target = s;
    }

    public void text(UnmarshallingContext.State state, CharSequence text) throws SAXException {
        try {
            State s = (State) state.target;
            s.handler.characters(text.toString().toCharArray(),0,text.length());
        } catch( SAXException e ) {
            state.getContext().handleError(e);
            throw e;
        }
    }

    public void leaveElement(UnmarshallingContext.State state, EventArg ea) throws SAXException {
        State s = (State) state.target;
        UnmarshallingContext context = state.getContext();

        try {
            s.handler.endElement(ea.uri, ea.local, ea.qname);
            s.undeclarePrefixes(context.getNewlyDeclaredPrefixes());
        } catch( SAXException e ) {
            context.handleError(e);
            throw e;
        }

        if((--s.depth)==0) {
            // emulate the end of the document
            try {
                s.undeclarePrefixes(context.getAllDeclaredPrefixes());
                s.handler.endDocument();
            } catch( SAXException e ) {
                context.handleError(e);
                throw e;
            }

            // we are done
            state.target = s.getElement();
        }
    }

}