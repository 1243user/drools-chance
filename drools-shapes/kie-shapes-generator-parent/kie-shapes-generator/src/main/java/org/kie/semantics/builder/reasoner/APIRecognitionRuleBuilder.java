package org.kie.semantics.builder.reasoner;

import org.apache.lucene.index.Term;
import org.drools.compiler.lang.DrlDumper;
import org.drools.compiler.lang.api.AccumulateDescrBuilder;
import org.drools.compiler.lang.api.CEDescrBuilder;
import org.drools.compiler.lang.api.FieldDescrBuilder;
import org.drools.compiler.lang.api.PackageDescrBuilder;
import org.drools.compiler.lang.api.PatternDescrBuilder;
import org.drools.compiler.lang.api.RuleDescrBuilder;
import org.drools.compiler.lang.api.TypeDeclarationDescrBuilder;
import org.drools.compiler.lang.api.impl.PackageDescrBuilderImpl;
import org.drools.compiler.lang.descr.PackageDescr;
import org.drools.compiler.lang.descr.RuleDescr;
import org.drools.core.base.ValueType;
import org.drools.core.base.evaluators.EvaluatorDefinition;
import org.drools.core.base.evaluators.IsAEvaluatorDefinition;
import org.drools.core.base.evaluators.Operator;
import org.drools.core.factmodel.traits.Traitable;
import org.drools.core.spi.Evaluator;
import org.kie.api.builder.Message;
import org.kie.api.builder.Results;
import org.kie.api.definition.type.PropertyReactive;
import org.kie.api.io.ResourceType;
import org.kie.internal.builder.conf.EvaluatorOption;
import org.kie.internal.utils.KieHelper;
import org.kie.semantics.NamedIndividual;
import org.kie.semantics.builder.model.Concept;
import org.kie.semantics.builder.model.OntoModel;
import org.kie.semantics.builder.model.PropertyRelation;
import org.kie.semantics.utils.NameUtils;
import org.kie.shapes.terms.ConceptDescriptor;
import org.kie.shapes.terms.ConceptScheme;
import org.kie.shapes.terms.generator.TerminologyGenerator;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataAllValuesFrom;
import org.semanticweb.owlapi.model.OWLDataCardinalityRestriction;
import org.semanticweb.owlapi.model.OWLDataComplementOf;
import org.semanticweb.owlapi.model.OWLDataExactCardinality;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataIntersectionOf;
import org.semanticweb.owlapi.model.OWLDataMaxCardinality;
import org.semanticweb.owlapi.model.OWLDataMinCardinality;
import org.semanticweb.owlapi.model.OWLDataOneOf;
import org.semanticweb.owlapi.model.OWLDataRange;
import org.semanticweb.owlapi.model.OWLDataSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLDataUnionOf;
import org.semanticweb.owlapi.model.OWLDatatype;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLNaryBooleanClassExpression;
import org.semanticweb.owlapi.model.OWLObjectCardinalityRestriction;
import org.semanticweb.owlapi.model.OWLObjectComplementOf;
import org.semanticweb.owlapi.model.OWLObjectExactCardinality;
import org.semanticweb.owlapi.model.OWLObjectMaxCardinality;
import org.semanticweb.owlapi.model.OWLObjectMinCardinality;
import org.semanticweb.owlapi.model.OWLObjectOneOf;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLObjectUnionOf;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLQuantifiedDataRestriction;
import org.semanticweb.owlapi.model.OWLQuantifiedObjectRestriction;
import org.semanticweb.owlapi.model.OWLQuantifiedRestriction;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.w3._2002._07.owl.Thing;
import thewebsemantic.vocabulary.Skos;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.kie.semantics.util.IRIUtils.fragmentOf;

public class APIRecognitionRuleBuilder {


    private static int counter = 0;

    protected Map<OWLClassExpression,OWLClassExpression> definitions;
    protected DLRecognitionBuildContext context = new DLRecognitionBuildContext();

    protected boolean useTMS                    = true;
    protected boolean usePropertyReactivity     = true;
    protected boolean debug                     = true;
    protected boolean refract                   = true;
    protected boolean useMetaClass              = true;
    protected String rootClass                  = Thing.class.getCanonicalName();

    protected boolean redeclare                 = false;

    protected OntoModel model;
    protected DLogicTransformer transformer;

