package net.minecraftforge.accesstransformer.service;

import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.INameMappingService;
import net.minecraftforge.accesstransformer.INameHandler;

public class ServiceNameHandler implements INameHandler {
    private final String origin;
    private final String target;

    public ServiceNameHandler(final String origin, final String target) {
        this.origin = origin;
        this.target = target;
    }

    String translate(INameMappingService.Domain domain, String origin) {
        return Launcher.INSTANCE.environment().findNameMapping(target).map(f->f.apply(domain, origin)).orElse(origin);
    }

    @Override
    public String translateClassName(final String className) {
        return translate(INameMappingService.Domain.CLASS, className);
    }

    @Override
    public String translateFieldName(final String fieldName) {
        return translate(INameMappingService.Domain.FIELD, fieldName);
    }

    @Override
    public String translateMethodName(final String methodName) {
        return translate(INameMappingService.Domain.METHOD, methodName);
    }

    @Override
    public String toString() {
        return "Service NameHandler from "+origin+" to "+target;
    }
}
