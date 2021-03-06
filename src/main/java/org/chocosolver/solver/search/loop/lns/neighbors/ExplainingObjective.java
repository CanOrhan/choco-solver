/**
 * This file is part of choco-solver, http://choco-solver.org/
 *
 * Copyright (c) 2018, IMT Atlantique. All rights reserved.
 *
 * Licensed under the BSD 4-clause license.
 * See LICENSE file in the project root for full license information.
 */
package org.chocosolver.solver.search.loop.lns.neighbors;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import org.chocosolver.cutoffseq.GeometricalCutoffStrategy;
import org.chocosolver.cutoffseq.ICutoffStrategy;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.ResolutionPolicy;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.explanations.*;
import org.chocosolver.solver.objective.IObjectiveManager;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.events.IntEventType;

/**
 * a specific neighborhood for LNS based on the explanation of the objective variable.
 * <p>
 * This neightborhood is specific in the sense that it needs to compute explanation on a solution.
 * Furthermore, the fixSomeVariables method creates and applies decision, so that the explanation recorder can infer.
 * <br/>
 *
 * @author Charles Prud'homme
 * @since 03/07/13
 */
public class ExplainingObjective extends ExplainingCut{

    /**
     * Reference the objective manager, to
     */
    private IObjectiveManager<IntVar> om;
    /**
     * The objective variable
     */
    private IntVar objective;
    /**
     * The lower bound of the {@link #objective}
     */
    private int LB;
    /**
     * The upper bound of the {@link #objective}
     */
    private int UB;
    /**
     * Clusters of decisions, each of them are related to a domain reduction
     */
    private final TIntArrayList clusters;
    /**
     * Strategy to build clusters, based on geometrical evolution
     */
    private ICutoffStrategy geo4cluster;
    /**
     * current cluster used in the fragment
     */
    private int cluster;

    /**
     * TEMPORARY DATA STRUCTURES.
     */
    private final TIntArrayList tmpDeductions;

    /**
     * TEMPORARY DATA STRUCTURES
     */
    private final TIntSet tmpValueDeductions;

    /**
     * Create a neighborhood which analyses the explanation of the objective current value to focus on decisions
     * more prone to improve its value
     * @param aModel a model
     * @param level relaxong factor
     * @param seed for randomness
     */
    public ExplainingObjective(Model aModel, int level, long seed) {
        super(aModel, level, seed);
        clusters = new TIntArrayList(16);
        // TEMPORARY DATA STRUCTURES
        tmpDeductions = new TIntArrayList();
        tmpValueDeductions = new TIntHashSet(16);
        geo4cluster = new GeometricalCutoffStrategy(1, 1.2);
        //TODO: check plug monitor
    }

    @Override
    public void recordSolution() {
        // 1. clear data structures
        tmpDeductions.clear();
        tmpValueDeductions.clear();
        clusters.clear();
        mDecisionPath.clear();

        // 2. get anti domain of the objective variable
        readRemovedValues();

        // 3. re-build the clusters if required
        buildCluster();

        clonePath();

        related.clear();
        for (int i = 0; i < tmpDeductions.size(); i++) {
            related.set(tmpDeductions.get(i));
        }
        unrelated.clear();
        unrelated.or(related);
        unrelated.flip(0, mModel.getSolver().getDecisionPath().size());
        unrelated.clear(0); // clear ROOT decision
        forceCft = true;
    }

    @Override
    protected void _fixVar() {
        if (cluster < clusters.size()) {
            int k = cluster++;
            if (k < clusters.size()) {
                for (int i = clusters.get(k - 1); i < clusters.get(k); i++) {
                    int idx = tmpDeductions.get(i);
                    notFrozen.clear(idx);
                }
            }
        } else {
            super._fixVar();
        }
    }


    @Override
    public void init() {
        Solver r = mModel.getSolver();
        om = r.getObjectiveManager();
        objective = om.getObjective();
        LB = objective.getLB();
        UB = objective.getUB();
        if (mExplanationEngine == null) {
            if (r.getExplainer() == NoExplanationEngine.SINGLETON) {
                r.setExplainer(new ExplanationEngine(mModel, false, false));
            }
            this.mExplanationEngine = r.getExplainer();
        }
    }

    /*@Override
    public void beforeUpBranch() {
    }

    @Override
    public void afterUpBranch() {
        // we need to catch up that case when the sub tree is closed and this imposes a fragment
        if (last != null && mModel.getResolver().getLastDecision()*//*.getId()*//* == last*//*.getId()*//*) {
            mModel.getSearchLoop().restart();
        }
    }*/

    ///////////////////////


    @Override
    protected void explain() {
        forceCft = false;
        nbFixedVariables = related.cardinality();
        nbCall = 0;
        increaseLimit();

        cluster = 1;
        notFrozen.clear();
        notFrozen.or(related);
    }

    /**
     * Iterate over removed values to explain the objective variable state
     */
    private void readRemovedValues() {
        clusters.add(0);
        if (objective.hasEnumeratedDomain()) {
            readRemovedValuesE();
        } else {
            readRemovedValuesB();
        }
    }

