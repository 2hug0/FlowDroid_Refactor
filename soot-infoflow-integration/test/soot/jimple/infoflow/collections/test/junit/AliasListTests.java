package soot.jimple.infoflow.collections.test.junit;

import java.util.Collections;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import soot.jimple.infoflow.IInfoflow;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.InfoflowConfiguration.DataFlowSolver;

public class AliasListTests extends FlowDroidTests {
    @Override
    protected void setConfiguration(InfoflowConfiguration config) {

    }

    private static final String testCodeClass = "soot.jimple.infoflow.collections.test.AliasListTestCode";

    @Test(timeout = 30000)
    public void testShiftOnAlias1() {
        IInfoflow infoflow = initInfoflow();
        if (infoflow.getConfig().getSolverConfiguration().getDataFlowSolver() == DataFlowSolver.SparseContextFlowSensitive) {
            Assert.assertTrue(true);
        } else {
            infoflow.getConfig().getPathConfiguration().setPathReconstructionMode(InfoflowConfiguration.PathReconstructionMode.Fast);
            String epoint = "<" + testCodeClass + ": void " + getCurrentMethod() + "()>";
            infoflow.computeInfoflow(appPath, libPath, Collections.singleton(epoint), sources, sinks);
            Assert.assertEquals(getExpectedResultsForMethod(epoint), infoflow.getResults().size());
            Assert.assertTrue(containsStmtString(infoflow.getResults(), "get(int)>(0)"));
            Assert.assertTrue(containsStmtString(infoflow.getResults(), "get(int)>(1)"));
            Assert.assertFalse(containsStmtString(infoflow.getResults(), "get(int)>(2)"));
        }
    }

    @Test(timeout = 30000)
    public void testShiftOnAlias2() {
        IInfoflow infoflow = initInfoflow();
        if (infoflow.getConfig().getSolverConfiguration().getDataFlowSolver() == DataFlowSolver.SparseContextFlowSensitive) {
            Assert.assertTrue(true);
        } else {
            infoflow.getConfig().getPathConfiguration().setPathReconstructionMode(InfoflowConfiguration.PathReconstructionMode.Fast);
            String epoint = "<" + testCodeClass + ": void " + getCurrentMethod() + "()>";
            infoflow.computeInfoflow(appPath, libPath, Collections.singleton(epoint), sources, sinks);
            Assert.assertEquals(getExpectedResultsForMethod(epoint), infoflow.getResults().size());
            Assert.assertTrue(containsStmtString(infoflow.getResults(), "get(int)>(0)"));
            Assert.assertTrue(containsStmtString(infoflow.getResults(), "get(int)>(1)"));
            Assert.assertFalse(containsStmtString(infoflow.getResults(), "get(int)>(2)"));
        }
    }
}
