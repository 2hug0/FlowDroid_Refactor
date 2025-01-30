package soot.jimple.infoflow.solver.mergeSolver;

import java.util.Objects;

import heros.solver.PathEdge;
import soot.Unit;
import soot.jimple.infoflow.data.Abstraction;

public class SymbolIncomingEntry {
    private final PathEdge<Unit, Abstraction> edge;
    private final Abstraction abstraction;

    public SymbolIncomingEntry(PathEdge<Unit, Abstraction> edge, Abstraction abstraction) {
        this.edge = edge;
        this.abstraction = abstraction;
    }

    public PathEdge<Unit, Abstraction> getPathEdge() {
        return edge;
    }

    public Abstraction getAbstraction() {
        return abstraction;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        SymbolIncomingEntry other = (SymbolIncomingEntry) obj;
        return Objects.equals(edge, other.edge) &&
               Objects.equals(abstraction, other.abstraction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(edge, abstraction);
    }

    @Override
    public String toString() {
        return "SymbolIncomingEntry { pathEdge=" + edge + ", abstraction=" + abstraction + " }";
    }
}
