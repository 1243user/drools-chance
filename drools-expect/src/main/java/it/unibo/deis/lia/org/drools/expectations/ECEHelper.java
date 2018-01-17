package it.unibo.deis.lia.org.drools.expectations;

import org.drools.compiler.builder.impl.ECE;
import org.drools.compiler.builder.impl.ExpectAssemblerService;
import org.drools.compiler.builder.impl.KnowledgeBuilderImpl;
import org.drools.core.ClockType;
import org.drools.core.impl.InternalKnowledgeBase;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.conf.KieBaseOption;
import org.kie.api.internal.assembler.KieAssemblers;
import org.kie.api.internal.utils.ServiceRegistry;
import org.kie.api.io.Resource;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.conf.ClockTypeOption;
import org.kie.internal.utils.KieHelper;

import java.util.HashSet;
import java.util.Set;

public class ECEHelper extends KieHelper {

    Set<Resource> eceResources = new HashSet<Resource>();



    @Override
    public KieBase build( KieBaseOption... options ) {

	    KieAssemblers assemblers = ServiceRegistry.getInstance().get( KieAssemblers.class );
	    assemblers.getAssemblers().put( ECE.ECE, new ExpectAssemblerService() );

	    addResource( KieServices.Factory.get().getResources().newClassPathResource( "it/unibo/deis/lia/org/drools/expectations/expect_axioms.drl" ), ResourceType.DRL );
	    KieBase kieBase = super.build( options );

        KnowledgeBuilderImpl kbuilder = new KnowledgeBuilderImpl();
        for ( Resource res : eceResources ) {
            kbuilder.add( res, ECE.ECE );
        }

	    (( InternalKnowledgeBase)kieBase).addPackages( kbuilder.getKnowledgePackages() );

        return kieBase;
    }


    public ECEHelper addECEContent( Resource resource ) {
        eceResources.add( resource );
        return this;
    }

    public ECEHelper addECEContent( String content ) {
        eceResources.add( KieServices.Factory.get().getResources().newByteArrayResource( content.getBytes() ) );
        return this;
    }

    public KieSession newECESession( boolean real_time ) {
        KieServices kieServices = KieServices.Factory.get();

        KieBase kieBase = this.build( EventProcessingOption.STREAM );

        KieSessionConfiguration kieSessionConfiguration = kieServices.newKieSessionConfiguration();
        kieSessionConfiguration.setOption( ClockTypeOption.get( real_time ?
                                                                ClockType.REALTIME_CLOCK.toExternalForm() :
                                                                ClockType.PSEUDO_CLOCK.toExternalForm() ) );

        return kieBase.newKieSession( kieSessionConfiguration, null );
    }

}
