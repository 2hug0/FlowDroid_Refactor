package soot.jimple.infoflow.solver.mergeSolver;

import soot.jimple.infoflow.solver.fastSolver.InfoflowSolver;
import soot.jimple.infoflow.solver.mergeSolver.unithandling.ActivationUnitManager;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.function.Consumer;

import heros.FlowFunction;
import heros.solver.PathEdge;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.problems.AbstractInfoflowProblem;
import soot.jimple.infoflow.solver.EndSummary;
import soot.jimple.infoflow.solver.IInfoflowSolver;
import soot.jimple.infoflow.solver.IncomingRecord;
import soot.jimple.infoflow.solver.executors.InterruptableExecutor;

public class MergeInfoflowSolver extends InfoflowSolver{
    
    private final ActivationUnitManager activationUnitManager;

    public MergeInfoflowSolver(AbstractInfoflowProblem problem, InterruptableExecutor executor,
                               ActivationUnitManager activationUnitManager){
        super(problem, executor);        
        this.activationUnitManager = new ActivationUnitManager(problem.getManager().getICFG());
    }

    @Override
	protected void processCall(PathEdge<Unit, Abstraction> edge) {
		final Abstraction d1 = edge.factAtSource();
		final Unit n = edge.getTarget(); // a call node; line 14...

		final Abstraction d2 = edge.factAtTarget();
		assert d2 != null;
		Collection<Unit> returnSiteNs = icfg.getReturnSitesOfCallAt(n);

		// for each possible callee
		Collection<SootMethod> callees = icfg.getCalleesOfCallAt(n);
		if (callees != null && !callees.isEmpty()) {
			if (maxCalleesPerCallSite < 0 || callees.size() <= maxCalleesPerCallSite) {
				callees.forEach(new Consumer<SootMethod>() {

					@Override
					public void accept(SootMethod sCalledProcN) {
						// Concrete and early termination check
						if (!sCalledProcN.isConcrete() || killFlag != null)
							return;	

						// compute the call-flow function
						FlowFunction<Abstraction> function = flowFunctions.getCallFlowFunction(n, sCalledProcN);
						Set<Abstraction> res = computeCallFlowFunction(function, d1, d2);

						if (res != null && !res.isEmpty()) {
							Collection<Unit> startPointsOf = icfg.getStartPointsOf(sCalledProcN);
							// for each result node of the call-flow function
							for (Abstraction d3 : res) {
								if (memoryManager != null)
									d3 = memoryManager.handleGeneratedMemoryObject(d2, d3);
								if (d3 == null)
									continue;
								PathEdge<Unit, Abstraction> edge = new PathEdge<>(d1, n, d2);
								Set<Abstraction> abs = activationUnitManager.concretize(edge, sCalledProcN, d3, solverId);
								for (Abstraction d0 : abs){
									
									// for each callee's start point(s)
									for (Unit sP : startPointsOf) {
										// create initial self-loop
										schedulingStrategy.propagateCallFlow(d0, sP, d0, n, false); // line 15 d0 was d3
									}

									// register the fact that <sp,d3> has an incoming edge from
									// <n,d2>
									// line 15.1 of Naeem/Lhotak/Rodriguez
									if (!addIncoming(sCalledProcN, d0, n, d1, d2)) // d0 was d3
										continue;

									applyEndSummaryOnCall(d1, n, d2, returnSiteNs, sCalledProcN, d0);
								}
							}
						}
					}

				});
			}
		}

		// line 17-19 of Naeem/Lhotak/Rodriguez
		// process intra-procedural flows along call-to-return flow functions
		for (Unit returnSiteN : returnSiteNs) {
			FlowFunction<Abstraction> callToReturnFlowFunction = flowFunctions.getCallToReturnFlowFunction(n,
					returnSiteN);
			Set<Abstraction> res = computeCallToReturnFlowFunction(callToReturnFlowFunction, d1, d2);
			if (res != null && !res.isEmpty()) {
				for (Abstraction d3 : res) {
					if (memoryManager != null)
						d3 = memoryManager.handleGeneratedMemoryObject(d2, d3);
					if (d3 != null)
						schedulingStrategy.propagateCallToReturnFlow(d1, returnSiteN, d3, n, false);
				}
			}
		}
	}
    
