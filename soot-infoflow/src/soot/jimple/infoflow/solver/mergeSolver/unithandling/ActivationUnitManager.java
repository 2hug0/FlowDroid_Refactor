package soot.jimple.infoflow.solver.mergeSolver.unithandling;

import soot.jimple.infoflow.data.accessPaths.ConcolicUnit;
import soot.jimple.infoflow.solver.IInfoflowSolver;
import soot.jimple.infoflow.solver.mergeSolver.MergeInfoflowSolver;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.toolkits.ide.icfg.BiDiInterproceduralCFG;

import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;


import heros.solver.PathEdge;
import soot.SootMethod;
import soot.Unit;


public class ActivationUnitManager {

    protected final BiDiInterproceduralCFG<Unit, SootMethod> icfg;   
    protected MergeInfoflowSolver forwardSolver; 

    protected final ConcurrentHashMap<Symbol, Set<Unit>> symb2Reps = new ConcurrentHashMap<>();    
    protected final ConcurrentHashMap<Symbol, Set<SymbolIncomingEntry>> symbolIncoming = new ConcurrentHashMap<>();


    public ActivationUnitManager(BiDiInterproceduralCFG<Unit, SootMethod> icfg) {		
		this.icfg = icfg;        	
	}

    public void setForwardSolver(IInfoflowSolver solver) {
		this.forwardSolver = (MergeInfoflowSolver) solver;
	}

