/**
 * This file is part of choco-solver, http://choco-solver.org/
 *
 * Copyright (c) 2018, IMT Atlantique. All rights reserved.
 *
 * Licensed under the BSD 4-clause license.
 * See LICENSE file in the project root for full license information.
 */
package org.chocosolver.solver.expression.discrete.relational;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.constraints.extension.Tuples;
import org.chocosolver.solver.constraints.extension.TuplesFactory;
import org.chocosolver.solver.expression.discrete.arithmetic.ArExpression;
import org.chocosolver.solver.expression.discrete.logical.BiLoExpression;
import org.chocosolver.solver.expression.discrete.logical.LoExpression;
import org.chocosolver.solver.expression.discrete.logical.NaLoExpression;
import org.chocosolver.solver.expression.discrete.logical.UnLoExpression;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * relational expression
 * <p>
 * Project: choco-solver.
 *
 * @author Charles Prud'homme
 * @since 28/04/2016.
 */
public interface ReExpression extends ArExpression {

    /**
     * List of available operator for relational expression
     */
    enum Operator {
        /**
         * less than
         */
        LT {
            @Override
            boolean eval(int i1, int i2) {
                return i1 < i2;
            }
        },
        /**
         * less than or equal to
         */
        LE {
            @Override
            boolean eval(int i1, int i2) {
                return i1 <= i2;
            }
        },
        /**
         * greater than
         */
        GE {
            @Override
            boolean eval(int i1, int i2) {
                return i1 >= i2;
            }
        },
        /**
         * greater than or equal to
         */
        GT {
            @Override
            boolean eval(int i1, int i2) {
                return i1 > i2;
            }
        },
        /**
         * not equal to
         */
        NE {
            @Override
            boolean eval(int i1, int i2) {
                return i1 != i2;
            }
        },
        /**
         * equal to
         */
        EQ {
            @Override
            boolean eval(int i1, int i2) {
                return i1 == i2;
            }
        };

        abstract boolean eval(int i1, int i2);
    }

    /**
     * Return the associated model
     *
     * @return a Model object
     */
    Model getModel();

    /**
     * @return the relational expression as an {@link BoolVar}.
     * If necessary, it creates intermediary variable and posts intermediary constraints
     */
    BoolVar boolVar();

    @Override
    default IntVar intVar() {
        return boolVar();
    }

    /**
     * Extract the variables from this expression
     * @param variables set of variables
     */
    void extractVar(HashSet<IntVar> variables);

    /**
     * Post the decomposition of this expression in the solver
     */
    default void post() {
        decompose().post();
    }

    /**
     * @return the topmost constraint representing the expression. If needed, a call to this method
     * creates additional variables and posts additional constraints.
     */
    default Constraint decompose(){
        throw new UnsupportedOperationException();
    }

    /**
     * @return a TABLE constraint that captures the expression
     */
    default Constraint extension() {
        HashSet<IntVar> avars = new LinkedHashSet<>();
        extractVar(avars);
        IntVar[] uvars = avars.stream().sorted().toArray(IntVar[]::new);
        Map<IntVar, Integer> map = IntStream.range(0, uvars.length).boxed().collect(Collectors.toMap(i -> uvars[i], i -> i));
        Tuples tuples = TuplesFactory.generateTuples(values -> beval(values, map), true, uvars);
//        System.out.printf("%d -> %d\n", VariableUtils.domainCardinality(uvars), tuples.nbTuples());
        return getModel().table(uvars, tuples);
    }

    /**
     * @param values int values to evaluate
     * @param map mapping between variables of the topmost expression and position in <i>values</i>
     * @return an evaluation of this relational expression based on a tuple
     */
    @SuppressWarnings("SuspiciousMethodCalls")
    default boolean beval(int[] values, Map<IntVar, Integer> map){
        assert this instanceof BoolVar;
        return values[map.get(this)] == 1;
    }

    @Override
    default int ieval(int[] values, Map<IntVar, Integer> map) {
        return beval(values, map)?1:0;
    }

    /**
     * @param y some relational expressions
     * @return return the expression "x &and; y_1 &and; y_2 &and; ..." where this is "x"
     */
    default ReExpression and(ReExpression... y) {
        return new NaLoExpression(LoExpression.Operator.AND, this, y);
    }

    /**
     * @param y some relational expressions
     * @return return the expression "x &or; y_1 &or; y_2 &or; ..." where this is "x"
     */
    default ReExpression or(ReExpression... y) {
        return new NaLoExpression(LoExpression.Operator.OR, this, y);
    }

    /**
     * @param y a relational expression
     * @return return the expression "x &oplus; y" where this is "x"
     */
    default ReExpression xor(ReExpression y) {
        return new BiLoExpression(LoExpression.Operator.XOR, this, y);
    }

    /**
     * @param y a relational expression
     * @return return the expression "x &rArr; y" where this is "x"
     */
    default ReExpression imp(ReExpression y) {
        return new BiLoExpression(LoExpression.Operator.IMP, this, y);
    }

    /**
     * @param y a relational expression
     * @return return the expression "x &hArr; y_1 &hArr; y_2 &hArr; ..." where this is "x"
     */
    default ReExpression iff(ReExpression... y) {
        return new NaLoExpression(LoExpression.Operator.IFF, this, y);
    }

    /**
     * @return return the expression "&not;x" where this is "x"
     */
    default ReExpression not() {
        return new UnLoExpression(LoExpression.Operator.NOT, this);
    }
}