    public APIRecognitionRuleBuilder( OntoModel model ) {
        this.model = model;
        transformer = new DLogicTransformer( model.getOntology() );
    }


    public String createDRL() {
        return createDRL( true );
    }

    public String createDRL( boolean validate ) {
        definitions = preprocessDefinitions( new DLogicTransformer( model.getOntology() ).getDefinitions() );

        PackageDescr root = visit( definitions, model.getOntology() );

        String drl = generateDRL( root, validate );

        System.out.println( "******************************************************************************" );
        System.out.println( "******************************************************************************" );
        System.out.println( "******************************************************************************" );
        System.out.println( drl );
        System.out.println( "******************************************************************************" );
        System.out.println( "******************************************************************************" );
        System.out.println( "******************************************************************************" );

        return drl;
    }

    protected PackageDescr visit( final Map<OWLClassExpression, OWLClassExpression> definitions, OWLOntology ontology ) {
        PackageDescrBuilder builder = PackageDescrBuilderImpl.newPackage().name( model.getDefaultPackage() );

        buildHeader( builder );

        for ( OWLClassExpression k : definitions.keySet() ) {
            if ( ! k.isAnonymous() ) {
                buildRecognitionRule( k.asOWLClass(), definitions.get( k ), builder.newRule(), ontology );
            }
        }

        return builder.getDescr();
    }

    public RuleDescr createDRL( final OWLClass klass, final OWLClassExpression definition, boolean withHeader, PackageDescrBuilder builder, OWLOntology ontology ) {
        RuleDescrBuilder rule = builder.newRule();
        if ( withHeader ) {
            buildHeader( builder );
        }

        buildRecognitionRule( klass, transformer.toDNF( definition ), rule, ontology != null ? ontology : model.getOntology() );

        return rule.getDescr();
    }

    private void buildHeader( PackageDescrBuilder builder ) {
        builder.newImport().target( NamedIndividual.class.getName() ).end()
                .newImport().target( Traitable.class.getName() ).end()
                .newImport().target( Thing.class.getName() ).end();

        if ( redeclare ) {
            buildDeclarations( builder );
        }
    }

    protected void buildRecognitionRule( OWLClass klass, OWLClassExpression defn, RuleDescrBuilder rule, OWLOntology ontology ) {
        context.clearBindings();
        String fqn = model.getConcept( klass.getIRI().toQuotedString() ).getFullyQualifiedName();

        rule.name( "Recognition " + klass.getIRI().toString() + ( counter++ ) );
        rule.attribute( "no-loop", "" );

        StringBuilder rhs = new StringBuilder();
        if ( debug ) {
            rhs.append( "\t" ).append( "System.out.println( \"Recognized \" + " )
                    .append( context.getScopedIdentifier() )
                    .append( " + \" as an instance of " ).append( fqn )
                    .append( " by rule \"  + " ).append( " drools.getRule().getName() " )
            .append( " ); \n" );
        }

        processOr( rule.lhs(), defn, fqn, null, ontology );

        if ( ! useMetaClass ) {
            rhs.append( "\t" ).append( "don( " )
                    .append( context.getScopedIdentifier() ).append( ", " )
                    .append( fqn ).append( ".class" )
                    .append( useTMS ? ", true " : ", false" ).append( " );" ).append( "\n" );
        } else {
            rhs.append( "\t" ).append( "bolster( " ).append( fqn ).append( "_" )
                    .append( ".don" ).append( NameUtils.capitalize( fragmentOf( klass.getIRI() ) ) ).append( "( " ).append( context.getScopedIdentifier() ).append( " ) );" ).append( "\n" );
        }
        rule.rhs( rhs.toString() );
    }

    protected void processOr( CEDescrBuilder parent, OWLClassExpression expr, String typeName, Object source, OWLOntology ontology ) {
        CEDescrBuilder or = parent.or();
        context.clearBindings();
        if ( expr instanceof OWLObjectUnionOf ) {
            OWLObjectUnionOf union = (OWLObjectUnionOf) expr;
            for ( OWLClassExpression sub : union.getOperandsAsList() ) {
                processAnd( or, sub, typeName, source, ontology );
            }
        } else if ( ! expr.isAnonymous() ) {
            processAnd( or, expr, typeName, source, ontology );
        }
    }

