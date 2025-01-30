package soot.jimple.infoflow.solver.mergeSolver;

import soot.jimple.infoflow.data.accessPaths.ConcolicUnit;
import soot.jimple.infoflow.solver.mergeSolver.Symbol;
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
    protected final Symbol GAS;

    protected final ConcurrentHashMap<Symbol, Set<Unit>> symb2Reps = new ConcurrentHashMap<>();    
    protected final ConcurrentHashMap<Symbol, Set<SymbolIncomingEntry>> symbolIncoming = new ConcurrentHashMap<>();


    public ActivationUnitManager(BiDiInterproceduralCFG<Unit, SootMethod> icfg) {		
		this.icfg = icfg;
        this.GAS = Symbol.GAS;		
	}

    // 
    public void addToSymb2Reps(Symbol symbol, Unit concreteActivationUnit) {
        symb2Reps.computeIfAbsent(symbol, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                 .add(concreteActivationUnit);
    }

    public Set<Unit> getConcreteUnitsForSymbol(Symbol symbol) {
        return symb2Reps.getOrDefault(symbol, Collections.emptySet());
    }

    public boolean containsUnitForSymbol(Symbol symbol, Unit concreteActivationUnit) {
        return symb2Reps.getOrDefault(symbol, Collections.emptySet()).contains(concreteActivationUnit);
    }

    public void addToSymbolIncoming(Symbol symbol, PathEdge<Unit, Abstraction> pathEdge, Abstraction abstraction) {
        symbolIncoming.computeIfAbsent(symbol, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
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
        SootMethod caller = symbol.getCaller();
        SootMethod callee = symbol.getCallee();
        
        // Line 63 iterate over all PathEdges for symbol from symbolIncoming
        for (SymbolIncomingEntry entry : symbolIncoming.getOrDefault(symbol, Collections.emptySet())) {

            PathEdge<Unit, Abstraction> pathEdge = entry.getPathEdge();
            Abstraction abs = entry.getAbstraction();

            // Line 64 combine abstraction with concrete activationUnit
            Abstraction d3 = abs.deriveAbstractionChangeActivationStmt(activationUnit);

            // Line 65 propagate d3, d5 in extern method
                    
        }
    }   
    
    // Lines 70-77
    public Abstraction symbolize(SootMethod callerSM, SootMethod calleeSM, Abstraction abstraction){
        // Line 71 get concrete activation unit
        Unit activationUnit = abstraction.getActivationUnit();

        // Line 72 clone abstraction
        Abstraction resAbstraction = abstraction.clone();

        // Line 73 create new symbol
        Symbol symbol = new Symbol(callerSM, calleeSM, abstraction);

        // Line 74 check if activation unit is not already in symb2Reps
        if (!symb2Reps.getOrDefault(symbol, Collections.emptySet()).contains(activationUnit)){

            // Line 75 if not add activation unit to symb2Reps
            symb2Reps.computeIfAbsent(symbol, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                 .add(activationUnit);

            // Line 76 call onActivationStmtAdded
            onActivationStmtAdded(symbol, activationUnit);
        }
        
        // Line 77 return abstraction with symbolic activation unit
        return resAbstraction = resAbstraction.makeActivationUnitSymbolic(symbol);

    }

    // Lines 78-92
    public Set<Abstraction> conretize(PathEdge<Unit, Abstraction> edge, SootMethod callee, Abstraction d3){
        // Line 79 Abstraction is active if activationUnit (ConcolicUnit) == null
        if(d3.isAbstractionActive()){
            return Collections.singleton(d3);
        }
        // Line 80: activationStmt (ConcolicUnit) of d3
        ConcolicUnit activationStmt = d3.getConcolicActivationUnit();

        // Line 81: activationStmt concrete or GAS -> return d3
        if (activationStmt.isConcrete() || activationStmt.getSymbol() == Symbol.GAS) {
        return Collections.singleton(d3);    
        }      
        
        // Line 82: activationStmt must be symbolic
        assert d3.getConcolicActivationUnit().isSymbolic();

        // Line 83: extract Symbol
        Symbol symbolD3 = d3.getConcolicActivationUnit().getSymbol();

        // Line 84 Set of represented facts (Abstractions)
        Set<Abstraction> representedFacts = new HashSet<>();

        // Line 85: new Abstraction of d3
        Abstraction absD3 = d3.clone();

        Unit callSiteUnit = edge.getTarget();         
        SootMethod caller = icfg.getMethodOf(callSiteUnit);
        // SootMethod callee = icfg.getCalleesOfCallAt(callSiteUnit).iterator().next();

        // Line 86 check if context matches
        if(symbolD3.matchContext(caller, callee)){

            // Line 87 save PathEdge and Abstraction in symbolIncoming
            symbolIncoming.computeIfAbsent(symbolD3, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                      .add(new SymbolIncomingEntry(edge, absD3));

            // Line 88 iterate over all concrete activation units for the symbol
            for (Unit v : symb2Reps.getOrDefault(symbolD3, Collections.emptySet())) {

                // Line 89 derive abstraction from d3 with activation unit v
                representedFacts.add(absD3.deriveAbstractionChangeActivationStmt(v)); // abs ∥ v
            }
        // Line 90 if context does not match
        } else{
            // Line 91 we assign GAS to the abstraction
            representedFacts.add(absD3.makeActivationUnitSymbolic(Symbol.GAS));
        }        
        
        // Line 92 return set of represented facts
        return representedFacts;
    }

    // Lines 93-98
    public Abstraction attachActivationStmt(Abstraction returnSiteAbs, Abstraction callSiteAbs){        
        
        // Line 94 get symbol of returnSiteAbs (u)
        Symbol symbolRetSiteAbs = returnSiteAbs.getConcolicActivationUnit().getSymbol();

        // Line 95 get conrete activation unit of callSiteAbs (v)
        Unit activationStmntCallSiteAbs = callSiteAbs.getActivationUnit();

        // Line 96  
        if(symbolRetSiteAbs == Symbol.GAS && activationStmntCallSiteAbs != null){
            // Line 97 derive abstraction from returnSiteAbs with activationStmntCallSiteAbs v
            return returnSiteAbs.deriveAbstractionChangeActivationStmt(activationStmntCallSiteAbs);
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

            // Line 102 attach activation statement
            return attachActivationStmt(returnSiteAbs, callSiteAbs);
        }

        // Line 103 return symbolized abstraction
        else{
            return symbolize(caller, callee, returnSiteAbs);        
        }
    }

}