    /**
     * Iterate over removed values to explain the objective variable state
     */
    private void readRemovedValuesE() {
        // 2'. compute bounds to avoid explaining the whole domain
        boolean ismax = om.getPolicy() == ResolutionPolicy.MAXIMIZE;
        int far, near;
        if (ismax) {
            far = UB;
            near = objective.getValue() + 1;
            // explain why obj cannot take a smaller value: from far to near
            while (far >= near) {
                explainValueE(far);
                far--;
            }
        } else {
            far = LB;
            near = objective.getValue() - 1;
            // explain why obj cannot take a smaller value: from far to near
            while (far <= near) {
                explainValueE(far);
                far++;
            }
        }
    }

    /**
     * Explain the removal of value from the objective variable
     *
     * @param value value to explain
     */
    private void explainValueE(int value) {

        // mimic explanation computation
        Explanation explanation = mExplanationEngine.makeExplanation(false);
        RuleStore rs = mExplanationEngine.getRuleStore();
        rs.init(explanation);
        rs.addRemovalRule(objective, value);
        ArrayEventStore es = mExplanationEngine.getEventStore();
        int i = es.getSize() - 1;

        while (i > -1) {
            if (rs.match(i, es)) {
                rs.update(i, es, explanation);
            }
            i--;
        }
        for (int b = explanation.getDecisions().nextSetBit(0); b >= 0; b = explanation.getDecisions().nextSetBit(b + 1)) {
            tmpValueDeductions.add(b);
        }

        assert tmpValueDeductions.size() > 0 : "E(" + value + ") is EMPTY";
        tmpValueDeductions.removeAll(tmpDeductions);
        //        assert tmpDeductions.size() == 0 || correct : "E(" + value + ") not INCLUDED in previous ones";
        if (tmpDeductions.addAll(tmpValueDeductions)) {
            clusters.add(tmpDeductions.size());
        }
        explanation.recycle();
    }


    /**
     * Iterate over removed values to explain the objective variable state
     */
    private void readRemovedValuesB() {
        clusters.add(0);
        // 2'. compute bounds to avoid explaining the whole domain
        boolean ismax = om.getPolicy() == ResolutionPolicy.MAXIMIZE;
        Explanation explanation = mExplanationEngine.makeExplanation(false);
        RuleStore rs = mExplanationEngine.getRuleStore();
        ArrayEventStore es = mExplanationEngine.getEventStore();
        rs.init(explanation);
        int i = 0;
        int far, near;
        if (ismax) {
            far = UB;
            near = objective.getValue() + 1;
            rs.addUpperBoundRule(objective);
            while (i < es.getSize()) {
                if (rs.match(i, es)) {
                    int val = es.getFirstValue(i);
                    int old = es.getSecondValue(i);
                    // need to check event: 2nd pos is old lb in case of instantiation
                    if(es.getEventType(i) == IntEventType.INSTANTIATE){
                        old = es.getThirdValue(i);
                    }
                    if (far >= near && far > val && far <= old) {
                        explainValueB(far, es, i);
                        far = val;
                        rs.init(explanation);
                    }
                    if (far < near) {
                        explanation.recycle();
                        return;
                    }
                }
                i++;
            }

        } else {
            far = LB;
            near = objective.getValue() - 1;
            rs.addLowerBoundRule(objective);
            while (i < es.getSize()) {
                if (rs.match(i, es)) {
                    int val = es.getFirstValue(i);
                    int old = es.getSecondValue(i);
                    // no need to check event; 2nd pos is always old lb
                    if (far <= near && far < val && far >= old) {
                        explainValueB(far, es, i);
                        far = val;
                        rs.init(explanation);
                    }
                    if (far > near) {
                        explanation.recycle();
                        return;
                    }
                }
                i++;
            }
        }
        explanation.recycle();
    }


    private void explainValueB(int value, ArrayEventStore es, int i) {

        // mimic explanation computation
        Explanation explanation = mExplanationEngine.makeExplanation(false);
        RuleStore rs = mExplanationEngine.getRuleStore();
        rs.init(explanation);
        rs.addRemovalRule(objective, value);

        while (i > -1) {
            if (rs.match(i, es)) {
                rs.update(i, es, explanation);
            }
            i--;
        }
        for (int b = explanation.getDecisions().nextSetBit(0); b >= 0; b = explanation.getDecisions().nextSetBit(b + 1)) {
            tmpValueDeductions.add(b);
        }

        tmpValueDeductions.removeAll(tmpDeductions);
        //        assert tmpDeductions.size() == 0 || correct : "E(" + value + ") not INCLUDED in previous ones";
        if (tmpDeductions.addAll(tmpValueDeductions)) {
            clusters.add(tmpDeductions.size());
        }
        explanation.recycle();
    }

    /**
     * Build the cluster, for restrict less operation
     */
    private void buildCluster() {
        if (clusters.size() > 1) {
            int one = clusters.get(1);
            clusters.clear();
            clusters.add(0);
            clusters.add(one);
            geo4cluster = new GeometricalCutoffStrategy(1, 1.2);
            for (int j = 0, i = one + 1; i < tmpDeductions.size(); j++, i += geo4cluster.getNextCutoff()) {
                clusters.add(i);
            }
            if (clusters.get(clusters.size() - 1) != tmpDeductions.size() - 1) {
                clusters.add(tmpDeductions.size() - 1);
            }
        }
    }
}