    protected void processAnd( CEDescrBuilder or, OWLClassExpression expr, String typeName, Object source, OWLOntology ontology ) {
        CEDescrBuilder and = or.and();
        processArg( and, expr, typeName, source, ontology );
    }

    protected void processArg( CEDescrBuilder parent, OWLClassExpression arg, String typeName, Object source, OWLOntology ontology ) {

        PatternDescrBuilder pattern = parent.pattern();

        pattern.type( rootClass ).constraint( context.getScopedIdentifier() + " := core " );

        if ( source != null ) {
            pattern.constraint( "core memberOf " + source );
        }
        if ( refract && ! useTMS ) {
            if ( typeName != null ) {
                pattern.constraint( isA( "this ", false, typeName ) );
            }
        }

        if ( arg instanceof OWLNaryBooleanClassExpression ) {
            for ( OWLClassExpression subArg : ( (OWLNaryBooleanClassExpression) arg ).getOperandsAsList() ) {
                if ( ! subArg.isAnonymous() ) {
                    pattern.constraint( isA( "this ", true, subArg ) );
                } else if ( subArg instanceof OWLObjectSomeValuesFrom && isDenotes( subArg ) ) {
                    IRI propIRI = ( (OWLObjectSomeValuesFrom) subArg ).getProperty().getNamedProperty().getIRI();
                    String prop = typedProperty( propIRI.toQuotedString(), fragmentOf( propIRI ) );
                    String val = qualifiedConceptName( ( (OWLObjectSomeValuesFrom) subArg ).getFiller(), ontology );
                    pattern.constraint( prop + " denotes " + val );
                } else if ( subArg instanceof OWLObjectComplementOf && ! ( (OWLObjectComplementOf) subArg ).getOperand().isAnonymous() ) {
                    pattern.constraint( isA( "this", false, ( (OWLObjectComplementOf) subArg ).getOperand() ) );
                } else if ( subArg instanceof OWLQuantifiedObjectRestriction || subArg instanceof OWLQuantifiedDataRestriction ) {
                    String constr = propBinding( (OWLQuantifiedRestriction) subArg );
                    if ( constr != null ) {
                        pattern.constraint( constr );
                    }
                } else if ( subArg instanceof OWLObjectOneOf ) {
                    pattern.constraint( oneOf( (OWLObjectOneOf) subArg ) );
                } else if ( subArg instanceof OWLObjectComplementOf && ( (OWLObjectComplementOf) subArg ).getOperand() instanceof OWLQuantifiedObjectRestriction ) {
                    String constr = propBinding( (OWLQuantifiedRestriction) ( (OWLObjectComplementOf) subArg ).getOperand() );
                    if ( constr != null ) {
                        pattern.constraint( constr );
                    }
                }
            }
            for ( OWLClassExpression subArg : ( (OWLNaryBooleanClassExpression) arg ).getOperandsAsList() ) {
                if ( subArg.isAnonymous()
                     && ! isDenotes( subArg )
                     && ! ( subArg instanceof OWLObjectComplementOf && ! ( (OWLObjectComplementOf) subArg ).getOperand().isAnonymous() )
                     && ! ( subArg instanceof OWLObjectOneOf ) ) {
                    nestedAtom( parent, subArg, ontology );
                }
            }
            context.clearBindings();
        } else if ( ! arg.isAnonymous() ) {
            pattern.constraint( isA( "this ", true, arg ) );
        }

    }

    private String qualifiedConceptName( OWLClassExpression filler, OWLOntology ontology ) {
        final StringBuilder res = new StringBuilder();
        OWLDataFactory odf = ontology.getOWLOntologyManager().getOWLDataFactory();
        OWLObjectSomeValuesFrom expresses = (OWLObjectSomeValuesFrom) ( (OWLObjectUnionOf) filler ).getOperandsAsList().iterator().next();
        OWLClassExpression nested = expresses.getFiller();
        nested = ((OWLNaryBooleanClassExpression) nested ).getOperandsAsList().get( 0 );
        nested = ((OWLNaryBooleanClassExpression) nested ).getOperandsAsList().get( 0 );
        OWLObjectOneOf ones = (OWLObjectOneOf) nested;

        TerminologyGenerator gen = new TerminologyGenerator( ontology, false );

        OWLNamedIndividual concept = (OWLNamedIndividual) ones.individuals().findFirst().orElseThrow( IllegalStateException::new );
	    OWLNamedIndividual scheme = EntitySearcher.getObjectPropertyValues( concept, odf.getOWLObjectProperty( TerminologyGenerator.IN_SCHEME ), ontology )
	                  .filter( OWLNamedIndividual.class::isInstance )
	                  .map( OWLNamedIndividual.class::cast ).findAny().orElseThrow( IllegalStateException::new );

	    ConceptScheme<ConceptDescriptor> sch = gen.toScheme( scheme, ontology );
	    ConceptDescriptor cd = gen.toCode( concept, Collections.singleton( sch ), ontology );
		res.append( NameUtils.getTermCodeSystemName( sch.getSchemeName() ) )
		   .append( "." )
		   .append( NameUtils.getTermConceptName( cd.getCode(), cd.getName() ) );

        return res.toString();
    }