	protected void processExitWithMerge(PathEdge<Unit, Abstraction> edge) {
		final Unit n = edge.getTarget(); // an exit node; line 21...
		SootMethod methodThatNeedsSummary = icfg.getMethodOf(n);

		final Abstraction d1 = edge.factAtSource();
		final Abstraction d2 = edge.factAtTarget();

		// for each of the method's start points, determine incoming calls

		// line 21.1 of Naeem/Lhotak/Rodriguez
		// register end-summary
		if (!addEndSummary(methodThatNeedsSummary, d1, n, d2))
			return;
		Set<IncomingRecord> inc = incoming(d1, methodThatNeedsSummary);

		// for each incoming call edge already processed
		// (see processCall(..))
		for (IncomingRecord entry : inc) {
			// Early termination check
			if (killFlag != null)
				return;

			// line 22
			Unit c = entry.n;
			Set<Abstraction> callerSideDs = Collections.singleton(entry.d1);
			// for each return site
			for (Unit retSiteC : icfg.getReturnSitesOfCallAt(c)) {
				// compute return-flow function
				FlowFunction<Abstraction> retFunction = flowFunctions.getReturnFlowFunction(c, methodThatNeedsSummary,
						n, retSiteC);
				Set<Abstraction> targets = computeReturnFlowFunction(retFunction, d1, d2, c, callerSideDs);
				// for each incoming-call value
				if (targets != null && !targets.isEmpty()) {
					final Abstraction d4 = entry.d1;
					final Abstraction predVal = entry.d2;

					for (Abstraction d5 : targets) {
						if (memoryManager != null)
							d5 = memoryManager.handleGeneratedMemoryObject(d2, d5);
						if (d5 == null)
							continue;

						// If we have not changed anything in the callee, we do not need the facts from
						// there. Even if we change something: If we don't need the concrete path, we
						// can skip the callee in the predecessor chain
						Abstraction d5p = shortenPredecessors(d5, predVal, d1, n, c);
						// Differnce to Naeem/Lhotak/Rodriguez: We need to attach the activation
						d5p = activationUnitManager.attachActivationStmt(predVal, d5p);
						schedulingStrategy.propagateReturnFlow(d4, retSiteC, d5p, c, false);
					}
				}
			}

			// Make sure all of the incoming edges are registered with the edge from the new
			// summary
			d1.addNeighbor(entry.d3);
		}

		// handling for unbalanced problems where we return out of a method with
		// a fact for which we have no incoming flow
		// note: we propagate that way only values that originate from ZERO, as
		// conditionally generated values should only be propagated into callers that
		// have an incoming edge for this condition
		if (followReturnsPastSeeds && d1 == zeroValue && (inc == null || inc.isEmpty())) {
			Collection<Unit> callers = icfg.getCallersOf(methodThatNeedsSummary);
			for (Unit c : callers) {
				for (Unit retSiteC : icfg.getReturnSitesOfCallAt(c)) {
					FlowFunction<Abstraction> retFunction = flowFunctions.getReturnFlowFunction(c,
							methodThatNeedsSummary, n, retSiteC);
					Set<Abstraction> targets = computeReturnFlowFunction(retFunction, d1, d2, c,
							Collections.singleton(zeroValue));
					if (targets != null && !targets.isEmpty()) {
						for (Abstraction d5 : targets) {
							if (memoryManager != null)
								d5 = memoryManager.handleGeneratedMemoryObject(d2, d5);
							if (d5 != null)
								// Difference to Naeem/Lhotak/Rodriguez: We need to attach the activation
								d5 = activationUnitManager.attachActivationStmt(null, d5);
								schedulingStrategy.propagateReturnFlow(zeroValue, retSiteC, d5, c, true);
						}
					}
				}
			}
			// in cases where there are no callers, the return statement would
			// normally not be processed at all; this might be undesirable if the flow
			// function has a side effect such as registering a taint; instead we thus call
			// the return flow function will a null caller
			if (callers.isEmpty()) {
				FlowFunction<Abstraction> retFunction = flowFunctions.getReturnFlowFunction(null,
						methodThatNeedsSummary, n, null);
				retFunction.computeTargets(d2);
			}
		}
	}

