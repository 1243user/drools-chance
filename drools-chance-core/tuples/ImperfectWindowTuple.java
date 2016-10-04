package org.drools.chance.reteoo.tuples;

import org.drools.chance.degree.Degree;
import org.drools.chance.evaluation.Evaluation;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.reteoo.RightTupleSink;
import org.drools.core.reteoo.WindowTuple;
import org.drools.core.reteoo.WindowTupleList;


public class ImperfectWindowTuple extends WindowTuple {

    protected Evaluation evaluation;
    
    public ImperfectWindowTuple() {
    }

    public ImperfectWindowTuple( InternalFactHandle handle, Evaluation eval ) {
        super( handle );
        this.evaluation = eval;
    }

    public ImperfectWindowTuple( InternalFactHandle handle, RightTupleSink sink, WindowTupleList activeWindowTupleList, Evaluation eval ) {
        super( handle, sink, activeWindowTupleList );
        this.evaluation = eval;
    }

    public Evaluation getEvaluation() {
        return evaluation;
    }

    public void setEvaluation(Evaluation evaluation) {
        this.evaluation = evaluation;
    }

    public Degree getDegree() {
        return getEvaluation().getDegree();
    }

    public int getSourceId() {
        return getEvaluation().getNodeId();
    }
}
