package org.drools.chance.reteoo.tuples;

import org.drools.chance.degree.Degree;
import org.drools.chance.evaluation.Evaluation;
import org.drools.chance.reteoo.ChanceFactHandle;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.reteoo.RightTuple;
import org.drools.core.reteoo.RightTupleSink;

public class ImperfectRightTuple extends RightTuple implements ImperfectTuple {
    
    private Evaluation evaluation;
        
    public ImperfectRightTuple() {
        super();
    }

    public ImperfectRightTuple( InternalFactHandle handle, Evaluation eval ) {
        super( handle );
        this.evaluation = eval;
    }

    public ImperfectRightTuple( InternalFactHandle handle, RightTupleSink sink, Evaluation eval ) {
        super( handle, sink );
        this.evaluation = eval;
    }

    public Evaluation getEvaluation() {
        return evaluation;
    }

    public Evaluation getCachedEvaluation( int idx ) {
        Evaluation eval = evaluation.getNodeId() == idx ? evaluation : null;
        return eval != null ? eval : ((ChanceFactHandle) this.getFactHandle()).getCachedEvaluation( idx );
    }


    public void addEvaluation(Evaluation evaluation) {
        this.evaluation = evaluation;
        ((ChanceFactHandle) this.getFactHandle()).addEvaluation( evaluation.getNodeId(), evaluation );
    }

    public void setEvaluation(Evaluation eval) {
        throw new UnsupportedOperationException( "Evaluations are added, not set, on a right tuple" );
    }

    public Degree getDegree() {
        return getEvaluation().getDegree();
    }
    
    public int getSourceId() {
        return getEvaluation().getNodeId();
    }
}
