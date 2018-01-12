package org.kie.semantics.builder.model;


import org.drools.semantics.builder.model.hierarchy.opt.ConceptStrengthEvaluator;
import org.optaplanner.core.api.domain.entity.PlanningEntity;
import org.optaplanner.core.api.domain.solution.cloner.DeepPlanningClone;
import org.optaplanner.core.api.domain.variable.PlanningVariable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@PlanningEntity
@DeepPlanningClone
public class ConceptImplProxy implements Cloneable {

    private Concept concept;
    private ConceptImplProxy chosenSuper;
    private Map<String, PropertyRelation> chosenProperties;
    private Set<String> neededProperties;
    private Set<ConceptImplProxy> chosenSubs;

    public ConceptImplProxy() {}

    protected ConceptImplProxy( Concept con, ConceptImplProxy sup ) {
        this.chosenSuper = sup;
        this.concept = con;
        this.chosenProperties = new HashMap<>();
        this.neededProperties = new HashSet<>();
        this.chosenSubs = new HashSet<>();

        Set<PropertyRelation> pros = con.getAvailableProperties();
        for ( PropertyRelation pro : pros ) {
            neededProperties.add( pro.getProperty() );
        }
    }

    public ConceptImplProxy( Concept con ) {
        this.concept = con;
        this.chosenProperties = new HashMap<>();
        this.neededProperties = new HashSet<>();
        this.chosenSubs = new HashSet<>();

        Set<PropertyRelation> pros = con.getAvailableProperties();
        for ( PropertyRelation pro : pros ) {
            neededProperties.add( pro.getProperty() );
        }
    }

    protected ConceptImplProxy(ConceptImplProxy con) {
        this.chosenSuper = con.getChosenSuper();
        this.concept = con.getConcept();
        this.chosenProperties = new HashMap<>( con.getChosenProperties() );
        this.neededProperties = con.getNeededProperties();
        this.chosenSubs = new HashSet<>();
    }

    public String getIri() {
        return concept.getIri();
    }

    @PlanningVariable( strengthComparatorClass = ConceptStrengthEvaluator.class, valueRangeProviderRefs = {"cons"})
    @DeepPlanningClone
    public ConceptImplProxy getChosenSuper() {
        return chosenSuper;
    }

    public void setChosenSuper( ConceptImplProxy dom ) {
        this.chosenSuper = dom;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ConceptImplProxy con = (ConceptImplProxy) o;

	    return getIri() != null ? getIri().equals( con.getIri() ) : con.getIri() == null;
    }

    @Override
    public int hashCode() {
        return getIri() != null ? getIri().hashCode() : 0;
    }

    public Concept getConcept() {
        return concept;
    }

    public void setConcept(Concept concept) {
        this.concept = concept;
    }

    @DeepPlanningClone
    public Map<String, PropertyRelation> getChosenProperties() {
        return chosenProperties;
    }

    public void setChosenProperties(Map<String, PropertyRelation> chosenProperties) {
        this.chosenProperties = chosenProperties;
    }

    public Set<ConceptImplProxy> getChosenSubs() {
        return chosenSubs;
    }

    public void setChosenSubs( Set<ConceptImplProxy> chosenSubs ) {
        this.chosenSubs = chosenSubs;
    }

    public void addChosenSub( ConceptImplProxy chosenSub ) {
        this.chosenSubs.add( chosenSub );
    }

    public ConceptImplProxy clone() {
        return new ConceptImplProxy( this );
    }

    @Override
    public String toString() {
        String s = "ConProxy{ iri='" + getIri() + "\' child of " + ( chosenSuper != null ? chosenSuper.getIri() : "N/A" ) + "\n";
        s += "\t\t chosen " + chosenProperties.size() + "\t" + chosenProperties.keySet() + "\n";
        s += "\t\t  avail " + getAvailablePropertiesVirtual().size() + "\t" + getAvailablePropertiesVirtual().keySet() + "\n";
        s += "\t\t needed " + neededProperties.size() + "\t" + neededProperties;
        return s;
    }

    public Map<String, PropertyRelation> getAvailablePropertiesVirtual() {
        Map<String, PropertyRelation> virtual = new HashMap<>( chosenProperties );
        if ( chosenSuper != null && chosenSuper != this ) {
            virtual.putAll( chosenSuper.getAvailablePropertiesVirtual() );
        }
        return virtual;
    }

    public Set<String> getNeededProperties() {
        return neededProperties;
    }


    public boolean validate() {
        return getAvailablePropertiesVirtual().keySet().equals( neededProperties );
    }

}
