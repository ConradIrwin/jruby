package org.jruby.compiler.ir.operands;

import java.util.List;
import java.util.Map;
import org.jruby.compiler.ir.Interp;
import org.jruby.compiler.ir.representations.InlinerInfo;
import org.jruby.compiler.ir.targets.JVM;
import org.jruby.runtime.DynamicScope;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

public abstract class Operand {
    public static final Operand[] EMPTY_ARRAY = new Operand[0];

    /**
     * Do we know the value of this operand at compile-time?
     * 
     * If we do then it may be possible to constant propagate (one case:
     * We also know it is also an ImmutableLiteral).  
     * 
     * @return true if a known compile-time value.
     */
    public boolean hasKnownValue() {
        return false;
    }

    // SSS: HUH? Use better names than this .. The distinction is not very clear!
    //
    // getValue returns the value of this operand, fully simplified
    // getSimplifiedOperand returns the operand in a form that can be materialized into bytecode, if it cannot be completely optimized away
    //
    // The value is used during optimizations and propagated through the IR.  But, it is thrown away after that.
    // But, the operand form is used for constructing the compound objects represented by the operand.
    //
    // Example: a = 1, b = [3,4], c = [a,b], d = [2,c]
    //   -- getValue(c) = [1,[3,4]];     getSimplifiedOperand(c) = [1, b]
    //   -- getValue(d) = [2,[1,[3,4]]]; getSimplifiedOperand(d) = [2, c]
    //
    // Note that b,c,d are all compound objects, and c has a reference to objects a and b, and d has a reference to c.
    // So, if contents of b is modified, the "simplified value"s of c and d also change!  This difference
    // is captured by these two methods.
    public Operand getSimplifiedOperand(Map<Operand, Operand> valueMap, boolean force) {
        return this;
    }

    public Operand getValue(Map<Operand, Operand> valueMap) {
        return this;
    }

    // if (getSubArray) is false, returns the 'index' element of the array, else returns the subarray starting at that element
    public Operand fetchCompileTimeArrayElement(int index, boolean getSubArray) {
        return null;
    }

    /** Append the list of variables used in this operand to the input list -- force every operand
     *  to implement this because a missing implementation can cause bad failures.
     */
    public abstract void addUsedVariables(List<Variable> l);

    public Operand cloneForInlining(InlinerInfo ii) {
        return this;
    }

    @Interp
    public Object retrieve(ThreadContext context, IRubyObject self, DynamicScope currDynScope, Object[] temp) {
        throw new RuntimeException(this.getClass().getSimpleName() + " should not be directly retrieved.");
    }

    public void compile(JVM jvm) {
        throw new RuntimeException(this.getClass().getSimpleName() + " has no compile logic.");
    }
}