    private boolean isDenotes( OWLClassExpression expr ) {
        if ( ! ( expr instanceof OWLObjectSomeValuesFrom ) ) {
            return false;
        }
        OWLObjectSomeValuesFrom arg = (OWLObjectSomeValuesFrom) expr;
        if ( ! ( arg.getFiller() instanceof OWLObjectUnionOf ) ) {
            return false;
        }
        List<OWLClassExpression> subArgs = ( (OWLObjectUnionOf) arg.getFiller() ).getOperandsAsList();
        if ( subArgs.isEmpty() || ! ( subArgs.get( 0 ) instanceof OWLObjectSomeValuesFrom ) ) {
            return false;
        }
        OWLObjectSomeValuesFrom some = (OWLObjectSomeValuesFrom) subArgs.get( 0 );

	    return some.getProperty().asOWLObjectProperty().getIRI().equals( TerminologyGenerator.DENOTES );
    }

    protected String isA( String subj, boolean positive, OWLClassExpression arg ) {
        return isA( subj, positive, model.getConcept( arg.asOWLClass().getIRI().toQuotedString() ).getFullyQualifiedName() );
    }

    protected String isA( String subj, boolean positive, String arg ) {
        StringBuilder sb = new StringBuilder();
        sb.append( subj );
        if ( ! positive ) {
            sb.append( " not " );
        }
        sb.append( " " ).append( IsAEvaluatorDefinition.ISA.getOperatorString() ).append( " " );
        sb.append( arg ).append( ".class" );
        return sb.toString();
    }

    protected String propBinding( OWLQuantifiedRestriction arg ) {
        String key = null;
        if ( arg instanceof OWLQuantifiedObjectRestriction ) {
            key = ( (OWLQuantifiedObjectRestriction) arg ).getProperty().asOWLObjectProperty().getIRI().toQuotedString();
        } else if ( arg instanceof OWLQuantifiedDataRestriction ) {
            key = ( (OWLQuantifiedDataRestriction) arg ).getProperty().asOWLDataProperty().getIRI().toQuotedString();
        }

	    String pName = model.getProperty( key ).getName();
        String pKey = context.getPropertyKey( pName );

        StringBuilder sb = new StringBuilder();
        if ( ! context.isPropertyBound( pKey ) ) {
            context.bindProperty( pName );
            //sb.append( isA( "this", true, dom ) );
            //sb.append( ", " );
            sb.append( pKey ).append( " : " ).append( typedProperty( key, pName ) );
            return sb.toString();
        } else {
            return null;
        }
    }

    private String typedProperty( String propIRI, String pName ) {
        String dom = model.getProperty( propIRI ).getDomain().getFullyQualifiedName();
	    return "this" +
			    "#" +
			    dom +
			    "." +
			    pName;
    }

    protected String oneOf( OWLObjectOneOf ones ) {
        StringBuilder sb = new StringBuilder();
        sb.append( "uri.toString() " );
        List<OWLIndividual> individuals = new ArrayList<>( ones.individuals().collect( Collectors.toList() ) );
        int N = individuals.size();
        for ( int j = 0; j < N; j++ ) {
            sb.append( " == " ).append( "\"" ).append( individuals.get( j ).asOWLNamedIndividual().getIRI().toString() ).append( "\"" );
            if ( j != N - 1 ) {
                sb.append( " || " );
            }
        }
        return sb.toString();
    }

