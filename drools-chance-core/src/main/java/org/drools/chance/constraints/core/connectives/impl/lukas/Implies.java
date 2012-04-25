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

package org.drools.chance.constraints.core.connectives.impl.lukas;

import org.drools.chance.constraints.core.connectives.impl.AbstractConnective;
import org.drools.chance.constraints.core.connectives.impl.LogicConnectives;
import org.drools.chance.degree.Degree;
import org.drools.chance.evaluation.Evaluation;


public class Implies extends AbstractConnective {

    public Implies() { }

    private static Implies instance = new Implies();

    public static Implies getInstance() {
        return instance;
    }

    public LogicConnectives getType() {
        return LogicConnectives.IMPL;
    }


    public Degree eval(Degree deg) {
        return deg;
    }

    public Degree eval(Degree left, Degree right) {
        return Not.getInstance().eval(left).sum(right);
    }

    public Degree eval(Degree... degs) {
        Degree deg = degs[0];
        for (int j = 1; j < degs.length; j++) {
            deg = this.eval(deg,degs[j]);
        }
        return deg;
    }

    public Degree eval(Evaluation... degs) {
        Degree deg = degs[0].getDegree();
        for (int j = 1; j < degs.length; j++) {
            deg = this.eval( deg, degs[j].getDegree() );
        }
        return deg;
    }




    public boolean isUnary() {
        return false;
    }

    public boolean isBinary() {
        return true;
    }

    public boolean isNary() {
        return false;
    }
}
