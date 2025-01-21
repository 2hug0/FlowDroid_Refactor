
package soot.jimple.infoflow.solver.mergeSolver.oldMerge;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import soot.SootMethod;
import soot.Unit;
import soot.UnitPrinter;
import soot.jimple.JimpleToBafContext;
import soot.jimple.NopStmt;
import soot.jimple.StmtSwitch;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.internal.AbstractStmt;
import soot.util.Switch;

public class Symbol extends AbstractStmt implements NopStmt {

	protected static final long serialVersionUID = 1L;

	protected final SootMethod caller;

	protected final SootMethod callee;

	protected final Abstraction abstraction;

	protected final Set<Unit> targets;

	protected final int hashCode;

	protected static final Symbol tag = new Symbol(null, null, null);

	public static Symbol getInactiveSymbol() {
		return tag;
	}

	public Symbol(SootMethod caller, SootMethod callee, Abstraction abstraction) {
		this.caller = caller;
		this.callee = callee;
		this.abstraction = abstraction;
		this.hashCode = Objects.hash(caller, callee, abstraction);

		this.targets = Collections.newSetFromMap(new ConcurrentHashMap<>());
	}

	public SootMethod getCaller() {
		return caller;
	}

	public SootMethod getCallee() {
		return callee;
	}

	public boolean addTarget(Unit u) {
		return targets.add(u);
	}

	public Set<Unit> getTargets() {
		return targets;
	}

	public boolean matchContext(SootMethod caller, SootMethod callee) {
		return this.caller == caller && this.callee == callee;
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
	public int hashCode() {
		return hashCode;
	}

	@Override
	public Object clone() {
		return this;
	}

	@Override
	public String toString() {
		return this == tag ? "tag" : "sym";
	}

	@Override
	public void toString(UnitPrinter up) {
	}

	@Override
	public void apply(Switch sw) {
		((StmtSwitch) sw).caseNopStmt(this);
	}

	@Override
	public void convertToBaf(JimpleToBafContext context, List<Unit> out) {
	}

	@Override
	public boolean fallsThrough() {
		return true;
	}

	@Override
	public boolean branches() {
		return false;
	}
}