    protected void nestedAtom( CEDescrBuilder parent, OWLClassExpression expr, OWLOntology ontology ) {
        if ( expr instanceof OWLObjectComplementOf ) {
            negAtom( parent, (OWLObjectComplementOf) expr, ontology );
        } else if ( expr instanceof OWLObjectSomeValuesFrom ) {
            someAtom( parent, (OWLObjectSomeValuesFrom) expr, ontology );
        } else if ( expr instanceof OWLObjectCardinalityRestriction ) {
            numAtom( parent, (OWLObjectCardinalityRestriction) expr, ontology );
        } else if ( expr instanceof OWLDataSomeValuesFrom ) {
            someData( parent, ( OWLDataSomeValuesFrom ) expr );
        } else if ( expr instanceof OWLDataAllValuesFrom ) {
            allData( parent, (OWLDataAllValuesFrom) expr );
        }
    }


    protected void negAtom( CEDescrBuilder parent, OWLObjectComplementOf expr, OWLOntology ontology ) {
        OWLClassExpression operand = expr.getOperand();
        OWLQuantifiedRestriction restr = (OWLQuantifiedRestriction) operand;
        String key = restr.getProperty().asOWLObjectProperty().getIRI().toQuotedString();
        String pName = model.getProperty( key ).getName();
        processOr( parent.not(), (OWLObjectUnionOf) restr.getFiller(), null, context.getPropertyKey( pName ), ontology );
    }

    protected void someAtom( CEDescrBuilder parent, OWLObjectSomeValuesFrom expr, OWLOntology ontology ) {
        String key = expr.getProperty().asOWLObjectProperty().getIRI().toQuotedString();
        String pName = model.getProperty( key ).getName();
        String src = context.getPropertyKey( pName );

        context.push();
        processOr( parent.exists(), expr.getFiller(), null, src, ontology );
        context.pop();
    }

    protected void numAtom( CEDescrBuilder parent, OWLObjectCardinalityRestriction expr, OWLOntology ontology ) {
        String key = expr.getProperty().asOWLObjectProperty().getIRI().toQuotedString();
        String pName = model.getProperty( key ).getName();
        String src = context.getPropertyKey( pName );

        context.push();
        AccumulateDescrBuilder acc = parent.accumulate();
        acc.function( "count", "$num", false, "1" );
        if ( expr instanceof OWLObjectMinCardinality ) {
            acc.constraint( "$num" + " >= " + expr.getCardinality() );
        } else if ( expr instanceof OWLObjectMaxCardinality ) {
            acc.constraint( "$num" + " <= " + expr.getCardinality() );
        } else if ( expr instanceof OWLObjectExactCardinality ) {
            acc.constraint( "$num" + " == " + expr.getCardinality() );
        }
        processOr( acc.source(), expr.getFiller(), null, src, ontology );
        context.pop();
    }


    protected void someData( CEDescrBuilder parent, OWLDataSomeValuesFrom expr ) {
        String key = expr.getProperty().asOWLDataProperty().getIRI().toQuotedString();
        String pName = model.getProperty( key ).getName();
        String src = context.getPropertyKey( pName );

        PatternDescrBuilder pattern = parent.exists().pattern().from().expression( src );
        if ( expr.getFiller() instanceof OWLDatatype ) {
            pattern.type( NameUtils.builtInTypeToWrappingJavaType( expr.getFiller().toString() ) );
        } else {
            pattern.type( Object.class.getName() ).constraint( dataExpr( expr.getFiller() ) );
        }
    }

    protected void allData( CEDescrBuilder parent, OWLDataAllValuesFrom expr ) {
        String key = expr.getProperty().asOWLDataProperty().getIRI().toQuotedString();
        String pName = model.getProperty( key ).getName();
        String src = context.getPropertyKey( pName );

        PatternDescrBuilder pattern = parent.forall().pattern().from().expression( src );
        if ( expr.getFiller() instanceof OWLDatatype ) {
            pattern.type( NameUtils.builtInTypeToWrappingJavaType( expr.getFiller().toString() ) );
        } else {
            pattern.type( Object.class.getName() ).constraint( dataExpr( expr.getFiller() ) );
        }
    }

