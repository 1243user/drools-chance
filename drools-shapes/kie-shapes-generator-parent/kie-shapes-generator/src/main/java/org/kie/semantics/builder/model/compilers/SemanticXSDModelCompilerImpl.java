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

package org.kie.semantics.builder.model.compilers;

import com.clarkparsia.empire.annotation.RdfsClass;
import org.jdom.Namespace;
import org.kie.internal.io.ResourceFactory;
import org.kie.semantics.builder.DLTemplateManager;
import org.kie.semantics.builder.model.CompiledOntoModel;
import org.kie.semantics.builder.model.Concept;
import org.kie.semantics.builder.model.ModelFactory;
import org.kie.semantics.builder.model.OntoModel;
import org.kie.semantics.builder.model.PropertyRelation;
import org.kie.semantics.builder.model.SemanticXSDModel;
import org.kie.semantics.builder.model.XSDModel;
import org.kie.semantics.utils.NameUtils;
import org.kie.semantics.utils.NamespaceUtils;
import org.mvel2.templates.CompiledTemplate;
import org.mvel2.templates.TemplateRegistry;
import org.mvel2.templates.TemplateRuntime;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SemanticXSDModelCompilerImpl extends XSDModelCompilerImpl implements SemanticXSDModelCompiler {


    private TemplateRegistry registry = DLTemplateManager.getDataModelRegistry( ModelFactory.CompileTarget.XSDX );

    protected static final String semGetterTemplateName = "semGetter.drlt";
    protected static final String semSetterTemplateName = "semSetter.drlt";
    protected static final String propChainTemplateName = "propChainGetter.drlt";

    private static CompiledTemplate gettt;
    private static CompiledTemplate settt;
    private static CompiledTemplate chant;


    @Override
    public CompiledOntoModel compile( OntoModel model ) {

        SemanticXSDModel sxsdModel = (SemanticXSDModel) super.compile( model );

        for ( Namespace ns : sxsdModel.getNamespaces() ) {
            sxsdModel.setBindings( ns.getURI(), createBindings( ns.getURI(), sxsdModel ) );
        }

        mergeNamespacedPackageInfo( sxsdModel );

        sxsdModel.setIndex( createIndex( sxsdModel ) );

        sxsdModel.setIndividualFactory( compileIntoFactory( sxsdModel ) );

        sxsdModel.setEmpireConfig( createEmpireConfig( Arrays.asList( sxsdModel.getName() ) ) );

        sxsdModel.setPersistenceXml( createPersistenceXml() );

        return sxsdModel;
    }




    private String createPersistenceXml() {
        try {
            String template = readFile( "persistence-template-hibernate.xml.template" );
            Map<String,Object> vars = new HashMap<>();
	        return TemplateRuntime.eval( template, vars ).toString();
        } catch ( Exception e ) {
            e.printStackTrace();
            return "";
        }
    }

    private String createEmpireConfig( Collection<String> models ) {
        try {
            String template = readFile( "empire.configuration.file.template" );
            Map<String,Object> vars = new HashMap<>();
            vars.put( "index", "empire.annotation.index" );
            vars.put( "models", models );
            vars.put( "datasources", Arrays.asList( "sesame", "jena" ) );
	        return TemplateRuntime.eval( template, vars ).toString();
        } catch ( Exception e ) {
            e.printStackTrace();
            return "";
        }
    }


    private String createIndex( SemanticXSDModel sxsdModel ) {
        try {
            String template = readFile( "empire.annotation.index.template" );
            Map<String,Object> vars = new HashMap<>();
            vars.put( "klasses", sxsdModel.getConcepts() );
	        return TemplateRuntime.eval( template, vars ).toString();
        } catch ( Exception e ) {
            e.printStackTrace();
            return "";
        }

    }

    private String createNamespacedPackageInfo( Namespace ns, String packageName, Map<String,Namespace> prefixMap ) {
        HashMap<String,Object> map = new HashMap<>();
        map.put( "namespace", ns.getURI() );
        map.put( "pack", packageName );
        map.put( "prefixMap", prefixMap );
	    return SemanticXSDModelCompilerImpl.getTemplatedCode( "package-info.java", map, ModelFactory.CompileTarget.JAVA );
    }




    private String compileIntoFactory( SemanticXSDModel sxsdModel ) {
        try {
            Map<String,Object> vars = new HashMap<>();
            vars.put( "package", sxsdModel.getDefaultPackage() );
            vars.put( "individuals", sxsdModel.getIndividuals() );
	        return getTemplatedCode( "IndividualFactory", vars, ModelFactory.CompileTarget.JAVA );
        } catch ( Exception e ) {
            e.printStackTrace();
            return "";
        }
    }


    public ModelFactory.CompileTarget getCompileTarget() {
        return ModelFactory.CompileTarget.XSDX;
    }

    private Document createBindings( String ns, SemanticXSDModel sxsdModel ) {

        if ( "http://www.w3.org/2002/07/owl".equals( ns ) ) {
            return getGlobalBindings();
        }

        String prefix = ((XSDModel) getModel()).mapNamespaceToPrefix( ns );
        try {
            String template = readFile( "bindings.xjb.template" );
            Collection<Concept> cons = filterConceptsByNS( getModel().getConcepts(), ns );
            cons = filterUnneedecConcepts( cons, model );

            Map<String,Object> vars = new HashMap<String,Object>();
            vars.put( "package", NameUtils.namespaceURIToPackage( ns ) );
            vars.put( "namespace", ns );
            vars.put( "concepts", cons );
            vars.put( "flat", getModel().getMode() != OntoModel.Mode.HIERARCHY );
            vars.put( "properties", propCache );
            vars.put( "modelName", getModel().getName() );
            vars.put( "schemaLocation", NameUtils.namespaceURIToPackage( ns ) );
//            vars.put( "schemaLocation", getModel().getName() + ( prefix == null ? "" : ( "_" + prefix ) ) );
            vars.put( "extra_code", prepareCodeExtensions( sxsdModel ) );
            vars.put( "enhancedNames", model.isUseEnhancedNames() );

            String bindings = TemplateRuntime.eval( template, NameUtils.getInstance(), vars ).toString();

            DocumentBuilderFactory df = DocumentBuilderFactory.newInstance();
            Document dox = df.newDocumentBuilder().parse( new InputSource( new StringReader( bindings ) ) );
            dox.normalizeDocument();

            return dox;
        } catch ( Exception ioe ) {
            ioe.printStackTrace();
            return null;
        }

    }

    private Collection<Concept> filterUnneedecConcepts( Collection<Concept> cons, CompiledOntoModel model ) {
        ArrayList<Concept> filtered = new ArrayList<Concept>( cons.size() );
        for ( Concept c : cons ) {
            if ( model.getMode() != OntoModel.Mode.FLAT && model.getMode() != OntoModel.Mode.NONE && model.getMode() != OntoModel.Mode.DATABASE ) {
                filtered.add( c );
            } else {
                if ( ! c.isAbstrakt() ) {
                    filtered.add( c );
                }
            }
        }
        return filtered;
    }


    public void mergeNamespacedPackageInfo( SemanticXSDModel model ) {
        for ( Namespace ns : model.getNamespaces() ) {
            String info = createNamespacedPackageInfo( ns, NameUtils.namespaceURIToPackage( ns.getURI() ), model.getAssignedPrefixes() );
            model.addNamespacedPackageInfo( ns, info );
        }
    }

    public void mergeEmpireConfig( File preexistingEmpireConfig, SemanticXSDModel model )  {
        try {
            FileInputStream bis = new FileInputStream( preexistingEmpireConfig );
            byte[] an = new byte[ bis.available() ];
            bis.read(  an  );


            Set<String> matchSet = new HashSet<>();
            Pattern regex = Pattern.compile( "\\d.name = (.+)-sesame-data-source", Pattern.MULTILINE );

            Matcher regexMatcher;
            regexMatcher = regex.matcher( new String( an ) );
            while ( regexMatcher.find() ) {
                matchSet.add( regexMatcher.group( 1 ) );
            }
            regexMatcher = regex.matcher( model.getEmpireConfig() );
            while ( regexMatcher.find() ) {
                matchSet.add( regexMatcher.group( 1 ) );
            }

            String config = createEmpireConfig( matchSet );

            model.setEmpireConfig( config );

        } catch ( IOException e ) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    public void mergeIndex( File preexistingIndex, SemanticXSDModel model ) {
        FileInputStream bis = null;
        try {
            Properties oldP = new Properties();
                oldP.load( new FileInputStream( preexistingIndex ) );
            Properties newP = new Properties();
                newP.load( new ByteArrayInputStream( model.getIndex().getBytes() ) );

            Set<String> oldTypes = Arrays.stream( oldP.getProperty( RdfsClass.class.getName() )
                                                      .split( "," ) ).collect( Collectors.toSet() );
	        Set<String> newTypes = Arrays.stream( newP.getProperty( RdfsClass.class.getName() )
	                                                  .split( "," ) ).collect( Collectors.toSet() );
			newTypes.addAll( oldTypes );
			newP.setProperty( RdfsClass.class.getName(), String.join( ",", newTypes ) );

	        StringWriter sw = new StringWriter();
			newP.store( sw, "" );

            model.setIndex( sw.toString() );
        } catch ( Exception e ) {
            e.printStackTrace();
        }
   }

//    public void mergePersistenceXml( File preexistingPersistenceXml, SemanticXSDModel model ) {
//        FileInputStream bis = null;
//        try {
//            bis = new FileInputStream( preexistingPersistenceXml );
//            byte[] an = new byte[ bis.available() ];
//            bis.read(  an  );
//
//            String old = new String( an );
//
//            System.out.println( "OLD PERX " + old );
//            System.out.println( "NEW PERX " + model.getPersistenceXml() );
//        } catch ( Exception e ) {
//            e.printStackTrace();
//        }
//
//        throw new UnsupportedOperationException( "TODO" );
//    }









    private Collection<Concept> filterConceptsByNS( List<Concept> concepts, String ns ) {
        List<Concept> filtered = new ArrayList<>();
        for ( Concept con : concepts ) {
            if ( NamespaceUtils.compareNamespaces(con.getNamespace(), ns) ) {
                filtered.add( con );
            }
        }
        return filtered;
    }

    private Map<String,String> prepareCodeExtensions( SemanticXSDModel sxsdModel ) {
        Map<String,String> code = new HashMap<>( sxsdModel.getConcepts().size() );

        List<Concept> filteredConcepts = sxsdModel.getConcepts();

        for ( Concept con : filteredConcepts ) {
            StringBuilder sb = new StringBuilder("");

            Map<String, Set<PropertyRelation>> restrictions = buildRestrictionMatrix( con.getProperties().values() );

            for ( String propKey : con.getProperties().keySet() ) {
                PropertyRelation prop = con.getProperties().get( propKey );
                if ( ! prop.isRestricted() && ! prop.isTransient() ) {


                    String setter = prop.getSetter( model.isUseEnhancedNames() );
                    String adder = prop.getSetter( model.isUseEnhancedNames() ).replace( "set", "add" );
                    String toggler = prop.getSetter( model.isUseEnhancedNames() ).replace( "set", "remove" );

                    Map<String,Object> vars = new HashMap<>();
                    vars.put( "typeName", prop.getTypeName() );
                    vars.put( "isSimpleBoolean", prop.isSimpleBoolean() );
                    vars.put( "isCollection", prop.isCollection() );

                    vars.put( "name", prop.getName() );
                    vars.put( "getter", prop.getGetter( model.isUseEnhancedNames() ) );
                    vars.put( "setter", setter );
                    vars.put( "adder", adder );
                    vars.put( "toggler", toggler );

                    vars.put( "min", prop.getMinCard() );
                    vars.put( "max", prop.getMaxCard() );

                    Set<PropertyRelation> restrs = restrictions.get( prop.getName() );
                    vars.put( "restrictions", restrs != null ? restrs : Collections.emptySet() );


//                    String getProperty = TemplateRuntime.execute( getGetterTemplate(), NameUtils.getInstance(), vars ).toString();
//
//                    sb.append( getProperty );
//
//                    String setProperty = TemplateRuntime.execute( getSetterTemplate(), NameUtils.getInstance(), vars ).toString();
//
//                    sb.append( setProperty );

                } else {
                    if ( prop.isChain() ) {

                        Map<String,Object> vars = new HashMap<>();
                        vars.put( "typeName", prop.getTypeName() );
                        vars.put( "isSimpleBoolean", prop.isSimpleBoolean() );
                        vars.put( "isCollection", prop.isCollection() );
                        vars.put( "getter", prop.getGetter( model.isUseEnhancedNames() ) );
                        vars.put( "min", prop.getMinCard() );
                        vars.put( "max", prop.getMaxCard() );

                        vars.put( "chains", prop.getChains() );

//                        String getChain = TemplateRuntime.execute( getChainTemplate(), NameUtils.getInstance(), vars ).toString();
//
//                        sb.append( getChain );
                    }
                }
            }
            code.put( con.getName(), sb.toString() );
            //code.put( con.getName(), "" );
        }
        return code;
    }

    private Map<String, Set<PropertyRelation>> buildRestrictionMatrix( Collection<PropertyRelation> rels ) {
        Map<String, Set<PropertyRelation>> matrix = new HashMap<String, Set<PropertyRelation>>();
        for ( PropertyRelation rel : rels ) {
            if ( rel.isRestricted() ) {
	            Set<PropertyRelation> others = matrix.computeIfAbsent( rel.getBaseProperty().getName(), k -> new HashSet<>() );
	            others.add( rel );
            }
        }
        return matrix;
    }


    protected CompiledTemplate getChainTemplate() {
        if ( chant == null ) {
            chant = DLTemplateManager.getDataModelRegistry( ModelFactory.CompileTarget.XSDX ).getNamedTemplate( propChainTemplateName );
        }
        return chant;
    }

    protected CompiledTemplate getGetterTemplate() {
        if ( gettt == null ) {
            gettt = DLTemplateManager.getDataModelRegistry( ModelFactory.CompileTarget.XSDX ).getNamedTemplate( semGetterTemplateName );
        }
        return gettt;
    }

    protected CompiledTemplate getSetterTemplate() {
        if ( settt == null ) {
            settt = DLTemplateManager.getDataModelRegistry( ModelFactory.CompileTarget.XSDX ).getNamedTemplate( semSetterTemplateName );
        }
        return settt;
    }

    private static String readFile(String name) throws IOException {
        String fullPath = SemanticXSDModelCompiler.class.getPackage().getName().replace( ".", "/" )
                + "/"
                + name;

        InputStream stream = ResourceFactory.newClassPathResource( fullPath ).getInputStream();
        try {
            byte[] data = new byte[ stream.available() ];
            stream.read(data);
            return new String( data );
        }
        finally {
            stream.close();
        }
    }

    public static String getTemplatedCode( String template, Map<String, Object> vars ) {
        return getTemplatedCode( template, vars, ModelFactory.CompileTarget.XSDX );
    }

    public static String getTemplatedCode( String template, Map<String, Object> vars, ModelFactory.CompileTarget target ) {
        return TemplateRuntime.execute (
                DLTemplateManager.getDataModelRegistry( target  ).getNamedTemplate( template + ".template" ),
                NameUtils.getInstance(),
                vars ).toString();

    }

    public Document getGlobalBindings() {
        InputStream bindingsIS = null;
        try {
            bindingsIS = ResourceFactory.newClassPathResource("org/kie/semantics/builder/model/compilers/global.xjb").getInputStream();
            byte[] data = new byte[ bindingsIS.available() ];
            bindingsIS.read( data );
            DocumentBuilderFactory df = DocumentBuilderFactory.newInstance();
            return df.newDocumentBuilder().parse( new ByteArrayInputStream( data ) );
        } catch ( Exception e ) {
            e.printStackTrace();
            return null;
        }
    }
}


