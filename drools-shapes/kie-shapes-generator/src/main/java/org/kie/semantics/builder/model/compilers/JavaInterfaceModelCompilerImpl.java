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

import org.kie.semantics.builder.DLTemplateManager;
import org.kie.semantics.builder.model.CompiledOntoModel;
import org.kie.semantics.builder.model.Concept;
import org.drools.semantics.builder.model.JavaInterfaceModelImpl;
import org.drools.semantics.builder.model.ModelFactory;
import org.drools.semantics.builder.model.OntoModel;
import org.drools.semantics.utils.NamespaceUtils;
import org.mvel2.templates.CompiledTemplate;
import org.mvel2.templates.TemplateRegistry;
import org.mvel2.templates.TemplateRuntime;

import java.util.Map;

public class JavaInterfaceModelCompilerImpl extends ModelCompilerImpl implements JavaInterfaceModelCompiler {


	private TemplateRegistry registry = DLTemplateManager.getDataModelRegistry(ModelFactory.CompileTarget.JAVA);


    public void setModel(OntoModel model) {
        this.model = (CompiledOntoModel) ModelFactory.newModel( ModelFactory.CompileTarget.JAVA, model );
    }

    public void compile( Concept con, Object context, Map<String, Object> params ) {

        if ( "Thing".equals( con.getName() ) && NamespaceUtils.compareNamespaces("http://www.w3.org/2002/07/owl", con.getNamespace()) ) {
            return;
        }
        if ( con.isResolved() ) {
            return;
        }

        if ( ( model.getMode() == OntoModel.Mode.NONE || model.getMode() == OntoModel.Mode.DATABASE ) && con.isAbstrakt() ) {
            return;
        }

	    String templateName = "TraitInterface.template";
	    CompiledTemplate template = registry.getNamedTemplate( templateName );

        String name = con.getFullyQualifiedName();

        getModel().addTrait( name, new JavaInterfaceModelImpl.InterfaceHolder(
                TemplateRuntime.execute( template, context, params ).toString().trim(),
                con.getPackage() ) );

    }


}
