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

package org.kie.semantics.lang.dl;

import com.clarkparsia.empire.Empire;
import com.clarkparsia.empire.EmpireOptions;
import com.clarkparsia.empire.config.ConfigKeys;
import com.clarkparsia.empire.config.EmpireConfiguration;
import com.clarkparsia.empire.jena.JenaEmpireModule;
import com.clarkparsia.empire.sesame.OpenRdfEmpireModule;
import com.clarkparsia.empire.util.DefaultEmpireModule;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import org.apache.commons.lang3.StringUtils;
import org.drools.shapes.OntoModelCompiler;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.kie.api.builder.Message;
import org.kie.api.builder.Results;
import org.kie.api.definition.KiePackage;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.utils.KieHelper;
import org.kie.semantics.UIdAble;
import org.kie.semantics.builder.DLFactory;
import org.kie.semantics.builder.DLFactoryBuilder;
import org.kie.semantics.builder.DLFactoryConfiguration;
import org.kie.semantics.builder.model.OntoModel;
import org.mvel2.MVEL;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import thewebsemantic.binding.Jenabean;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.spi.PersistenceProvider;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.PropertyException;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


/**
 * This is a sample class to launch a rule.
 */
@SuppressWarnings("restriction")
public class DL_9_CompilationTest {

    protected DLFactory factory = DLFactoryBuilder.newDLFactoryInstance();


    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    protected OntoModelCompiler compiler;


