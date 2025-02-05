package soot.jimple.infoflow.solver.mergeSolver;

import soot.FastHierarchy;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.InfoflowManager;
import soot.jimple.infoflow.globalTaints.GlobalTaintManager;
import soot.jimple.infoflow.solver.IInfoflowSolver;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.infoflow.solver.mergeSolver.unithandling.ActivationUnitManager;
import soot.jimple.infoflow.sourcesSinks.manager.ISourceSinkManager;
import soot.jimple.infoflow.taintWrappers.ITaintPropagationWrapper;

public class MergeInfoflowManager extends InfoflowManager{

    protected final ActivationUnitManager activationUnitManager;

    public MergeInfoflowManager(InfoflowConfiguration config, IInfoflowSolver mainSolver, IInfoflowCFG icfg,
			ISourceSinkManager sourceSinkManager, ITaintPropagationWrapper taintWrapper, FastHierarchy hierarchy,
			GlobalTaintManager globalTaintManager, ActivationUnitManager activationUnitManager) {
		super(config, mainSolver, icfg, sourceSinkManager, taintWrapper, hierarchy, globalTaintManager);

		this.activationUnitManager = activationUnitManager == null ? new ActivationUnitManager(icfg) : activationUnitManager;
	}

	protected MergeInfoflowManager(InfoflowConfiguration config, IInfoflowSolver mainSolver, IInfoflowCFG icfg,
			ISourceSinkManager sourceSinkManager, ITaintPropagationWrapper taintWrapper, FastHierarchy hierarchy,
			MergeInfoflowManager existingManager){
		super(config, mainSolver, icfg, sourceSinkManager, taintWrapper, hierarchy, existingManager.getGlobalTaintManager());
		this.activationUnitManager = existingManager.getActivationUnitManager();
	}



	public ActivationUnitManager getActivationUnitManager() {
		return activationUnitManager;
	}

	@Override
	public void setMainSolver(IInfoflowSolver solver) {
		super.setMainSolver(solver);
		activationUnitManager.setForwardSolver(solver);
	}	
    
}
