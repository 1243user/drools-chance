/*
 * Copyright 2011 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.shapes;

import com.sun.codemodel.JDefinedClass;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.model.CPluginCustomization;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;
import org.kie.semantics.builder.model.compilers.SemanticXSDModelCompilerImpl;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AccessorPlugin extends Plugin {

    public static String uri = "http://jboss.org/drools/drools-chance/drools-shapes/plugins/accessor";
    public static String uri2 = "http://jboss.org/drools/drools-chance/drools-shapes/plugins/metadata";


    private static final String inferredGetterTempl = "inferredGetter";


    public String getOptionName() {
        return "Xsem-accessors";
    }

    public List<String> getCustomizationURIs() {
        return Collections.singletonList( uri );
    }

    public boolean isCustomizationTagName( String nsUri, String localName ) {
        return nsUri.equals( uri ) && ( localName.equals( "property" ) || localName.equals( "accessor" ) || localName.equals( "accessors" )
                || localName.equals( "chain" ) || localName.equals( "link" ) )
        ||
                nsUri.equals( uri2 ) && (
                        localName.equals( "type" )
                                || localName.equals( "property")
                                || localName.equals( "restriction" ) );
    }

    public String getUsage() {
        return "  -Xsem-accessors";
    }

    @Override
    public boolean run(Outline outline, Options opt, ErrorHandler errorHandler) throws SAXException {
        for (ClassOutline co : outline.getClasses() ) {

            processBaseAccessors( co );
            
            processInferredAccessors( co );

        }

        return true;
    }


    private void processInferredAccessors( ClassOutline co ) {
        CPluginCustomization c = co.target.getCustomizations().find( uri2, "type" );
        if( c == null ) {
            return;
        }


        Element metaType = c.element;
        NodeList propList = metaType.getChildNodes();
        
        List<PropEssentials> props = new ArrayList<>();
        for ( int j = 0; j < propList.getLength(); j++ ) {
            Node n = propList.item( j );            
            if ( n instanceof Element ) {
                Element el = (Element) n;
//                if ( ! el.getTagName().equals( "property" ) ) { continue; }
                String name = el.getAttribute( "name" );
                String type = el.getAttribute( "type" );
                String simp = el.getAttribute( "simple" );
                String enhancedNames = el.getAttribute( "enhancedNames" );
                PropEssentials prop = new PropEssentials( name, type, Boolean.valueOf( simp ), Boolean.valueOf( enhancedNames ) );

                for ( int k = 0; k < el.getChildNodes().getLength(); k++ ) {
                    Node m = el.getChildNodes().item( k );
                    if ( m instanceof Element ) {
                        Element em = (Element) m;
//                        if ( ! em.getTagName().equals( "restriction" ) ) { continue; }
                        prop.addSub( new Sub( em.getAttribute( "name" ), em.getAttribute( "type" ), Boolean.valueOf( em.getAttribute( "single" ) ) ) );
                    }
                }

                props.add( prop );
            }
        }


        
        HashMap<String, Object> map = new HashMap<>();
        
        for ( PropEssentials prop : props ) {
            map.put( "name", prop.getName() );
            map.put( "type", prop.getType() );
            map.put( "subs", prop.getSubs() );
            map.put( "simple", prop.isSimple() );
            map.put( "enhancedNames", prop.isEnhancedName() );
            String meta = SemanticXSDModelCompilerImpl.getTemplatedCode( inferredGetterTempl, map );
            co.implClass.direct( meta );
        }


        c.markAsAcknowledged();

    }

    private void processBaseAccessors( ClassOutline co ) {

        CPluginCustomization c = co.target.getCustomizations().find( uri, "accessors" );
        if ( c== null ) {
            return;
        }

        Element accessors = c.element;

        NodeList props = accessors.getElementsByTagNameNS( uri, "property" );
        for ( int j = 0; j < props.getLength(); j++ ) {
            compileProperty( co.implClass, (Element) props.item( j ) );
        }

        createCycleManager(co.implClass);


        NodeList axx = accessors.getElementsByTagNameNS( uri, "accessor" );
        for ( int j = 0; j < axx.getLength(); j++ ) {
            compileAccessor( co.implClass, (Element) axx.item( j ) );
        }

        c.markAsAcknowledged();
    }
    
    
    private void createCycleManager(JDefinedClass implClass) {
        Map<String,Object> vars = new HashMap<>();
                vars.put( "name", implClass.name().substring(0, implClass.name().length() - 4) ); //remove "Impl"
        String method = SemanticXSDModelCompilerImpl.getTemplatedCode("onCycleDetected", vars);

                implClass.direct(method);
    }


    private void compileProperty( JDefinedClass implClass, Element item ) {
        Map<String,Object> vars = new HashMap<>();
        boolean simple = Boolean.valueOf( item.getAttribute( "simple" ) );

        vars.put( "name", item.getAttribute( "name" ) );
        vars.put( "type", item.getAttribute( "type" ) );
        vars.put( "max", null );
        vars.put( "simple", simple );
        vars.put( "primitive", Boolean.valueOf( item.getAttribute( "primitive" ) ) );
        vars.put( "enhancedNames", Boolean.valueOf( item.getAttribute( "enhancedNames" ) ) );

        String code;

        code = SemanticXSDModelCompilerImpl.getTemplatedCode("baseGetterSetter", vars);
        implClass.direct( code );

        code = SemanticXSDModelCompilerImpl.getTemplatedCode("baseAddRemove", vars);
        implClass.direct( code );

        code = SemanticXSDModelCompilerImpl.getTemplatedCode("genericAdd", vars);
        implClass.direct( code );
    }

    private void compileAccessor( JDefinedClass implClass, Element item ) {
        Map<String,Object> vars = new HashMap<>();
        boolean simple = Boolean.valueOf( item.getAttribute( "simple" ) );
        boolean primitive = Boolean.valueOf( item.getAttribute( "primitive" ) );         vars.put( "name", item.getAttribute( "name" ) );
        String type = item.getAttribute( "type" );
        String baseType = item.getAttribute( "baseType" );
        // boolean restricted = ! baseType.equals( type );

        vars.put( "type", type );
        vars.put( "primitive", primitive );
        vars.put( "min", Integer.valueOf( item.getAttribute( "min" ) ) );
        vars.put( "max", item.getAttribute( "max" ).equals( "null") ? null : Integer.valueOf( item.getAttribute( "max" ) ) );
        vars.put( "inherited", Boolean.valueOf( item.getAttribute( "inherited" ) ) );
        vars.put( "base", item.getAttribute( "base" ) );
        vars.put( "baseType", baseType );
        vars.put( "simple", simple );
        vars.put( "enhancedNames", Boolean.valueOf( item.getAttribute( "enhancedNames" ) ) );

        Set<List<Link>> chains = new HashSet<>();
        NodeList chainsList = item.getElementsByTagNameNS( uri, "chain");
        for ( int j = 0; j < chainsList.getLength(); j++ ) {
            List<Link> chain = new ArrayList<>();
            NodeList linkList = ((Element) chainsList.item( j )).getElementsByTagNameNS( uri, "link" );
            for ( int k = 0; k < linkList.getLength(); k++ ) {
                Element link = (Element) linkList.item( k );
                chain.add( new Link( link.getTextContent(), link.getAttribute( "type" ) ) );
            }
            chains.add( chain );
        }

        vars.put( "chains", chains );
        vars.put( "isChained", chainsList.getLength() > 0 );


        String code;

        if ( ! ( simple && primitive ) ) {
            if ( chainsList.getLength() == 0 ) {
                if ( ! type.equals( baseType ) || (! simple) ) {
                    code = SemanticXSDModelCompilerImpl.getTemplatedCode("restrictedGetterSetter", vars);
                    implClass.direct( code );
                }
            } else {
                code = SemanticXSDModelCompilerImpl.getTemplatedCode("chainGetter", vars);
                implClass.direct( code );
            }

            if ( chainsList.getLength() == 0 && ! type.equals( baseType ) || (! simple) ) {
                code = SemanticXSDModelCompilerImpl.getTemplatedCode("restrictedAddRemove", vars);
                implClass.direct( code );
            }
        }

    }

    
    
    public static class Sub {
        private String name;
        private String type;
        private boolean single;

        private Sub(String name, String type, boolean single) {
            this.name = name;
            this.single = single;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isSingle() {
            return single;
        }

        public void setSingle(boolean single) {
            this.single = single;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        @Override
        public boolean equals(Object o) {
	        if ( this == o ) return true;
	        if ( o == null || getClass() != o.getClass() ) return false;

	        Sub sub = ( Sub ) o;

	        return single == sub.single && ( name != null ? name.equals( sub.name ) : sub.name == null ) && ( type != null ? type.equals( sub.type ) : sub.type == null );
        }

        @Override
        public int hashCode() {
            int result = name != null ? name.hashCode() : 0;
            result = 31 * result + (single ? 1 : 0);
            result = 31 * result + type.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "Sub{" +
                    "name='" + name + '\'' +
                    ", single=" + single +
                    '}';
        }
    }
    
    public static class PropEssentials {
        private String name;
        private String type;
        private boolean simple;
        private boolean enhancedName;
        
        private List<Sub> subs;

        public PropEssentials(String name, String type, boolean simple, boolean enhancedName) {
            this.name = name;
            this.type = type;
            this.simple = simple;
            this.subs = new ArrayList<>();
            this.enhancedName = enhancedName;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public List<Sub> getSubs() {
            return subs;
        }

        public void addSub( Sub sub ) {
            subs.add( sub );
        }

        public boolean isSimple() {
            return simple;
        }

        public void setSimple(boolean simple) {
            this.simple = simple;
        }

        public boolean isEnhancedName() {
            return enhancedName;
        }

        public void setEnhancedName( boolean enhancedName ) {
            this.enhancedName = enhancedName;
        }

        @Override
        public boolean equals(Object o) {
	        if ( this == o ) return true;
	        if ( o == null || getClass() != o.getClass() ) return false;

	        PropEssentials that = ( PropEssentials ) o;

	        return ( name != null ? name.equals( that.name ) : that.name == null ) && ( type != null ? type.equals( that.type ) : that.type == null );
        }

        @Override
        public int hashCode() {
            int result = name != null ? name.hashCode() : 0;
            result = 31 * result + (type != null ? type.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "PropEssentials{" +
                    "name='" + name + '\'' +
                    ", type='" + type + '\'' +
                    ", subs=" + subs +
                    '}';
        }
    }

    public static class Link {
        private String name;
        private String type;

        public Link(String name, String type) {
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

}
