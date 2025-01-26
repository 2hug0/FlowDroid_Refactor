package soot.jimple.infoflow.solver.mergeSolver;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import soot.SootMethod;
import soot.Unit;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.solver.mergeSolver.solver.InfoflowSolver;
import soot.jimple.infoflow.solver.IInfoflowSolver;
import soot.jimple.toolkits.ide.icfg.BiDiInterproceduralCFG;

public class ActivationUnitManager {

	protected final Map<Symbol, Symbol> symbolSet;

	protected final Unit inactiveSymbol;

	protected InfoflowSolver forwardSolver;

	protected final Map<Symbol, Map<Abstraction, Map<Unit, Map<Abstraction, Abstraction>>>> symbolIncoming;

	protected final BiDiInterproceduralCFG<Unit, SootMethod> icfg;

	protected LoadingCache<SootMethod, Set<SootMethod>> methodToCallees = CacheBuilder.newBuilder()
			.build(new CacheLoader<SootMethod, Set<SootMethod>>() {

				@Override
				public Set<SootMethod> load(SootMethod key) throws Exception {
					return getTransitiveCallees(key);
				};

			});


	public ActivationUnitManager(BiDiInterproceduralCFG<Unit, SootMethod> icfg) {
		this.inactiveSymbol = Symbol.getInactiveSymbol();
		this.icfg = icfg;

		this.symbolSet = new ConcurrentHashMap<>();
		this.symbolIncoming = new ConcurrentHashMap<>();
	}

	public Abstraction symbolize(Unit callSite, SootMethod callee, Abstraction retSiteAbs,
			Abstraction exitAbs) {
		final Unit activationUnit = retSiteAbs.getActivationUnit();

		final SootMethod caller = icfg.getMethodOf(callSite);
		final Symbol symbol = new Symbol(caller, callee, exitAbs.getActiveCopy());
		final Symbol old = symbolSet.computeIfAbsent(symbol, v -> symbol);

		if (old.addTarget(activationUnit))
			onNewTargetAddedToSymbol(old, activationUnit);

		return retSiteAbs.replaceActivationUnit(old);
	}

	public Set<Abstraction> concretize(Abstraction d1, Unit callSite, Abstraction d2, 
			SootMethod callee, Abstraction abs, boolean solverId) {
		if (abs.isAbstractionActive())
			return Collections.singleton(abs);

		Unit u = abs.getActivationUnit();
		if (!solverId) {
			if (u == inactiveSymbol)
				return Collections.singleton(abs);
			return Collections.singleton(abs.deriveSymbolicAbstraction(inactiveSymbol));
		}

		if (u == inactiveSymbol || !(u instanceof Symbol))
			return Collections.singleton(abs);

		Symbol symbol = (Symbol) u;
		if (solverId && symbol.matchContext(icfg.getMethodOf(callSite), callee)) {
			addSymbolIncoming(symbol, abs, d1, callSite, d2);

			return symbol.getTargets().stream()
					.map(au -> abs.replaceActivationUnit(au))
					.collect(Collectors.toSet());
		}
		return Collections.singleton(abs.deriveSymbolicAbstraction(inactiveSymbol));
	}

	public Abstraction attatchActivationUnit(Abstraction callSiteAbs, Abstraction retSiteAbs) {
		if (retSiteAbs.getActivationUnit() == inactiveSymbol && callSiteAbs != null)
			return retSiteAbs.deriveConcreteAbstraction(callSiteAbs);
		return retSiteAbs;
	}

	protected void onNewTargetAddedToSymbol(Symbol symbol, Unit target) {
		Map<Abstraction, Map<Unit, Map<Abstraction, Abstraction>>> map = symbolIncoming.get(symbol);
		if (map != null && !map.isEmpty()) {
			for (Map.Entry<Abstraction, Map<Unit, Map<Abstraction, Abstraction>>> entry : map.entrySet()) {
				Abstraction d3 = entry.getKey();
				for (Map.Entry<Unit, Map<Abstraction, Abstraction>> callEntry : entry.getValue().entrySet())
					for (Map.Entry<Abstraction, Abstraction> d1d2Entry : callEntry.getValue().entrySet())
						forwardSolver.enterMethod(d1d2Entry.getKey(), callEntry.getKey(), d1d2Entry.getValue(),
								symbol.getCallee(), d3.replaceActivationUnit(target));
			}
		}
	}

	protected boolean addSymbolIncoming(Symbol symbol, Abstraction abs, Abstraction d1, Unit callSite, Abstraction d2) {
		return symbolIncoming.computeIfAbsent(symbol, v -> new ConcurrentHashMap<>())
				.computeIfAbsent(abs.getActiveCopy(), v -> new ConcurrentHashMap<>())
				.computeIfAbsent(callSite, v -> new ConcurrentHashMap<>())
				.put(d1, d2) == null;
	}
	
	public SootMethod getMethodOf(Unit unit) {
		if (unit instanceof Symbol)
			return ((Symbol) unit).getCallee();
		else
			return icfg.getMethodOf(unit);
	}

	/*
	* check if method `cur` is in the set of transitive callees of `target`
	*/
	public boolean inTransitiveCalleeOf(SootMethod cur, SootMethod target) {
		return methodToCallees.getUnchecked(target).contains(cur);
	}

	protected Set<SootMethod> getTransitiveCallees(SootMethod method) {
		Set<SootMethod> callees = new HashSet<>();
		Queue<SootMethod> workList = new LinkedList<>();
		workList.offer(method);
		callees.add(method);

		while (!workList.isEmpty()) {
			SootMethod sm = workList.poll();
			if (sm.isConcrete()) {
				// We can only look for callees if we have a body
				if (sm.hasActiveBody()) {
					// Schedule the callees
					for (Unit callSite : icfg.getCallsFromWithin(sm)) {
						for (SootMethod callee : icfg.getCalleesOfCallAt(callSite)) {
							if (callees.add(callee))
								workList.offer(callee);
						}
					}
				}
			}
		}

		return callees;
	}

	public void setForwardSolver(IInfoflowSolver solver) {
		this.forwardSolver = (InfoflowSolver) solver;
	}

	public void cleanup() {
		symbolSet.clear();
		symbolIncoming.clear();
		methodToCallees.invalidateAll();
	}

	public void printStat() {
		if (symbolSet.isEmpty())
			return;

		double setSize = symbolSet.size();
		long accum = 0;
		long min = Long.MAX_VALUE;
		long max = 0;
		long accum1 = 0;
		long num1 = 0;
		for (Symbol sym : symbolSet.keySet()) {
			long size = sym.getTargets().size();
			accum += size;
			if (size > max)
				max = size;
			if (size < min)
				min = size;
			if (size > 1) {
				accum1 += size;
				num1 += 1;
			}
		}
		System.out.println("Stat:");
		System.out.println("    #Symbol: " + setSize);
		System.out.println("    #Max: " + max);
		System.out.println("    #Min: " + min);
		if (setSize > 0) {
			double avg = accum / setSize;
			System.out.println("    #Avg: " + avg);
		}
		if (num1 > 0) {
			System.out.println("    #Symbol1: " + num1);
			double avg1 = accum1 / num1;
			System.out.println("    #Avg1: " + avg1);
		}
	}

}
