package soot.jimple.infoflow.solver.mergeSolver.unithandling;

import java.util.Objects;

import soot.SootMethod;
import soot.jimple.infoflow.data.Abstraction;

public class Symbol {

    protected final int hashCode;
    protected final SootMethod caller;
    protected final SootMethod callee;
    protected final Abstraction abstraction;
    protected static final Symbol GAS = new Symbol(null, null, null);

    public Symbol(SootMethod callerSM, SootMethod calleeSM, Abstraction abstraction){
        this.caller = callerSM;
        this.callee = calleeSM;
        this.abstraction = abstraction;
        this.hashCode = Objects.hash(callerSM, calleeSM, abstraction);
    }

    public SootMethod getCaller() {
        return caller;
    }

    public SootMethod getCallee() {
        return callee;
    }

    public Abstraction getAbstraction() {
        return abstraction;
    }

    public Symbol getGAS() {
        return GAS;
    }

    public boolean matchContext(SootMethod callerSM, SootMethod calleeSM){
        return this.caller.equals(callerSM) && this.callee.equals(calleeSM);
    }

    @Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		if (obj == null || getClass() != obj.getClass())
			return false;
		Symbol other = (Symbol) obj;
		if (!caller.equals(other.caller))
			return false;
		if (!callee.equals(other.callee))
			return false;
		if (!abstraction.equals(other.abstraction))
			return false;
		return true;
	}

    @Override
    public String toString() {
        return "Symbol <" +
                "caller=" + (caller != null ? caller.toString() : "null") +
                ", callee=" + (callee != null ? callee.toString() : "null") +
                ", abstraction=" + (abstraction != null ? abstraction.toString() : "null") +
                '>';
    }
    
    @Override
	public int hashCode() {
		return hashCode;
	}
}


/* 

import java.util.concurrent.ConcurrentHashMap;
import java.util.Objects;

public class Symbol {

    protected final SootMethod caller;
    protected final SootMethod callee;
    protected final Abstraction abstraction;

    // Cache für bereits erstellte Symbol-Objekte
    private static final ConcurrentHashMap<SymbolKey, Symbol> cache = new ConcurrentHashMap<>();

    // Konstante für "GAS"
    protected static final Symbol GAS = new Symbol(null, null, null);

    private Symbol(SootMethod callerSM, SootMethod calleeSM, Abstraction abstraction) {
        this.caller = callerSM;
        this.callee = calleeSM;
        this.abstraction = abstraction;
    }

    // Fabrikmethode zum Erzeugen oder Abrufen eines Symbols
    public static Symbol getInstance(SootMethod callerSM, SootMethod calleeSM, Abstraction abstraction) {
        SymbolKey key = new SymbolKey(callerSM, calleeSM, abstraction);
        return cache.computeIfAbsent(key, k -> new Symbol(callerSM, calleeSM, abstraction));
    }

    public SootMethod getCaller() { return caller; }
    public SootMethod getCallee() { return callee; }
    public Abstraction getAbstraction() { return abstraction; }
    public static Symbol getGAS() { return GAS; }

    // Hilfsklasse als Key für die ConcurrentHashMap
    private static class SymbolKey {
        private final SootMethod caller;
        private final SootMethod callee;
        private final Abstraction abstraction;

        public SymbolKey(SootMethod caller, SootMethod callee, Abstraction abstraction) {
            this.caller = caller;
            this.callee = callee;
            this.abstraction = abstraction;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            SymbolKey that = (SymbolKey) obj;
            return Objects.equals(caller, that.caller) &&
                   Objects.equals(callee, that.callee) &&
                   Objects.equals(abstraction, that.abstraction);
        }

        @Override
        public int hashCode() {
            return Objects.hash(caller, callee, abstraction);
        }
    }
}
*/

