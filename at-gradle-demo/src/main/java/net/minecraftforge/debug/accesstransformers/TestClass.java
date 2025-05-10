package net.minecraftforge.debug.accesstransformers;

import net.minecraftforge.coremod.CoreMod;
import net.minecraftforge.coremod.CoreModEngine;

public class TestClass {
    public static void main(String[] args) {
        System.out.println(CoreMod.COREMODLOG);
        System.out.println(CoreModEngine.LOGGER);
    }
}