    @Test
    public void testDiamondOptimizedHierarchyCompilation() {

        OntoModel results = factory.buildModel( "diamond",
                                                ResourceFactory.newClassPathResource( "ontologies/diamondProp2.manchester.owl" ),
                                                DLFactoryConfiguration.newConfiguration( OntoModel.Mode.OPTIMIZED ) );

        assertTrue( results.isHierarchyConsistent() );

        compiler = new OntoModelCompiler( results, folder.getRoot(), false );

        // ****** Stream the java interfaces
        boolean javaOut = compiler.streamJavaInterfaces( true );

        assertTrue( javaOut );

        // ****** Stream the XSDs, the JaxB customizations abd the persistence configuration
        boolean xsdOut = compiler.streamXSDsWithBindings();

        assertTrue( xsdOut );
        File f = new File( compiler.getMetaInfDir().getPath() + File.separator + results.getDefaultPackage() + ".xsd" );
        try {
            Document dox = parseXML( f, true );

            NodeList types = dox.getElementsByTagName( "xsd:complexType" );
            assertEquals( 10, types.getLength() );

            NodeList elements = dox.getElementsByTagName( "xsd:element" );
            assertEquals( 12 + 14, elements.getLength() );
        } catch ( Exception e ) {
            fail( e.getMessage() );
        }

        showDirContent( folder );

        File b = new File( compiler.getMetaInfDir().getPath()+ File.separator + results.getDefaultPackage() + ".xjb" );
        try {
            Document dox = parseXML( b, false );

            NodeList types = dox.getElementsByTagName( "bindings" );
            assertEquals( 28, types.getLength() );
        } catch ( Exception e ) {
            fail( e.getMessage() );
        }


        showDirContent( folder );

        // ****** Generate sources
        boolean mojo = compiler.mojo(  Arrays.asList(
                "-extension",
                "-Xjaxbindex",
                "-Xannotate",
                "-Xinheritance",
                "-XtoString",
                "-Xcopyable",
                "-Xmergeable",
                "-Xvalue-constructor",
                "-Xfluent-api",
                "-Xkey-equality",
                "-Xsem-accessors",
                "-Xdefault-constructor",
                "-Xmetadata",
                "-Xinject-code"),
                OntoModelCompiler.MOJO_VARIANTS.JPA2 );

        assertTrue( mojo );

        File klass = new File( compiler.getXjcDir().getPath()
                + File.separator
                + results.getDefaultPackage().replace(".", File.separator)
                + File.separator
                + "BottomImpl.java" );
        printSourceFile( klass, System.out );

        File intfx = new File( compiler.getJavaDir().getPath()
                + File.separator
                + results.getDefaultPackage().replace(".", File.separator)
                + File.separator
                + "C0.java" );
        printSourceFile( intfx, System.out );

        showDirContent( folder );

        // ****** Do compile sources
        List<Diagnostic<? extends JavaFileObject>> diagnostics = compiler.doCompile();

        boolean success = true;
        for ( Diagnostic diag : diagnostics ) {
            System.out.println( "ERROR : " + diag );
            if ( diag.getKind() == Diagnostic.Kind.ERROR ) {
                success = false;
            }
        }
        assertTrue( success );



        showDirContent( folder );


        try {
            parseXML( new File( compiler.getBinDir().getPath() + "/META-INF/" + "persistence.xml" ), true );
        } catch ( Exception e ) {
            e.printStackTrace();
        }

        try {
            ClassLoader urlKL = new URLClassLoader(
                    new URL[] { compiler.getBinDir().toURI().toURL() },
                    Thread.currentThread().getContextClassLoader()
            );

            testPersistenceWithInstance( urlKL, "org.jboss.drools.semantics.diamond2.Bottom", results.getName() );

        } catch ( Exception e ) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            fail( e.getMessage() );
        }


    }


    @Test
    public void testFunctionalDataPropertyGeneration() {

        OntoModel results = factory.buildModel( "simple",
                                                ResourceFactory.newClassPathResource( "ontologies/simpleFunctionalDataP.owl" ),
                                                DLFactoryConfiguration.newConfiguration( OntoModel.Mode.FLAT     ) );

        assertTrue( results.isHierarchyConsistent() );

        compiler = new OntoModelCompiler( results, folder.getRoot(), false );

        // ****** Stream the java interfaces
        boolean javaOut = compiler.streamJavaInterfaces( true );

        assertTrue( javaOut );

        // ****** Stream the XSDs, the JaxB customizations abd the persistence configuration
        boolean xsdOut = compiler.streamXSDsWithBindings();

        assertTrue( xsdOut );

        showDirContent( folder );

        // ****** Generate sources
        boolean mojo = compiler.mojo(  Arrays.asList(
                "-extension",
                "-Xjaxbindex",
                "-Xannotate",
                "-Xinheritance",
                "-XtoString",
                "-Xcopyable",
                "-Xmergeable",
                "-Xvalue-constructor",
                "-Xfluent-api",
                "-Xkey-equality",
                "-Xsem-accessors",
                "-Xdefault-constructor",
                "-Xmetadata",
                "-Xinject-code"),
                OntoModelCompiler.MOJO_VARIANTS.JPA2 );

        assertTrue( mojo );

        File klass = new File( compiler.getXjcDir().getPath()
                + File.separator
                + results.getDefaultPackage().replace(".", File.separator)
                + File.separator
                + "KlassImpl.java" );
        printSourceFile( klass, System.out );

        System.out.println( "-----------------------------" );
        System.out.println( "-----------------------------" );

        File klass2 = new File( compiler.getJavaDir().getPath()
                + File.separator
                + results.getDefaultPackage().replace(".", File.separator)
                + File.separator
                + "package-info.java" );
        printSourceFile( klass2, System.out );

	    System.out.println( "-----------------------------" );
	    System.out.println( "-----------------------------" );

	    showDirContent( folder );

        List<Diagnostic<? extends JavaFileObject>> diagnostics = compiler.doCompile();

        boolean success = true;
        for ( Diagnostic diag : diagnostics ) {
            System.out.println( diag.getKind() + " : " + diag );
            if ( diag.getKind() == Diagnostic.Kind.ERROR ) {
                success = false;
            }
        }
        assertTrue( success );

        try {
            ClassLoader urlKL = new URLClassLoader(
                    new URL[] { compiler.getBinDir().toURI().toURL() },
                    Thread.currentThread().getContextClassLoader()
            );

            Class<?> i = urlKL.loadClass( "org.test.Klass" );
            assertNotNull( i );
            assertEquals( String.class, i.getMethod( "getProp" ).getReturnType() );

            Class<?> k = urlKL.loadClass( "org.test.KlassImpl" );
            assertNotNull( k );
            assertEquals( String.class, k.getMethod( "getProp" ).getReturnType() );
        } catch ( Exception e ) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            fail( e.getMessage() );
        }
    }

    @Test
    public void testMetadataMetaclassGeneration() throws ParserConfigurationException, TransformerException, SAXException, IOException {

        OntoModel results = factory.buildModel( "simple",
                                                ResourceFactory.newClassPathResource( "ontologies/testMetadata.owl" ),
                                                DLFactoryConfiguration.newConfiguration( OntoModel.Mode.FLAT     ) );

        assertTrue( results.isHierarchyConsistent() );

        compiler = new OntoModelCompiler( results, folder.getRoot(), false );

        // ****** Stream the java interfaces
        boolean javaOut = compiler.streamJavaInterfaces( true );

        assertTrue( javaOut );

        // ****** Stream the XSDs, the JaxB customizations abd the persistence configuration
        boolean xsdOut = compiler.streamXSDsWithBindings();

        assertTrue( xsdOut );

        boolean metaOut = compiler.streamMetaclasses( true );
        assertTrue( metaOut );

        showDirContent( folder );

        // ****** Generate sources
        boolean mojo = compiler.mojo(  Arrays.asList(
                "-extension",
                "-Xjaxbindex",
                "-Xannotate",
                "-Xinheritance",
                "-XtoString",
                "-Xcopyable",
                "-Xmergeable",
                "-Xvalue-constructor",
                "-Xfluent-api",
                "-Xkey-equality",
                "-Xsem-accessors",
                "-Xdefault-constructor",
                "-Xmetadata",
                "-Xmetaclass",
                "-Xinject-code"),
                OntoModelCompiler.MOJO_VARIANTS.JPA2 );

        assertTrue( mojo );

        File klass = new File( compiler.getXjcDir().getPath()
                + File.separator
                + results.getDefaultPackage().replace(".", File.separator)
                + File.separator
                + "KlassImpl.java" );
        printSourceFile( klass, System.out );

        showDirContent( folder );

        List<Diagnostic<? extends JavaFileObject>> diagnostics = compiler.doCompile();

        boolean success = true;
        for ( Diagnostic diag : diagnostics ) {
            System.out.println( "ERROR : " + diag );
            if ( diag.getKind() == Diagnostic.Kind.ERROR ) {
                success = false;
            }
        }
        assertTrue( success );

        checkMetaClass();

    }

	private void checkMetaClass() {
		try {
			ClassLoader urlKL = new URLClassLoader(
					new URL[] { compiler.getBinDir().toURI().toURL() },
					Thread.currentThread().getContextClassLoader()
			);

			Class<?> k = urlKL.loadClass( "org.test.KlassImpl" );
			Object i = k.newInstance();
			MVEL.eval( "_.modify().prop( 'itsme' ).call()", i );

			assertEquals( "itsme", k.getMethod( "getProp" ).invoke( i ) );

		} catch ( Exception e ) {
			e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
			fail( e.getMessage() );
		}
	}

	@Test
    public void testMetadataMetaclassGenerationWithHierarchy() {

        OntoModel results = factory.buildModel( "simpleHier",
                                                ResourceFactory.newClassPathResource( "ontologies/testMetadataSimpleHierarchy.owl" ),
                                                DLFactoryConfiguration.newConfiguration( OntoModel.Mode.HIERARCHY ) );

        assertTrue( results.isHierarchyConsistent() );

        compiler = new OntoModelCompiler( results, folder.getRoot(), false );

        // ****** Stream the java interfaces
        boolean javaOut = compiler.streamJavaInterfaces( true );

        assertTrue( javaOut );

        // ****** Stream the XSDs, the JaxB customizations abd the persistence configuration
        boolean xsdOut = compiler.streamXSDsWithBindings();
        assertTrue( xsdOut );

        // ****** Stream the XSDs, the JaxB customizations abd the persistence configuration
        boolean metaOut = compiler.streamMetaclasses( true );
        assertTrue( metaOut );

        showDirContent( folder );

        // ****** Generate sources
        boolean mojo = compiler.mojo(  Arrays.asList(
                "-extension",
                "-Xjaxbindex",
                "-Xannotate",
                "-Xinheritance",
                "-XtoString",
                "-Xcopyable",
                "-Xmergeable",
                "-Xvalue-constructor",
                "-Xfluent-api",
                "-Xkey-equality",
                "-Xsem-accessors",
                "-Xdefault-constructor",
                "-Xmetadata",
                "-Xmetaclass",
                "-Xinject-code"),
                OntoModelCompiler.MOJO_VARIANTS.JPA2 );

        assertTrue( mojo );

        showDirContent( folder );

		System.out.println( "-----------------------------" );

		File schema = new File( compiler.getMetaInfDir().getPath()
				                        + File.separator
				                        + "com.foo.xsd" );
		printSourceFile( schema, System.out );

		System.out.println( "-----------------------------" );


		List<Diagnostic<? extends JavaFileObject>> diagnostics = compiler.doCompile();

        boolean success = true;
        for ( Diagnostic diag : diagnostics ) {
            System.out.println( "ERROR : " + diag );
            if ( diag.getKind() == Diagnostic.Kind.ERROR ) {
                success = false;
            }
        }
        assertTrue( success );

        checkMetaClass();

    }

    @Test
    public void testMetaclassWithCrossAttributes() {

        OntoModel results = factory.buildModel( "crossAttributes",
                                                ResourceFactory.newClassPathResource( "ontologies/crossAttributes.owl" ),
                                                DLFactoryConfiguration.newConfiguration( OntoModel.Mode.HIERARCHY ) );

        assertTrue( results.isHierarchyConsistent() );

        compiler = new OntoModelCompiler( results, folder.getRoot(), false );

        // ****** Stream the java interfaces
        boolean javaOut = compiler.streamJavaInterfaces( true );

        assertTrue( javaOut );

        // ****** Stream the XSDs, the JaxB customizations abd the persistence configuration
        boolean metaOut = compiler.streamMetaclasses( false );
        assertTrue( metaOut );

        showDirContent( folder );

        List<Diagnostic<? extends JavaFileObject>> diagnostics = compiler.doCompile();

        boolean success = true;
        for ( Diagnostic diag : diagnostics ) {
            System.out.println( "ERROR : " + diag );
            if ( diag.getKind() == Diagnostic.Kind.ERROR ) {
                success = false;
            }
        }
        assertTrue( success );
    }


    @Test
    public void testRecognitionRuleGeneration() {

        OntoModel results = factory.buildModel( "rules",
                                                ResourceFactory.newClassPathResource( "ontologies/testSimpleDefinition.owl" ),
                                                DLFactoryConfiguration.newConfiguration( OntoModel.Mode.HIERARCHY ) );

        assertTrue( results.isHierarchyConsistent() );

        compiler = new OntoModelCompiler( results, folder.getRoot(), false );

        // ****** Stream the java interfaces
        boolean javaOut = compiler.streamJavaInterfaces( true );

        assertTrue( javaOut );

        // ****** Stream the recognition rules
        boolean recogOut = compiler.streamRecognitionRules( new Properties() );

        assertTrue( recogOut );

        showDirContent( folder );

        // ****** Generate sources
        boolean mojo = compiler.mojo( Collections.singletonList( "-extension" ), OntoModelCompiler.MOJO_VARIANTS.JPA2 );

        assertTrue( mojo );

        //showDirContent( folder );

        List<Diagnostic<? extends JavaFileObject>> diagnostics = compiler.doCompile();

        //showDirContent( folder );

        boolean success = true;
        for ( Diagnostic diag : diagnostics ) {
            System.out.println( "ERROR : " + diag );
            if ( diag.getKind() == Diagnostic.Kind.ERROR ) {
                success = false;
            }
        }
        assertTrue( success );

        printSourceFile( new File( compiler.getDrlDir() + "/org/test/rules_recognition.drl" ), System.out );


        try {
            ClassLoader urlKL = new URLClassLoader(
                    new URL[] { compiler.getBinDir().toURI().toURL() },
                    Thread.currentThread().getContextClassLoader()
            );

            KieHelper helper = new KieHelper();
            Results res = helper.setClassLoader( urlKL )
                    .addFromClassPath( "org/test/rules_recognition.drl" )
                    .verify();
            assertEquals( 0, res.getMessages( Message.Level.ERROR ).size() );


            KiePackage pkg =  helper.build().getKiePackage( "org.test" );
	        assertEquals( 2, pkg.getRules().size() );

        } catch ( Exception e ) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            fail( e.getMessage() );
        }

    }

    @Test
    public void testMetaClassGeneration() {

        OntoModel results = factory.buildModel( "rules",
                                                ResourceFactory.newClassPathResource( "ontologies/testSimpleDefinition.owl" ),
                                                DLFactoryConfiguration.newConfiguration( OntoModel.Mode.HIERARCHY ) );
        compiler = new OntoModelCompiler( results, folder.getRoot(), false );

        // ****** Stream the java interfaces
        boolean javaOut = compiler.streamJavaInterfaces( true );
        assertTrue( javaOut );

        // ****** Stream the recognition rules
        boolean recogOut = compiler.streamRecognitionRules( new Properties() );
        assertTrue( recogOut );

        boolean mcOut = compiler.streamMetaclasses( false );
        assertTrue( mcOut );

        showDirContent( folder );

        List<Diagnostic<? extends JavaFileObject>> diagnostics = compiler.doCompile();
        boolean success = true;
        for ( Diagnostic diag : diagnostics ) {
            System.out.println( "ERROR : " + diag );
            if ( diag.getKind() == Diagnostic.Kind.ERROR ) {
                success = false;
            }
        }
        assertTrue( success );

    }

    /*
    private void copy( String file, String pack, String targetFolder, boolean java ) {
        File klass = new File( ( java ? compiler.getJavaDir().getPath() : compiler.getXjcDir().getPath() )
                               + File.separator
                               + pack.replace( ".", File.separator )
                               + File.separator
                               + file + ".java" );
        File f = new File( targetFolder + File.separator + file + ".java" );
        if ( ! f.getParentFile().exists() ) {
            if ( f.getParentFile().mkdirs() ) {
	            try {
		            printSourceFile( klass, new PrintStream( f ) );
	            } catch ( FileNotFoundException fnfe ) {
		            fail( fnfe.getMessage() );
	            }
            };
        }
    }
    */


    @Test
    public void testIncrementalCompilation() {
        try {

            OntoModel diamond = factory.buildModel( "diamondX",
                                                    ResourceFactory.newClassPathResource( "ontologies/diamondProp.manchester.owl" ),
                                                    DLFactoryConfiguration.newConfiguration( OntoModel.Mode.OPTIMIZED ) );

            compiler = new OntoModelCompiler( diamond, folder.getRoot(), false );

            List<Diagnostic<? extends JavaFileObject>> diag1 = compiler.compileOnTheFly( OntoModelCompiler.minimalOptions, OntoModelCompiler.MOJO_VARIANTS.JPA2 );

            for ( Diagnostic<?> dx : diag1 ) {
                System.out.println( dx );
                assertFalse( dx.getKind() == Diagnostic.Kind.ERROR );
            }


            showDirContent( folder );

            ClassLoader urlKL = new URLClassLoader(
                    new URL[] { compiler.getBinDir().toURI().toURL() },
                    Thread.currentThread().getContextClassLoader()
            );


            OntoModel results = factory.buildModel( "diamondInc",
                                                    ResourceFactory.newClassPathResource( "ontologies/dependency.test.owl" ),
                                                    DLFactoryConfiguration.newConfiguration( OntoModel.Mode.OPTIMIZED ),
                                                    urlKL );

            System.out.println( results );

            Class<?> bot = Class.forName( "org.jboss.drools.semantics.diamond.BottomImpl", true, urlKL );
            Class<?> botIF = Class.forName( "org.jboss.drools.semantics.diamond.Bottom", true, urlKL );
            assertNotNull( bot );
            assertNotNull( botIF );
            Object botInst = bot.newInstance();
            assertNotNull( botInst );


            OntoModelCompiler compiler2 = new OntoModelCompiler( results, folder.getRoot(), false );

            compiler2.fixResolvedClasses();

            compiler2.streamJavaInterfaces( false );
            compiler2.streamXSDsWithBindings();

            compiler2.mojo( OntoModelCompiler.defaultOptions, OntoModelCompiler.MOJO_VARIANTS.JPA2, urlKL );

	        showDirContent( folder );

			//printMetaFiles( compiler2.getMetaInfDir() );

	        File unImplBoundLeft = new File( compiler2.getXjcDir().getPath() + File.separator +
			                                         "org.jboss.drools.semantics.diamond".replace( ".", File.separator ) +
			                                         File.separator + "Left.java" );
	        assertFalse( unImplBoundLeft.exists() );
	        File implBoundLeft = new File( compiler2.getXjcDir().getPath() + File.separator +
			                                       "org.jboss.drools.semantics.diamond".replace( ".", File.separator ) +
			                                       File.separator + "LeftImpl.java" );
	        assertTrue( implBoundLeft.exists() );

	        File leftInterface = new File( compiler2.getJavaDir().getPath() + File.separator +
			                                       "org.jboss.drools.semantics.diamond".replace( ".", File.separator ) +
			                                       File.separator + "Left.java" );

	        assertTrue( leftInterface.exists() );

            List<Diagnostic<? extends JavaFileObject>> diagnostics = compiler2.doCompile();


            for ( Diagnostic<?> dx : diagnostics ) {
                System.out.println( dx );
                assertFalse( dx.getKind() == Diagnostic.Kind.ERROR );
            }

            showDirContent( folder );

            Document dox = parseXML( new File( compiler2.getBinDir().getPath() + "/META-INF/persistence.xml" ), false );
                XPath xpath = XPathFactory.newInstance().newXPath();
                XPathExpression expr = xpath.compile( "//persistence-unit/@name" );
            assertEquals( "diamondX", expr.evaluate( dox, XPathConstants.STRING ) );


            File YInterface = new File( compiler2.getJavaDir().getPath() + File.separator +
                    "org.jboss.drools.semantics.diamond".replace( ".", File.separator ) +
                    File.separator + "X.java" );
            assertTrue( YInterface.exists() );


            Class<?> colf = Class.forName( "some.dependency.test.ChildOfLeftImpl", true, urlKL );
            assertNotNull( colf );
            Object colfInst = colf.newInstance();

                    List<String> hierarchy = getHierarchy( colf );
            assertTrue( hierarchy.contains( "some.dependency.test.ChildOfLeftImpl" ) );
            assertTrue( hierarchy.contains( "some.dependency.test.org.jboss.drools.semantics.diamond.LeftImpl" ) );
            assertTrue( hierarchy.contains( "org.jboss.drools.semantics.diamond.LeftImpl" ) );
            assertTrue( hierarchy.contains( "org.jboss.drools.semantics.diamond.C0Impl" ) );
            assertTrue( hierarchy.contains( "org.jboss.drools.semantics.diamond.TopImpl" ) );
            assertTrue( hierarchy.contains( "org.w3._2002._07.owl.ThingImpl" ) );

            Set<String> itfHierarchy = getIFHierarchy( colf );

            System.err.println( itfHierarchy.containsAll( Arrays.asList(
                    "org.jboss.drools.semantics.diamond.C1",
                    "org.jboss.drools.semantics.diamond.C0",
                    "some.dependency.test.org.jboss.drools.semantics.diamond.Left",
                    "some.dependency.test.ChildOfLeft",
                    "org.jboss.drools.semantics.diamond.Left",
                    "org.jboss.drools.semantics.diamond.Top",
                    "com.clarkparsia.empire.EmpireGenerated",
                    "org.w3._2002._07.owl.Thing",
                    "java.io.Serializable",
                    "org.drools.semantics.Thing",
                    "com.clarkparsia.empire.SupportsRdfId" ) ) );

            Method getter1 = colf.getMethod( "getAnotherLeftProp" );
                assertNotNull( getter1 );
            Method getter2 = colf.getMethod( "getImportantProp");
                assertNotNull( getter2 );

            for ( Method m : colf.getMethods() ) {
                if ( m.getName().equals( "addImportantProp" ) ) {
                    m.getName();
                }
            }

            Method adder = colf.getMethod( "addImportantProp", botIF );
                assertNotNull( adder );
            adder.invoke( colfInst, botInst );
            List l = (List) getter2.invoke( colfInst );
            assertEquals( 1, l.size() );



            File off = new File( compiler2.getXjcDir().getPath() + File.separator +
                    "org.jboss.drools.semantics.diamond".replace( ".", File.separator ) +
                    File.separator + "Left_Off.java" );
            assertFalse( off.exists() );


            testPersistenceWithInstance( urlKL, "org.jboss.drools.semantics.diamond.Bottom", diamond.getName() );
            System.out.println(" Done" );

        } catch ( Exception e ) {
            e.printStackTrace();
            fail( e.getMessage() );
        }

    }

	private void printMetaFiles( File META ) throws IOException {
		for ( File binder : META.listFiles( pathname -> (pathname.getName().endsWith( "xjb" ) || pathname.getName().endsWith( "xsd" ))
				&& (! pathname.getName().contains( "global" ) ) && (! pathname.getName().contains( "owlThing" )) )) {
			System.out.println( "Printing" + binder );
			FileInputStream fis = new FileInputStream(binder);
			byte[] data = new byte[(int) binder.length()];
			fis.read(data);
			fis.close();
			System.out.println( new String(data) );
			System.out.println(  );
			System.out.println(  );
		}
	}

	private Set<String> getIFHierarchy( Class<?> x ) {
        Set<String> l = new HashSet<>();
        extractInterfaces( x, l );
        return l;
    }

    private void extractInterfaces( Class<?> x, Set<String> l ) {
        for ( Class<?> itf : x.getInterfaces() ) {
            l.add( itf.getName() );
            extractInterfaces( itf, l );
        }
    }

    private List<String> getHierarchy( Class<?> x ) {
        List<String> l = new LinkedList<>();
        Class<?> k = x;
        while ( ! k.equals( Object.class ) ) {
            l.add( k.getName() );
            k = k.getSuperclass();
        }
        return l;
    }




    private void testPersistenceWithInstance( ClassLoader urlKL, String cName, String pUnit ) {
        Object bot = null;

        try {
            bot = createTestFact( urlKL, cName );
        } catch ( Exception e ) {
            e.printStackTrace();
            fail( e.getMessage() );
        }
        if ( bot != null ) {
            checkJaxbRefresh( bot, urlKL );

            checkEmpireRefresh( bot, urlKL );

            checkSQLRefresh( bot, urlKL, pUnit );

            checkJenaBeansRefresh( bot, urlKL );
        }
    }

    private void checkJenaBeansRefresh( Object bot, ClassLoader urlk ) {
        ClassLoader oldKL = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader( urlk );

            OntModel m = ModelFactory.createOntologyModel();
            Jenabean.instance().bind(m);
            Jenabean.instance().writer().saveDeep(bot);

            m.write(System.err);

            Object x = Jenabean.instance().reader().load( bot.getClass(), ((UIdAble) bot).getDyEntryId() );

            try {
                checkEquality( bot, x );
            } catch ( Exception e ) {
                e.printStackTrace();
                fail( e.getMessage() );
            }

        } finally {
            Thread.currentThread().setContextClassLoader( oldKL );
        }

    }

    private void checkSQLRefresh( Object obj, ClassLoader urlk, String punit ) {
        ClassLoader oldKL = Thread.currentThread().getContextClassLoader();
        EntityManagerFactory emf = null;
        EntityManager em = null;
        try {
            Thread.currentThread().setContextClassLoader( urlk );

            HashMap<String,String> props = new HashMap<>();
            props.put( "hibernate.hbm2ddl.auto", "create-drop" );
            emf = Persistence.createEntityManagerFactory(
                    punit, props
            );

            em = emf.createEntityManager();

            checkJPARefresh( obj,
                    ((UIdAble) obj).getDyEntryId(),
                    em );

        } finally {
            Thread.currentThread().setContextClassLoader( oldKL );
            if ( em != null && em.isOpen() ) {
                em.clear();
                em.close();
            }
            if ( emf != null && emf.isOpen() ) {
                emf.close();
            }
        }

    }


    private void checkEmpireRefresh( Object obj, ClassLoader urlk ) {
        File config = new File( compiler.getBinDir().getPath() + File.separator + OntoModelCompiler.METAINF + File.separator + "empire.configuration.file" );
        File annox = new File( compiler.getBinDir().getPath() + File.separator + OntoModelCompiler.METAINF + File.separator + "empire.annotation.index" );

        ClassLoader oldKL = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader( urlk );


            checkJPARefresh( obj,
                    ((UIdAble) obj).getRdfId(),
                    initEmpireEM(config, annox, obj.getClass().getPackage().getName()));
        } finally {
            Thread.currentThread().setContextClassLoader( oldKL );
        }

    }


    private void checkJPARefresh(  Object obj, Object key, EntityManager em ) {

        persist( obj, em );

        Object obj2 =  refreshOnJPA( obj, key, em );

        System.out.println( obj2 );

        try {
            checkEquality( obj, obj2 );
        } catch ( Exception e ) {
            e.printStackTrace();
            fail( e.getMessage() );
        }

    }

    private void checkEquality(Object obj, Object obj2) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Object a0 = obj.getClass().getMethod( "getC0Prop" ).invoke( obj );
        Object c0 = obj2.getClass().getMethod( "getC0Prop" ).invoke( obj2 );
        assertTrue( c0 instanceof List && ((List) c0).size() == 4 );
        // graph (de)serialization cannot guarantee list order as of Empire 1.0
		assertTrue( ((List<?>) c0).containsAll( (List<?>) a0 ) );

        Object aX = obj.getClass().getMethod( "getObjPropX" ).invoke( obj );
        Object cX = obj2.getClass().getMethod( "getObjPropX" ).invoke( obj2 );
        assertNotNull( cX );
        System.out.println( cX );
        assertTrue( cX.getClass().getName().endsWith( "XImpl" ) );
        assertEquals( aX, cX );

        Object a2 = obj.getClass().getMethod( "getC2Prop" ).invoke( obj );
        Object c2 = obj2.getClass().getMethod( "getC2Prop" ).invoke( obj2 );
        assertTrue( c2 instanceof List
                && ((List) c2).size() == 1
                && ((List) c2).get( 0 ) instanceof String
        );
        assertEquals( a2, c2 );
    }

    private EntityManager initEmpireEM( File config, File annox, String pack ) {
        System.setProperty( "empire.configuration.file", config.getPath() );

	    EmpireConfiguration ecfg = DefaultEmpireModule.readConfiguration();
        ecfg.getGlobalConfig().put( ConfigKeys.ANNOTATION_INDEX, annox.getPath() );
        Empire.init( ecfg, new JenaEmpireModule( ) );
        EmpireOptions.STRICT_MODE = false;

        PersistenceProvider aProvider = Empire.get().persistenceProvider();

        EntityManagerFactory emf = aProvider.createEntityManagerFactory( pack, getMockEMConfigMap() );
	    return emf.createEntityManager();
    }

    private static Map<String, String> getMockEMConfigMap() {
        Map<String, String> map = new HashMap<>();
	    map.put( ConfigKeys.FACTORY, "jena-test");
        return map;
    }

    private void persist( Object o, EntityManager em ) {
        em.getTransaction().begin();
        em.persist( o );
        em.getTransaction().commit();
        em.clear();
    }

    private Object refreshOnJPA( Object o, Object key, EntityManager em ) {
        Object val;
        em.getTransaction().begin();
        val = em.find( o.getClass(), key );
        em.getTransaction().commit();
        return val;
    }




    private Object createTestFact( ClassLoader urlKL, String cName ) throws Exception {
        Class<?> bottom = Class.forName( cName, true, urlKL );
        Class<?> bottomImpl = Class.forName( cName + "Impl", true, urlKL );

        Object bot = bottomImpl.newInstance();
        assertTrue( bottom.isAssignableFrom( bot.getClass() ) );

        assertNotNull( bottomImpl.getMethod( "getObjPropX" ) );
        assertNotNull( bottomImpl.getMethod( "getObjPropXs" ) );

        Object ret = bottomImpl.getMethod( "getObjPropXs" ).invoke( bot );
        assertNotNull( ret );
        assertTrue( ret instanceof List );
        assertEquals( 1, ( (List) ret ).size() );

        bottom.getMethod( "addC1Prop", String.class ).invoke( bot, "helloc1" );
        bottom.getMethod( "addC2Prop", String.class ).invoke( bot, "helloc2" );
        bottom.getMethod( "addC0Prop", String.class ).invoke( bot, "helloc0" );
        bottom.getMethod( "addC0Prop", String.class ).invoke( bot, "helloc0_2" );
        bottom.getMethod( "addC0Prop", String.class ).invoke( bot, "helloc0_3" );
        bottom.getMethod( "addC0Prop", String.class ).invoke( bot, "helloc0_4" );

        return bot;
    }

    private void checkJaxbRefresh( Object obj, ClassLoader urlKL ) {
        try {
            JAXBContext jaxbContext;
            jaxbContext = JAXBContext.newInstance( obj.getClass().getPackage().getName(), urlKL );
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty( Marshaller.JAXB_ENCODING, "UTF-8" );
            marshaller.setProperty( Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE );

            StringWriter sw = new StringWriter();
            marshaller.marshal( obj, sw );

            System.out.println( sw.toString() );

            jaxbContext = JAXBContext.newInstance( obj.getClass().getPackage().getName(), urlKL );
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            Marshaller marshaller2 = jaxbContext.createMarshaller();
            marshaller2.setProperty( Marshaller.JAXB_ENCODING, "UTF-8" );
            marshaller2.setProperty( Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE );

            Object clone = unmarshaller.unmarshal( new StringReader( sw.toString() ) );

            StringWriter sw2 = new StringWriter();
            marshaller2.marshal( clone, sw2 );
            System.err.println( sw2.toString() );

            assertEquals( sw.toString(), sw2.toString() );
        } catch ( PropertyException e ) {
            e.printStackTrace();
            fail( e.getMessage() );
        } catch ( JAXBException e ) {
            fail( e.getMessage() );
        }
    }

    private void printSourceFile( File f, PrintStream out  ) {
        try {
            FileInputStream fis = new FileInputStream( f );
            int n = fis.available();
            byte[] buf = new byte[ n ];
            if ( n == fis.read( buf ) ) {
	            out.println( new String( buf ) );
            }
        } catch ( IOException ioe ) {
            ioe.printStackTrace();
            fail( ioe.getMessage() );
        }
    }

    private Document parseXML( File f, boolean print ) throws IOException, ParserConfigurationException, SAXException, TransformerException {
        InputSource xSource = new InputSource( new FileInputStream( f ) );
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = dbf.newDocumentBuilder();
	    Document dox = builder.parse( xSource );
        if ( print ) {
            streamXML( dox, System.out );
        }
        return dox;
    }

    private void streamXML(Document dox, PrintStream out) throws TransformerException {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.transform( new DOMSource( dox ), new StreamResult( out ) );
    }


    private void showDirContent(TemporaryFolder folder) {
        showDirContent( folder.getRoot(), 0 );
    }

    private void showDirContent( File file, int i ) {
        System.out.println( tab(i) + " " + file.getName() );
        if ( file.isDirectory() ) {
        	File[] files = file.listFiles();
        	if ( files != null ) {
		        for ( File sub : files ) {
			        showDirContent( sub, i + 1 );
		        }
	        }
        }
    }

    private String tab( int n ) {
        return StringUtils.repeat( "\t", n );
    }


}