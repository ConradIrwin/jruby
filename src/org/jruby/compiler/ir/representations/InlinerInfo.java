package org.jruby.compiler.ir.representations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jruby.runtime.Arity;
import org.jruby.compiler.ir.IRScope;
import org.jruby.compiler.ir.Tuple;
import org.jruby.compiler.ir.instructions.jruby.ToAryInstr;
import org.jruby.compiler.ir.instructions.CallBase;
import org.jruby.compiler.ir.instructions.ResultInstr;
import org.jruby.compiler.ir.instructions.YieldInstr;
import org.jruby.compiler.ir.operands.Array;
import org.jruby.compiler.ir.operands.ClosureLocalVariable;
import org.jruby.compiler.ir.operands.Label;
import org.jruby.compiler.ir.operands.LocalVariable;
import org.jruby.compiler.ir.operands.Operand;
import org.jruby.compiler.ir.operands.Variable;

public class InlinerInfo {
    private static Integer globalInlineCount = 0;

    public final CFG callerCFG;
    public final CallBase call;

    private Operand[] callArgs;
    private Map<Label, Label> lblRenameMap;
    private Map<Variable, Variable> varRenameMap;
    private Map<BasicBlock, BasicBlock> bbRenameMap;
    private List yieldSites;
    private Operand callReceiver;
    private String inlineVarPrefix;

    // For inlining closures
    private Operand yieldArg;
    private Variable yieldResult;

    public InlinerInfo(CallBase call, CFG c) {
        this.call = call;
        this.callArgs = call.getCallArgs();
        this.callerCFG = c;
        this.varRenameMap = new HashMap<Variable, Variable>();
        this.lblRenameMap = new HashMap<Label, Label>();
        this.bbRenameMap = new HashMap<BasicBlock, BasicBlock>();
        this.yieldSites = new ArrayList();
        this.callReceiver = call.getReceiver();
        synchronized(globalInlineCount) { 
            this.inlineVarPrefix = "%in" + globalInlineCount + "_"; 
            globalInlineCount++;
        }
    }

    /**
     * Returns the scope into which code is being inlined.
     */
    public IRScope getInlineHostScope() {
        return callerCFG.getScope();
    }

    public Label getRenamedLabel(Label l) {
        Label newLbl = this.lblRenameMap.get(l);
        if (newLbl == null) {
           newLbl = getInlineHostScope().getNewLabel();
           this.lblRenameMap.put(l, newLbl);
        }
        return newLbl;
    }

    public void resetRenameMaps() {
        this.varRenameMap = new HashMap<Variable, Variable>();
        this.lblRenameMap = new HashMap<Label, Label>();
    }

    public Map<Variable, Variable> getVarRenameMap() {
        return varRenameMap;
    }

    public void setupYieldArgsAndYieldResult(YieldInstr yi, BasicBlock yieldBB, Arity blockArity) {
        int     blockArityValue = blockArity.getValue();
        IRScope callerScope   = getInlineHostScope();
        Operand yieldInstrArg = yi.getYieldArg();

        if ((yieldInstrArg == null) || (blockArityValue == 0)) {
            this.yieldArg = new Array(); // Zero-elt array
        } else {
            // SSS FIXME: The code below is not entirely correct.  We have to process 'yi.getYieldArg()' similar
            // to how InterpretedIRBlockBody (1.8 and 1.9 modes) processes it.  We may need a special instruction
            // that takes care of aligning the stars and bringing good fortune to arg yielder and arg receiver.

            boolean needSpecialProcessing = (blockArityValue != -1) && (blockArityValue != 1);
            if (yieldInstrArg instanceof Array) {
                this.yieldArg = yieldInstrArg;
            } else {
                Variable yieldArgArray = callerScope.getNewTemporaryVariable(); 
                yieldBB.addInstr(new ToAryInstr(yieldArgArray, yieldInstrArg, callerScope.getManager().getTrue()));
                this.yieldArg = yieldArgArray;
            }
        }

        this.yieldResult = yi.getResult();
    }

    public Variable getRenamedVariable(Variable v) {
        Variable newVar = this.varRenameMap.get(v);
        if (newVar == null) {
            newVar = getInlineHostScope().getNewInlineVariable(inlineVarPrefix, v);
            this.varRenameMap.put(v, newVar);
        }
        return newVar;
    }

    public BasicBlock getRenamedBB(BasicBlock bb) {
        return bbRenameMap.get(bb);
    }

    public BasicBlock getOrCreateRenamedBB(BasicBlock bb) {
        BasicBlock renamedBB = getRenamedBB(bb);
        if (renamedBB == null) {
            renamedBB =  new BasicBlock(this.callerCFG, getRenamedLabel(bb.getLabel()));
            bbRenameMap.put(bb, renamedBB);
        }
        return renamedBB;
    }

    public int getArgsCount() {
        return callArgs.length;
    }

    public Operand getCallArg(int index) {
        return index < callArgs.length ? callArgs[index] : null;
    }

    public Operand getCallArg(int argIndex, boolean restOfArgArray) {
        if (restOfArgArray == false) {
            return getCallArg(argIndex);
        }
        else if (argIndex >= callArgs.length) {
            return new Array();
        }
        else {
            Operand[] tmp = new Operand[callArgs.length - argIndex];
            for (int j = argIndex; j < callArgs.length; j++)
                tmp[j-argIndex] = callArgs[j];

            return new Array(tmp);
        }
    }

    public Operand getCallReceiver() {
        return callReceiver;
    }

    public Operand getCallClosure() {
        return call.getClosureArg(callerCFG.getScope().getManager().getNil());
    }

    public Variable getCallResultVariable() {
        return (call instanceof ResultInstr) ? ((ResultInstr)call).getResult() : null;
    }

    public void recordYieldSite(BasicBlock bb, YieldInstr i) {
        yieldSites.add(new Tuple<BasicBlock, YieldInstr>(bb, i));
    }

    public List getYieldSites() {
        return yieldSites;
    }

    public Variable getYieldResult() {
        return yieldResult;
    }

    public Operand getYieldArg() {
        return yieldArg;
    }
}
