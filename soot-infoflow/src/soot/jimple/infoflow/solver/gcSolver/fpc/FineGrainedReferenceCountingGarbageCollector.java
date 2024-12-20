package soot.jimple.infoflow.solver.gcSolver.fpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import heros.solver.Pair;
import heros.solver.PathEdge;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.solver.gcSolver.AbstractReferenceCountingGarbageCollector;
import soot.jimple.infoflow.solver.gcSolver.IGCReferenceProvider;
import soot.jimple.toolkits.ide.icfg.BiDiInterproceduralCFG;
import soot.util.ConcurrentHashMultiMap;

public abstract class FineGrainedReferenceCountingGarbageCollector
		extends AbstractReferenceCountingGarbageCollector<Pair<SootMethod, Abstraction>> {

	protected static final Logger logger = LoggerFactory.getLogger(FineGrainedReferenceCountingGarbageCollector.class);

	public FineGrainedReferenceCountingGarbageCollector(BiDiInterproceduralCFG<Unit, SootMethod> icfg,
			ConcurrentHashMultiMap<Pair<SootMethod, Abstraction>, PathEdge<Unit, Abstraction>> jumpFunctions,
			IGCReferenceProvider<Pair<SootMethod, Abstraction>> referenceProvider) {
		super(icfg, jumpFunctions, referenceProvider);
	}

	public FineGrainedReferenceCountingGarbageCollector(BiDiInterproceduralCFG<Unit, SootMethod> icfg,
			ConcurrentHashMultiMap<Pair<SootMethod, Abstraction>, PathEdge<Unit, Abstraction>> jumpFunctions) {
		super(icfg, jumpFunctions);
	}

	private class GCThread extends Thread {

		private boolean finished = false;

		public GCThread() {
			setName("Fine-grained aggressive IFDS Garbage Collector");
		}

		@Override
		public void run() {
			while (!finished) {
				gcImmediate();

				if (sleepTimeSeconds > 0) {
					try {
						Thread.sleep(sleepTimeSeconds * 1000);
					} catch (InterruptedException e) {
						break;
					}
				}
			}
		}

		/**
		 * Notifies the thread to finish its current garbage collection and then
		 * terminate
		 */
		public void finish() {
			finished = true;
			interrupt();
		}

	}

	protected int sleepTimeSeconds = 1;
	protected int maxPathEdgeCount = 0;
	protected int maxMemoryConsumption = 0;

	protected GCThread gcThread;

	@Override
	protected void initialize() {
		super.initialize();

		// Start the garbage collection thread
		gcThread = new GCThread();
		gcThread.start();
	}

	@Override
	public void gc() {
		// nothing to do here
	}

	@Override
	public void notifySolverTerminated() {
		gcImmediate();

		logger.info(String.format("GC removes %d abstractions", getGcedAbstractions()));
		logger.info(String.format("GC removes %d path edges", getGcedEdges()));
		logger.info(String.format("Remaining Path edges count is %d", getRemainingPathEdgeCount()));
		logger.info(String.format("Recorded Maximum Path edges count is %d", getMaxPathEdgeCount()));
		logger.info(String.format("Recorded Maximum memory consumption is %d", getMaxMemoryConsumption()));
		gcThread.finish();
	}

	/**
	 * Sets the time to wait between garbage collection cycles in seconds
	 *
	 * @param sleepTimeSeconds The time to wait between GC cycles in seconds
	 */
	public void setSleepTimeSeconds(int sleepTimeSeconds) {
		this.sleepTimeSeconds = sleepTimeSeconds;
	}

	private int getUsedMemory() {
		Runtime runtime = Runtime.getRuntime();
		return (int) Math.round((runtime.totalMemory() - runtime.freeMemory()) / 1E6);
	}

	public long getMaxPathEdgeCount() {
		return this.maxPathEdgeCount;
	}

	public int getMaxMemoryConsumption() {
		return this.maxMemoryConsumption;
	}

	@Override
	protected void onAfterRemoveEdges() {
		int pec = 0;
		for (Integer i : jumpFnCounter.values()) {
			pec += i;
		}
		this.maxPathEdgeCount = Math.max(this.maxPathEdgeCount, pec);
		this.maxMemoryConsumption = Math.max(this.maxMemoryConsumption, getUsedMemory());
	}

	@Override
	protected Pair<SootMethod, Abstraction> genAbstraction(PathEdge<Unit, Abstraction> edge) {
		SootMethod method = icfg.getMethodOf(edge.getTarget());
		return new Pair<>(method, edge.factAtSource());
	}
}