    @Override
	protected void processExit(PathEdge<Unit, Abstraction> edge) {
		processExitWithMerge(edge);

		if (followReturnsPastSeeds && followReturnsPastSeedsHandler != null) {
			final Abstraction d1 = edge.factAtSource();
			final Unit u = edge.getTarget();
			final Abstraction d2 = edge.factAtTarget();

			final SootMethod methodThatNeedsSummary = icfg.getMethodOf(u);
			final Set<IncomingRecord> inc = incoming(d1, methodThatNeedsSummary);

			if (inc == null || inc.isEmpty())
				followReturnsPastSeedsHandler.handleFollowReturnsPastSeeds(d1, u, d2);
		}
	}

	
	@Override
	protected void applyEndSummaryOnCall(final Abstraction d1, final Unit n, final Abstraction d2,
			Collection<Unit> returnSiteNs, SootMethod sCalledProcN, Abstraction d3) {
		// line 15.2
		Set<EndSummary> endSumm = endSummary(sCalledProcN, d3);

		// still line 15.2 of Naeem/Lhotak/Rodriguez
		// for each already-queried exit value <eP,d4> reachable
		// from <sP,d3>, create new caller-side jump functions to
		// the return sites because we have observed a potentially
		// new incoming edge into <sP,d3>
		if (endSumm != null && !endSumm.isEmpty()) {
			for (EndSummary entry : endSumm) {
				Unit eP = entry.eP;
				Abstraction d4 = entry.d4;

				// We must acknowledge the incoming abstraction from the other path
				entry.calleeD1.addNeighbor(d3);

				// for each return site
				for (Unit retSiteN : returnSiteNs) {
					// compute return-flow function
					FlowFunction<Abstraction> retFunction = flowFunctions.getReturnFlowFunction(n, sCalledProcN, eP,
							retSiteN);
					Set<Abstraction> retFlowRes = computeReturnFlowFunction(retFunction, d3, d4, n,
							Collections.singleton(d1));
					if (retFlowRes != null && !retFlowRes.isEmpty()) {
						// for each target value of the function
						for (Abstraction d5 : retFlowRes) {
							if (memoryManager != null)
								d5 = memoryManager.handleGeneratedMemoryObject(d4, d5);

							// If we have not changed anything in
							// the callee, we do not need the facts from
							// there. Even if we change something:
							// If we don't need the concrete path,
							// we can skip the callee in the predecessor
							// chain
							Abstraction d5p = shortenPredecessors(d5, d2, d3, eP, n);
							// Difference to Naeem/Lhotak/Rodriguez: We need to attach the activation
							d5p = activationUnitManager.attachActivationStmt(d2, d5p);
							schedulingStrategy.propagateReturnFlow(d1, retSiteN, d5p, n, false);
						}
					}
				}
			}
			onEndSummaryApplied(n, sCalledProcN, d3);
		}
	}

	@Override
	public void injectContext(IInfoflowSolver otherSolver, SootMethod callee, Abstraction d3, Unit callSite,
							  Abstraction d2, Abstraction d1) {
		PathEdge<Unit, Abstraction> edge = new PathEdge<>(d1, callSite, d2); 
		Set<Abstraction> abs = activationUnitManager.concretize(edge, callee, d3, !solverId);
		for (Abstraction d0 : abs){
			if (!addIncoming(callee, d0, callSite, d1, d2)) // old d3 instead of d0
				return;

			Collection<Unit> returnSiteNs = icfg.getReturnSitesOfCallAt(callSite);
			applyEndSummaryOnCall(d1, callSite, d2, returnSiteNs, callee, d0); // old d3 instead of d0
		}
	}


}
