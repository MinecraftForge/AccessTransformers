package net.minecraftforge.accesstransformer.benchmarks;

import net.minecraftforge.accesstransformer.parser.AccessTransformerList;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.objectweb.asm.Type;

@State(Scope.Benchmark)
public class AccessTransformerListBenchmark {
    private AccessTransformerList staticList;

    @Setup
    public void setup() throws Exception {
        staticList = new AccessTransformerList();
        staticList.loadFromResource("accesstransformer_forge_119.cfg");
        staticList.loadFromResource("accesstransformer_firstaid_119.cfg");
        staticList.loadFromResource("accesstransformer_jei_119.cfg");
    }

    @Benchmark
    public void testATLoad(Blackhole blackhole) throws Exception {
        AccessTransformerList list = new AccessTransformerList();
        list.loadFromResource("accesstransformer_forge_119.cfg");
        list.loadFromResource("accesstransformer_firstaid_119.cfg");
        list.loadFromResource("accesstransformer_jei_119.cfg");
        blackhole.consume(list);
    }

    @Benchmark
    public void testAtContainsMiss(Blackhole blackhole) throws Exception {
        if (staticList.containsClassTarget(Type.getObjectType("net/minecraft/client/gui/font/FontManager")))
            throw new RuntimeException("Didn't expect to find FontManager!");
    }

    @Benchmark
    public void testAtContainsHit(Blackhole blackhole) throws Exception {
        if (!staticList.containsClassTarget(Type.getObjectType("net/minecraft/client/gui/Gui")))
            throw new RuntimeException("Expected to find Gui!");
    }
}