    protected void numData( CEDescrBuilder parent, OWLDataCardinalityRestriction expr ) {
        String key = expr.getProperty().asOWLDataProperty().getIRI().toQuotedString();
        String pName = model.getProperty( key ).getName();
        String src = context.getPropertyKey( pName );

        AccumulateDescrBuilder acc = parent.accumulate();
        acc.function( "count", "$num", false, "1" );
        if ( expr instanceof OWLDataMinCardinality ) {
            acc.constraint( "$num" + " >= " + expr.getCardinality() );
        } else if ( expr instanceof OWLDataMaxCardinality ) {
            acc.constraint( "$num" + " <= " + expr.getCardinality() );
        } else if ( expr instanceof OWLDataExactCardinality ) {
            acc.constraint( "$num" + " == " + expr.getCardinality() );
        }

        PatternDescrBuilder pattern = acc.source().pattern().from().expression( src );
        if ( expr.getFiller() instanceof OWLDatatype ) {
            pattern.type( NameUtils.builtInTypeToWrappingJavaType( expr.getFiller().toString() ) );
        } else {
            pattern.type( Object.class.getName() ).constraint( dataExpr( expr.getFiller() ) );
        }
    }

    protected String dataExpr( OWLDataRange expr ) {
        if ( expr instanceof OWLDataComplementOf ) {
	        return "! " + dataExpr( ( ( OWLDataComplementOf ) expr ).getDataRange() );
        } else if ( expr instanceof OWLDataIntersectionOf ) {
            OWLDataIntersectionOf and = (OWLDataIntersectionOf) expr;
            List<OWLDataRange> args = and.operands().collect( Collectors.toList() );
            int N = args.size();
            StringBuilder sb = new StringBuilder();
            for ( int j = 0; j < N; j++ ) {
                sb.append( "this instanceof " ).append( dataExpr( args.get( j ) ) );
                if ( j != N - 1 ) {
                    sb.append( " && " );
                }
            }
            return sb.toString();
        } else if ( expr instanceof OWLDataUnionOf ) {
            OWLDataUnionOf or = (OWLDataUnionOf) expr;
            List<OWLDataRange> args = or.operands().collect( Collectors.toList() );
            int N = args.size();
            StringBuilder sb = new StringBuilder();
            for ( int j = 0; j < N; j++ ) {
                sb.append( "this instanceof " ).append( dataExpr( args.get( j ) ) );
                if ( j != N - 1 ) {
                    sb.append( " || " );
                }
            }
            return sb.toString();
        } else if ( expr instanceof OWLDataOneOf ) {
            OWLDataOneOf ones = (OWLDataOneOf) expr;
            List<OWLLiteral> args = ones.values().collect( Collectors.toList() );
            int N = args.size();
            StringBuilder sb = new StringBuilder( "this " );
            for ( int j = 0; j < N; j++ ) {
                sb.append( " == " ).append( formatLiteral( args.get( j ) ) );
                if ( j != N - 1 ) {
                    sb.append( " || " );
                }
            }
            return sb.toString();
        } else {
            return NameUtils.builtInTypeToWrappingJavaType( expr.toString() );
        }
    }

	private Object formatLiteral( OWLLiteral owlLiteral ) {
		if ( owlLiteral.isBoolean() ) {
			return owlLiteral.parseBoolean();
		}
		if ( owlLiteral.isFloat() ) {
			return owlLiteral.parseFloat();
		}
		if ( owlLiteral.isDouble() ) {
			return owlLiteral.parseDouble();
		}
		if ( owlLiteral.isInteger() ) {
			return owlLiteral.parseInteger();
		}
		return "\"" + owlLiteral.getLiteral() + "\"";
	}

	protected void buildDeclarations( PackageDescrBuilder builder ) {
        for ( Concept con : model.getConcepts() ) {
            TypeDeclarationDescrBuilder tdb = builder.newDeclare().type();

            tdb.setTrait( true ).name( con.getFullyQualifiedName() );
            for ( Concept sup : con.getSuperConcepts() ) {
                tdb.superType( sup.getFullyQualifiedName() );
            }
            if ( usePropertyReactivity ) {
                tdb.newAnnotation( PropertyReactive.class.getSimpleName() );
            }

            for ( PropertyRelation prop : con.getProperties().values() ) {
                if ( ! prop.isRestricted() && ! prop.isInherited() ) {
                    FieldDescrBuilder field = tdb.newField( prop.getName() );
                    if ( prop.isSimple() ) {
                        field.type( prop.getTarget().getFullyQualifiedName() );
                    } else {
                        field.type( List.class.getName() ).initialValue( "new " + ArrayList.class.getName() + "()" )
                                .newAnnotation( "genericType" ).value( prop.getTarget().getFullyQualifiedName() );
                    }
                }
            }

        }
    }