    // 
    public boolean addToSymb2Reps(Symbol symbol, Unit concreteActivationUnit) {
        return symb2Reps.computeIfAbsent(symbol, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                 .add(concreteActivationUnit);                
    }

    public Set<Unit> getConcreteUnitsForSymbol(Symbol symbol) {
        return symb2Reps.getOrDefault(symbol, Collections.emptySet());
    }

    public boolean containsUnitForSymbol(Symbol symbol, Unit concreteActivationUnit) {
        return symb2Reps.getOrDefault(symbol, Collections.emptySet()).contains(concreteActivationUnit);
    }

    public boolean addToSymbolIncoming(Symbol symbol, PathEdge<Unit, Abstraction> pathEdge, Abstraction abstraction) {
        return symbolIncoming.computeIfAbsent(symbol, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                      .add(new SymbolIncomingEntry(pathEdge, abstraction));
    }
    
    public Set<SymbolIncomingEntry> getPathEdgesForSymbol(Symbol symbol) {
        return symbolIncoming.getOrDefault(symbol, Collections.emptySet());
    }

    public boolean containsPathEdgeForSymbol(Symbol symbol, PathEdge<Unit, Abstraction> pathEdge) {
        return symbolIncoming.getOrDefault(symbol, Collections.emptySet())
                             .stream()
                             .anyMatch(entry -> entry.getPathEdge().equals(pathEdge));
    }
    
    
    
    // Lines 61-68
    public void onActivationStmtAdded(Symbol symbol, Unit activationUnit) {

        // Line 62 get caller and callee from symbol
        // SootMethod caller = symbol.getCaller();
        SootMethod callee = symbol.getCallee();
        
        // Line 63 iterate over all PathEdges for symbol from symbolIncoming
        for (SymbolIncomingEntry entry : symbolIncoming.getOrDefault(symbol, Collections.emptySet())) {

            PathEdge<Unit, Abstraction> edge = entry.getPathEdge();
            Abstraction abs = entry.getAbstraction();
            Abstraction d1 = edge.factAtSource();
			Unit u = edge.getTarget();
			Abstraction d2 = edge.factAtTarget();
            // Line 64 combine abstraction with concrete activationUnit
            Abstraction d3 = abs.replaceActivationUnit(activationUnit);

            // Line 65 propagate d3, d5 in extern method
            forwardSolver.enterMethod(d1, u, d2, callee, d3);
                    
        }
    }   
    
    // Lines 70-77
    public Abstraction symbolize(SootMethod callerSM, SootMethod calleeSM, Abstraction abstraction){
        // Line 71 get concrete activation unit
        final Unit activationUnit = abstraction.getActivationUnit();

        // Line 72 clone abstraction happens in return method replaceActivationUnit
        // Line 73 create new symbol
        Symbol symbol = new Symbol(callerSM, calleeSM, abstraction);

        // Line 74 check if activation unit is already in symb2Reps
        if (!symb2Reps.getOrDefault(symbol, Collections.emptySet()).contains(activationUnit)){

            // Line 75 if not add activation unit to symb2Reps
            if(addToSymb2Reps(symbol, activationUnit)){

                // Line 76 call onActivationStmtAdded
                onActivationStmtAdded(symbol, activationUnit);
            }
        }
        
        // Line 77 return abstraction with symbolic activation unit
        return abstraction.replaceActivationUnit(symbol);

    }

    // Lines 78-92
    public Set<Abstraction> concretize(PathEdge<Unit, Abstraction> edge, SootMethod callee, Abstraction d3, boolean solverID){
        // Line 79 Abstraction is active if activationUnit (ConcolicUnit) == null
        if(d3.isAbstractionActive()){
            return Collections.singleton(d3);
        }
        // Line 80: activationStmt (ConcolicUnit) of d3
        ConcolicUnit activationStmt = d3.getConcolicActivationUnit();

        if (!solverID) {
			if (activationStmt.getSymbol() == Symbol.GAS)
				return Collections.singleton(d3);
			return Collections.singleton(d3.deriveSymbolicAbstraction(Symbol.GAS));
		}

        // Line 81: concrete activationStmt or GAS -> return d3
        if (activationStmt.isConcrete() || activationStmt.getSymbol() == Symbol.GAS) {
            return Collections.singleton(d3);    
        }      
        
        // Line 82: activationStmt must be symbolic
        assert d3.getConcolicActivationUnit().isSymbolic();

        // Line 83: extract Symbol
        Symbol symbol_d3 = d3.getConcolicActivationUnit().getSymbol();

        // Line 84 Set of represented facts (Abstractions)
        Set<Abstraction> representedFacts = new HashSet<>();

        // Line 85: new Abstraction of d3 is processed in "deriveAbstraction..." methods
        Unit callSiteUnit = edge.getTarget();         
        SootMethod caller = icfg.getMethodOf(callSiteUnit);        

        // Line 86 check if context matches
        if(solverID && symbol_d3.matchContext(caller, callee)){

            // Line 87 save PathEdge and Abstraction in symbolIncoming
            symbolIncoming.computeIfAbsent(symbol_d3, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                      .add(new SymbolIncomingEntry(edge, d3));

            // Line 88 iterate over all concrete activationStmt for the symbol
            for (Unit v : symb2Reps.getOrDefault(symbol_d3, Collections.emptySet())) {

                // Line 89 derive abstraction from d3 with activation unit v
                representedFacts.add(d3.replaceActivationUnit(v)); // abs ∥ v
            }
        // Line 90 if context does not match
        } else{
            // Line 91 we assign GAS to the abstraction
            representedFacts.add(d3.deriveSymbolicAbstraction(Symbol.GAS));
        }        
        
        // Line 92 return set of represented facts
        return representedFacts;
    }

    // Lines 93-98
    public Abstraction attachActivationStmt(Abstraction callSiteAbs, Abstraction returnSiteAbs){        
        
        // Line 94 get symbol of returnSiteAbs (u)
        Symbol symbolRetSiteAbs = returnSiteAbs.getConcolicActivationUnit().getSymbol();        

        // Line 96  check if u equals GAS - v is processed in deriveConcreteAbstraction
        if(symbolRetSiteAbs == Symbol.GAS && callSiteAbs != null){
            // Line 97 derive abstraction from returnSiteAbs with activationStmntCallSiteAbs v
            return returnSiteAbs.deriveConcreteAbstraction(callSiteAbs);
        }
        else {
            // Line 98 return returnSiteAbs
            return returnSiteAbs;
        } 
    }

    // Lines 99-103
    public Abstraction onReturnFlow(Abstraction returnSiteAbs, Abstraction callSiteAbs, SootMethod callee, SootMethod caller){
        // Line 100 get symbol of returnSiteAbs
        Symbol symbol = returnSiteAbs.getConcolicActivationUnit().getSymbol();

        // Line 101 if symbol is GAS and callSiteAbs is not null
        if(symbol == Symbol.GAS && callSiteAbs != null){

            // Line 102 attach activationstatement
            return attachActivationStmt(returnSiteAbs, callSiteAbs);
        }

        // Line 103 return symbolized abstraction
        else{
            return symbolize(caller, callee, returnSiteAbs);        
        }
    }

    public SootMethod getMethodOf(ConcolicUnit concolicUnit) {
        Symbol symbol = concolicUnit.getSymbol();
		if (symbol != null)
			return symbol.getCallee();
		else
			return icfg.getMethodOf(concolicUnit.getUnit());
	}

}
