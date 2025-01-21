package soot.jimple.infoflow.solver.mergeSolver;

import soot.jimple.infoflow.InfoflowManager;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.problems.AbstractInfoflowProblem;
import soot.jimple.infoflow.problems.InfoflowProblem;
import soot.jimple.infoflow.problems.rules.IPropagationRuleManagerFactory;
import soot.jimple.infoflow.solver.executors.InterruptableExecutor;
import soot.jimple.infoflow.solver.fastSolver.InfoflowSolver;

public class MergeInfoflowSolver extends InfoflowProblem{

    protected final MergeInfoflowManager manager;

    public MergeInfoflowProblem(InfoflowManager manager, Abstraction zeroValue, IPropagationRuleManagerFactory ruleManagerFactory) {
		super(manager, zeroValue, ruleManagerFactory);

        this.manager = (MergeInfoflowManager) manager;
    }
}