    protected Map<OWLClassExpression, OWLClassExpression> preprocessDefinitions( Map<OWLClassExpression, OWLClassExpression> definitions ) {
        Map<OWLClassExpression,OWLClassExpression> filteredDefs = new HashMap<>();
        for ( OWLClassExpression key : definitions.keySet() ) {
            boolean filtered = false;
            OWLClassExpression def = definitions.get( key );
            if ( fragmentOf( key.asOWLClass().getIRI() ).contains( "Filler" ) ) {
                filtered = true;
            } else {
                if ( def instanceof OWLObjectUnionOf ) {
                    OWLObjectUnionOf or = (OWLObjectUnionOf) def;
                    if ( or.getOperandsAsList().size() == 1 ) {
                        OWLClassExpression inner = or.getOperandsAsList().iterator().next();
                        if ( inner instanceof OWLObjectSomeValuesFrom &&
		                        fragmentOf( ( (OWLObjectSomeValuesFrom) inner ).getProperty()
		                                                           .getNamedProperty()
		                                                           .getIRI() )
		                                                           .equals( "denotes" ) ) {
                            filtered = true;
                        }
                    }
                }
            }
            if ( ! filtered ) {
                filteredDefs.put( key, def );
            }

        }
        return filteredDefs;
    }


    public static String generateDRL( PackageDescr root, boolean validate ) {
        String drl = new DrlDumper().dump( root );

        if ( validate ) {
            KieHelper kh = new KieHelper(
                EvaluatorOption.get( "denotes", new MockEvaluatorDefinition() ) );
            Results res = kh.addContent( drl, ResourceType.DRL ).verify();
            if ( res.hasMessages( Message.Level.ERROR ) ) {
                throw new IllegalStateException( res.getMessages( Message.Level.ERROR ).toString() );
            }
        }

        return cleanWhites( drl );
    }



    protected static String cleanWhites( String drl ) {
        return drl.replaceAll( "^ +| +$|( )+", "$1" ).replaceAll( "\\s*\n+\\s*(\\s*\n+\\s*)+", "\n" );
    }


    public APIRecognitionRuleBuilder setUseTMS( boolean useTMS ) {
        this.useTMS = useTMS;
        return this;
    }

    public APIRecognitionRuleBuilder setUsePropertyReactivity( boolean usePropertyReactivity ) {
        this.usePropertyReactivity = usePropertyReactivity;
        return this;
    }

    public APIRecognitionRuleBuilder setDebug( boolean debug ) {
        this.debug = debug;
        return this;
    }

    public APIRecognitionRuleBuilder setUseMetaClass( boolean useMetaClass ) {
        this.useMetaClass = useMetaClass;
        return this;
    }

    public APIRecognitionRuleBuilder setRootClass( String rootClass ) {
        this.rootClass = rootClass;
        return this;
    }

    public APIRecognitionRuleBuilder setRedeclare( boolean redeclare ) {
        this.redeclare = redeclare;
        return this;
    }

    public APIRecognitionRuleBuilder setRefract( boolean refract ) {
        this.refract = refract;
        return this;
    }

    public static class MockEvaluatorDefinition implements EvaluatorDefinition {
        public MockEvaluatorDefinition() { }
        public String[] getEvaluatorIds() { return new String[] { "denotes" }; }
        public boolean isNegatable() { return false; }
        public Evaluator getEvaluator( ValueType type, String operatorId, boolean isNegated, String parameterText, EvaluatorDefinition.Target leftTarget, EvaluatorDefinition.Target rightTarget ) { return null; }
        public Evaluator getEvaluator( ValueType type, String operatorId, boolean isNegated, String parameterText ) { return null; }
        public Evaluator getEvaluator( ValueType type, Operator operator, String parameterText ) { return null; }
        public Evaluator getEvaluator( ValueType type, Operator operator ) { return null; }
        public boolean supportsType( ValueType type ) { return true; }
        public EvaluatorDefinition.Target getTarget() { return EvaluatorDefinition.Target.FACT; }
        public void writeExternal( ObjectOutput objectOutput ) throws IOException {}
        public void readExternal( ObjectInput objectInput ) throws IOException, ClassNotFoundException {}
    }


}
