package net.minecraftforge.accesstransformer;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.util.*;
import java.util.stream.*;

public class MethodTarget extends Target<MethodNode> {
    private final String methodName;
    private final List<Type> arguments;
    private final Type returnType;
    private final String targetName;

    public MethodTarget(final String className, final String methodName, final List<String> arguments, final String returnValue) {
        super(className);
        this.methodName = methodName;
        this.arguments = arguments.stream().map(s->s.replace('.', '/')).map(Type::getType).collect(Collectors.toList());
        this.returnType = Type.getType(returnValue.replace('.', '/'));
        this.targetName = methodName+'('+arguments.stream().collect(Collectors.joining())+')'+returnType;
    }

    @Override
    public TargetType getType() {
        return TargetType.METHOD;
    }

    @Override
    public String toString() {
        return super.toString() + " "
                + Objects.toString(methodName) + "("
                + Objects.toString(arguments.stream().map(Object::toString).collect(Collectors.joining()))+")"
                +Objects.toString(returnType);
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof MethodTarget)) return false;
        return super.equals(obj) &&
                Objects.equals(methodName, ((MethodTarget)obj).methodName) &&
                Objects.deepEquals(arguments, ((MethodTarget)obj).arguments) &&
                Objects.equals(returnType, ((MethodTarget)obj).returnType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClassName(), getType(), methodName);
    }

    @Override
    public String targetName() {
        return targetName;
    }

    @Override
    public void apply(final MethodNode node, final AccessTransformer.Modifier targetAccess, final AccessTransformer.FinalState targetFinalState, Set<String> privateChanged) {
        boolean wasPrivate = (node.access & Opcodes.ACC_PRIVATE) == Opcodes.ACC_PRIVATE;
        node.access = targetAccess.mergeWith(node.access);
        node.access = targetFinalState.mergeWith(node.access);
        if (wasPrivate && !"<init>".equals(node.name) && (node.access & Opcodes.ACC_PRIVATE) != Opcodes.ACC_PRIVATE) {
            privateChanged.add(node.name+node.desc);
        }

    }

}
