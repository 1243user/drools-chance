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

import org.kie.api.io.Resource;
import org.kie.internal.io.ResourceFactory;
import org.kie.semantics.builder.DLFactory;
import org.kie.semantics.builder.DLFactoryBuilder;
import org.kie.semantics.builder.DLFactoryConfiguration;
import org.drools.semantics.builder.model.ModelFactory;
import org.drools.semantics.builder.model.OntoModel;
import org.kie.semantics.builder.model.SemanticXSDModel;
import org.drools.semantics.builder.model.compilers.ModelCompiler;
import org.drools.semantics.builder.model.compilers.ModelCompilerFactory;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;


/**
 * This is a sample class to launch a rule.
 */
public class DL_6_MixedNamespacesTest {

    protected DLFactory factory = DLFactoryBuilder.newDLFactoryInstance();
    



    @Test
    public void testMixedExternal() {
        String source1 = "ontologies/mixed/appendix.owl";
        String source2 = "ontologies/mixed/mixed.owl";
        Resource res = ResourceFactory.newClassPathResource(source1);
        Resource res2 = ResourceFactory.newClassPathResource(source2);

        OntoModel results = factory.buildModel( "mixedModel", new Resource[] { res, res2 }, DLFactoryConfiguration.newConfiguration( OntoModel.Mode.HIERARCHY ) );

        System.out.println( results );

        ModelCompiler compiler =  ModelCompilerFactory.newModelCompiler( ModelFactory.CompileTarget.XSDX );
	    assert compiler != null;
	    SemanticXSDModel xsdModel = (SemanticXSDModel) compiler.compile( results );

        assertEquals( 5, xsdModel.getNamespaces().size() );
        assertEquals( 7, xsdModel.getConcepts().size() );

    }



}