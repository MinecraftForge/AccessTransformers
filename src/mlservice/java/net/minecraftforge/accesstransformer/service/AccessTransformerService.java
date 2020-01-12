package net.minecraftforge.accesstransformer.service;

import cpw.mods.modlauncher.serviceapi.*;
import net.minecraftforge.accesstransformer.*;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.nio.file.*;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.Consumer;

@SuppressWarnings("unchecked")
public class AccessTransformerService implements ILaunchPluginService {
    @Override
    public String name() {
        return "accesstransformer";
    }

    @Override
    public void addResource(final Path path, final String resourceName) {
        AccessTransformerEngine.INSTANCE.addResource(path, resourceName);
    }

    @Override
    public boolean processClass(final Phase phase, final ClassNode classNode, final Type classType) {
        return AccessTransformerEngine.INSTANCE.transform(classNode, classType);
    }

    @Override
    public ComputeLevel processClassNew(Phase phase, ClassNode classNode, Type classType, String reason) {
        return AccessTransformerEngine.INSTANCE.transform(classNode, classType) ? ComputeLevel.SIMPLE_REWRITE : ComputeLevel.NO_REWRITE;
    }

    private static final EnumSet<Phase> YAY = EnumSet.of(Phase.BEFORE);
    private static final EnumSet<Phase> NAY = EnumSet.noneOf(Phase.class);

    @Override
    public EnumSet<Phase> handlesClass(final Type classType, final boolean isEmpty) {
        return !isEmpty && AccessTransformerEngine.INSTANCE.handlesClass(classType) ? YAY : NAY;
    }

    /**
     * The first entry of the pair is the "naming scheme in use", the second is the naming scheme of the
     * AccessTransformers being loaded into the system. Currently, this will ask
     * ModLauncher for a naming transformation to the "naming scheme in use"
     * for every file loaded.
     *
     * @return A consumer of Map.Entry<String,String> specifying a name scheme pair.
     */
    @Override
    public Consumer<Map.Entry<String,String>> getExtension() {
        return this::setNameHandler;
    }

    private void setNameHandler(Map.Entry<String,String> naming) {
        AccessTransformerEngine.INSTANCE.acceptNaming(new ServiceNameHandler(naming.getKey(), naming.getValue()));
    }
}
